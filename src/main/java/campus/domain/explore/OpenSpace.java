package campus.domain.explore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Where a player may stand: a grid of square cells over the campus, blocked inside buildings.
 * Movement is free in any direction; buildings are solid. The browser rasterises the same outlines
 * with the same rule (a cell is blocked if its centre is inside a building), so both sides agree.
 */
public final class OpenSpace {
    private final GeoPoint southWest;
    private final double cellMetres;
    private final int columns;
    private final int rows;
    private final BitSet blocked;
    private final double metresPerLatitude;
    private final double metresPerLongitude;

    public record Route(List<GeoPoint> points, double metres) {
        public Route {
            points = List.copyOf(points);
        }
    }

    /**
     * @param outlines building polygons; each polygon is [outer ring, courtyard rings...], each ring a list of
     *                 points. Cells whose centre is inside an outer ring and not in a courtyard are blocked.
     */
    public OpenSpace(GeoPoint southWest, GeoPoint northEast, double cellMetres, List<List<List<GeoPoint>>> outlines) {
        if (!(cellMetres > 0) || northEast.latitude() <= southWest.latitude()
                || northEast.longitude() <= southWest.longitude()) {
            throw new IllegalArgumentException("Invalid open-space bounds.");
        }
        this.southWest = southWest;
        this.cellMetres = cellMetres;
        this.metresPerLatitude = Math.PI * 6_371_000 / 180;
        this.metresPerLongitude = metresPerLatitude * Math.cos(Math.toRadians(
                (southWest.latitude() + northEast.latitude()) / 2));
        this.columns = (int) Math.ceil(
                (northEast.longitude() - southWest.longitude()) * metresPerLongitude / cellMetres);
        this.rows = (int) Math.ceil((northEast.latitude() - southWest.latitude()) * metresPerLatitude / cellMetres);
        this.blocked = new BitSet(columns * rows);
        for (List<List<GeoPoint>> polygon : outlines) {
            rasterise(polygon);
        }
    }

    public double cellMetres() {
        return cellMetres;
    }

    public boolean isOpen(GeoPoint point) {
        int column = column(point);
        int row = row(point);
        return inside(column, row) && !blocked.get(row * columns + column);
    }

    /** True if a straight walk between the points never enters a building (sampled every quarter cell). */
    public boolean clearPath(GeoPoint from, GeoPoint to) {
        double metres = from.distanceTo(to);
        int samples = Math.max(1, (int) Math.ceil(metres / (cellMetres / 4)));
        for (int i = 0; i <= samples; i++) {
            if (!isOpen(from.towards(to, (double) i / samples))) {
                return false;
            }
        }
        return true;
    }

