package campus.domain.explore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.PriorityQueue;
import java.util.function.IntPredicate;

/** The campus footpath network. Players may only stand on its edges. */
public final class WalkGraph {
    private final List<GeoPoint> nodes;
    private final int[][] neighbours;
    private final double[][] lengths;
    private final boolean[] connected;

    /** A spot on an edge: {@code fraction} 0 is at {@code from}, 1 is at {@code to}. */
    public record Position(int from, int to, double fraction) {
        public Position {
            if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
                throw new IllegalArgumentException("Edge fraction must be between 0 and 1.");
            }
        }
    }

    public record Route(List<Integer> nodes, double metres) {
        public Route {
            nodes = List.copyOf(nodes);
        }
    }

    public WalkGraph(List<GeoPoint> nodes, List<int[]> edges) {
        this.nodes = List.copyOf(nodes);
        List<List<Integer>> adjacent = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            adjacent.add(new ArrayList<>());
        }
        for (int[] edge : edges) {
            if (edge.length != 2 || edge[0] == edge[1] || !valid(edge[0]) || !valid(edge[1])) {
                throw new IllegalArgumentException("Invalid walkway edge.");
            }
            if (!adjacent.get(edge[0]).contains(edge[1])) {
                adjacent.get(edge[0]).add(edge[1]);
                adjacent.get(edge[1]).add(edge[0]);
            }
        }
        neighbours = new int[nodes.size()][];
        lengths = new double[nodes.size()][];
        for (int i = 0; i < nodes.size(); i++) {
            neighbours[i] = adjacent.get(i).stream().mapToInt(Integer::intValue).toArray();
            lengths[i] = new double[neighbours[i].length];
            for (int k = 0; k < neighbours[i].length; k++) {
                lengths[i][k] = nodes.get(i).distanceTo(nodes.get(neighbours[i][k]));
            }
        }
        connected = largestComponent();
    }

    public int size() {
        return nodes.size();
    }

    public GeoPoint node(int index) {
        return nodes.get(index);
    }

    /** True for nodes in the largest connected part of the network, where rounds take place. */
    public boolean isMainNetwork(int node) {
        return valid(node) && connected[node];
    }

    public boolean hasEdge(int a, int b) {
        return edgeIndex(a, b) >= 0;
    }

    public GeoPoint pointAt(Position position) {
        requireEdge(position);
        return nodes.get(position.from()).towards(nodes.get(position.to()), position.fraction());
    }

    public Position at(int node) {
        if (!valid(node) || neighbours[node].length == 0) {
            throw new IllegalArgumentException("Node is not on the walkway network.");
        }
        return new Position(node, neighbours[node][0], 0);
    }

    /**
     * Walking distance between two positions, if it is at most {@code limit} metres.
     * Empty means the move is longer than the limit or not connected.
     */
    public OptionalDouble walkingDistance(Position start, Position end, double limit) {
        requireEdge(start);
        requireEdge(end);
        double startLength = edgeLength(start.from(), start.to());
        double endLength = edgeLength(end.from(), end.to());
        double best = Double.POSITIVE_INFINITY;
        if (sameEdge(start, end)) {
            double endFraction = start.from() == end.from() ? end.fraction() : 1 - end.fraction();
            best = Math.abs(start.fraction() - endFraction) * startLength;
        }
        double[] distance = dijkstra(new int[]{start.from(), start.to()},
                new double[]{start.fraction() * startLength, (1 - start.fraction()) * startLength},
                limit, node -> false);
        best = Math.min(best, Math.min(distance[end.from()] + end.fraction() * endLength,
                distance[end.to()] + (1 - end.fraction()) * endLength));
        return best <= limit ? OptionalDouble.of(best) : OptionalDouble.empty();
    }

    /** Shortest walking route from a node to the nearest node satisfying {@code goal}. */
    public Optional<Route> shortestRoute(int source, IntPredicate goal) {
        if (!valid(source)) {
            throw new IllegalArgumentException("Unknown node.");
        }
        int[] previous = new int[nodes.size()];
        Arrays.fill(previous, -1);
        double[] distance = new double[nodes.size()];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        distance[source] = 0;
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        queue.add(new double[]{0, source});
        while (!queue.isEmpty()) {
            double[] item = queue.poll();
            int node = (int) item[1];
            if (item[0] > distance[node]) {
                continue;
            }
            if (goal.test(node)) {
                ArrayDeque<Integer> path = new ArrayDeque<>();
                for (int at = node; at != -1; at = previous[at]) {
                    path.addFirst(at);
                }
                return Optional.of(new Route(List.copyOf(path), distance[node]));
            }
            for (int k = 0; k < neighbours[node].length; k++) {
                int next = neighbours[node][k];
                double candidate = distance[node] + lengths[node][k];
                if (candidate < distance[next]) {
                    distance[next] = candidate;
                    previous[next] = node;
                    queue.add(new double[]{candidate, next});
                }
            }
        }
        return Optional.empty();
    }

    private double[] dijkstra(int[] sources, double[] costs, double limit, IntPredicate stop) {
        double[] distance = new double[nodes.size()];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        PriorityQueue<double[]> queue = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
        for (int i = 0; i < sources.length; i++) {
            if (costs[i] < distance[sources[i]]) {
                distance[sources[i]] = costs[i];
                queue.add(new double[]{costs[i], sources[i]});
            }
        }
        while (!queue.isEmpty()) {
            double[] item = queue.poll();
            int node = (int) item[1];
            if (item[0] > distance[node] || item[0] > limit || stop.test(node)) {
                continue;
            }
            for (int k = 0; k < neighbours[node].length; k++) {
                int next = neighbours[node][k];
                double candidate = distance[node] + lengths[node][k];
                if (candidate < distance[next] && candidate <= limit) {
                    distance[next] = candidate;
                    queue.add(new double[]{candidate, next});
                }
            }
        }
        return distance;
    }

    private boolean[] largestComponent() {
        int[] component = new int[nodes.size()];
        Arrays.fill(component, -1);
        int best = -1;
        int bestSize = 0;
        for (int start = 0, label = 0; start < nodes.size(); start++) {
            if (component[start] != -1) {
                continue;
            }
            int size = 0;
            ArrayDeque<Integer> stack = new ArrayDeque<>(List.of(start));
            component[start] = label;
            while (!stack.isEmpty()) {
                int node = stack.pop();
                size++;
                for (int next : neighbours[node]) {
                    if (component[next] == -1) {
                        component[next] = label;
                        stack.push(next);
                    }
                }
            }
            if (size > bestSize) {
                bestSize = size;
                best = label;
            }
            label++;
        }
        boolean[] result = new boolean[nodes.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = component[i] == best && neighbours[i].length > 0;
        }
        return result;
    }

    private static boolean sameEdge(Position a, Position b) {
        return (a.from() == b.from() && a.to() == b.to()) || (a.from() == b.to() && a.to() == b.from());
    }

    private void requireEdge(Position position) {
        if (!hasEdge(position.from(), position.to())) {
            throw new IllegalArgumentException("Position is not on a walkway.");
        }
    }

    private double edgeLength(int a, int b) {
        return lengths[a][edgeIndex(a, b)];
    }

    private int edgeIndex(int a, int b) {
        if (!valid(a) || !valid(b)) {
            return -1;
        }
        for (int k = 0; k < neighbours[a].length; k++) {
            if (neighbours[a][k] == b) {
                return k;
            }
        }
        return -1;
    }

    private boolean valid(int node) {
        return node >= 0 && node < nodes.size();
    }
}