    /** The open spot nearest to {@code point} within {@code maxMetres} (the point itself if it is open). */
    public Optional<GeoPoint> nearestOpen(GeoPoint point, double maxMetres) {
        if (isOpen(point)) {
            return Optional.of(point);
        }
        int reach = (int) Math.ceil(maxMetres / cellMetres);
        GeoPoint best = null;
        double bestDistance = Double.MAX_VALUE;
        int column = column(point);
        int row = row(point);
        for (int r = row - reach; r <= row + reach; r++) {
            for (int c = column - reach; c <= column + reach; c++) {
                if (inside(c, r) && !blocked.get(r * columns + c)) {
                    GeoPoint candidate = centre(c, r);
                    double distance = candidate.distanceTo(point);
                    if (distance <= maxMetres && distance < bestDistance) {
                        best = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** Open cell centres, for choosing start points. */
    public List<GeoPoint> openCells(int stride) {
        List<GeoPoint> result = new ArrayList<>();
        for (int row = 0; row < rows; row += stride) {
            for (int column = 0; column < columns; column += stride) {
                if (!blocked.get(row * columns + column)) {
                    result.add(centre(column, row));
                }
            }
        }
        return result;
    }

    /**
     * Shortest walk on open ground from {@code start} to the nearest point satisfying {@code goal}:
     * A* over the grid (8 directions), then shortened by cutting corners wherever the way is clear.
     */
    public Optional<Route> shortestRoute(GeoPoint start, Predicate<GeoPoint> goal) {
        if (!isOpen(start)) {
            return Optional.empty();
        }
        int startCell = row(start) * columns + column(start);
        double[] cost = new double[columns * rows];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        int[] previous = new int[columns * rows];
        Arrays.fill(previous, -1);
        cost[startCell] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, startCell});
        int found = -1;
        while (!queue.isEmpty()) {
            double[] item = queue.poll();
            int cell = (int) item[1];
            if (item[0] > cost[cell]) {
                continue;
            }
            if (goal.test(centre(cell % columns, cell / columns))) {
                found = cell;
                break;
            }
            relaxNeighbours(cell, cost, previous, queue);
        }
        if (found < 0) {
            return Optional.empty();
        }
        ArrayDeque<GeoPoint> path = new ArrayDeque<>();
        for (int cell = found; cell != startCell; cell = previous[cell]) {
            path.addFirst(centre(cell % columns, cell / columns));
        }
        path.addFirst(start);
        List<GeoPoint> smooth = smooth(new ArrayList<>(path));
        double metres = 0;
        for (int i = 1; i < smooth.size(); i++) {
            metres += smooth.get(i - 1).distanceTo(smooth.get(i));
        }
        return Optional.of(new Route(smooth, metres));
    }

    private void relaxNeighbours(int cell, double[] cost, int[] previous, PriorityQueue<double[]> queue) {
        int column = cell % columns;
        int row = cell / columns;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int c = column + dc;
                int r = row + dr;
                if ((dr == 0 && dc == 0) || !inside(c, r) || blocked.get(r * columns + c)) {
                    continue;
                }
                // No squeezing diagonally between two blocked cells.
                if (dr != 0 && dc != 0 && (blocked.get(row * columns + c) || blocked.get(r * columns + column))) {
                    continue;
                }
                double step = (dr != 0 && dc != 0 ? Math.sqrt(2) : 1) * cellMetres;
                int next = r * columns + c;
                if (cost[cell] + step < cost[next]) {
                    cost[next] = cost[cell] + step;
                    previous[next] = cell;
                    queue.add(new double[]{cost[next], next});
                }
            }
        }
    }

    /** String-pulling: walk forward, keeping a point only where a straight clear walk can't go further. */
    private List<GeoPoint> smooth(List<GeoPoint> path) {
        List<GeoPoint> result = new ArrayList<>();
        GeoPoint anchor = path.get(0);
        result.add(anchor);
        for (int i = 2; i < path.size(); i++) {
            if (!clearPath(anchor, path.get(i))) {
                anchor = path.get(i - 1);
                result.add(anchor);
            }
        }
        if (path.size() > 1) {
            result.add(path.getLast());
        }
        return result;
    }

    private void rasterise(List<List<GeoPoint>> polygon) {
        List<GeoPoint> outer = polygon.get(0);
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (GeoPoint p : outer) {
            minLat = Math.min(minLat, p.latitude());
            maxLat = Math.max(maxLat, p.latitude());
            minLon = Math.min(minLon, p.longitude());
            maxLon = Math.max(maxLon, p.longitude());
        }
        int c0 = Math.max(0, column(new GeoPoint(minLat, minLon)));
        int c1 = Math.min(columns - 1, column(new GeoPoint(maxLat, maxLon)));
        int r0 = Math.max(0, row(new GeoPoint(minLat, minLon)));
        int r1 = Math.min(rows - 1, row(new GeoPoint(maxLat, maxLon)));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                GeoPoint centre = centre(c, r);
                if (contains(outer, centre) && polygon.stream().skip(1).noneMatch(hole -> contains(hole, centre))) {
                    blocked.set(r * columns + c);
                }
            }
        }
    }

    /** Even-odd point-in-polygon test, matching the browser's version. */
    static boolean contains(List<GeoPoint> ring, GeoPoint point) {
        boolean hit = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            GeoPoint a = ring.get(i);
            GeoPoint b = ring.get(j);
            if ((a.latitude() > point.latitude()) != (b.latitude() > point.latitude())
                    && point.longitude() < (b.longitude() - a.longitude()) * (point.latitude() - a.latitude())
                    / (b.latitude() - a.latitude()) + a.longitude()) {
                hit = !hit;
            }
        }
        return hit;
    }

    private int column(GeoPoint point) {
        return (int) Math.floor((point.longitude() - southWest.longitude()) * metresPerLongitude / cellMetres);
    }

    private int row(GeoPoint point) {
        return (int) Math.floor((point.latitude() - southWest.latitude()) * metresPerLatitude / cellMetres);
    }

    private GeoPoint centre(int column, int row) {
        return new GeoPoint(southWest.latitude() + (row + 0.5) * cellMetres / metresPerLatitude,
                southWest.longitude() + (column + 0.5) * cellMetres / metresPerLongitude);
    }

    private boolean inside(int column, int row) {
        return column >= 0 && row >= 0 && column < columns && row < rows;
    }
}
