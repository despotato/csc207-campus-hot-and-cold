package campus.domain.explore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An anonymous path someone walked on a campus: only the points and when it was recorded, never who walked it or
 * how they scored. Other players see these as students walking around campus.
 */
public record FootTrail(String campusId, List<GeoPoint> points, Instant recordedAt) {
    /** Points are kept about this far apart (metres), which keeps trails small to store and send. */
    public static final double SPACING_METRES = 8;

    public FootTrail {
        if (campusId == null || !campusId.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("A trail needs a campus ID.");
        }
        Objects.requireNonNull(recordedAt);
        points = List.copyOf(points);
        if (points.size() < 2) {
            throw new IllegalArgumentException("A trail needs at least two points.");
        }
    }

    /** A trail from a walked path, thinned to a point every {@link #SPACING_METRES} (always keeping the end). */
    public static FootTrail of(String campusId, List<GeoPoint> walked, Instant recordedAt) {
        List<GeoPoint> kept = new ArrayList<>();
        for (GeoPoint point : walked) {
            if (kept.isEmpty() || kept.getLast().distanceTo(point) >= SPACING_METRES) {
                kept.add(point);
            }
        }
        if (!walked.isEmpty() && !kept.getLast().equals(walked.getLast())) {
            kept.add(walked.getLast());
        }
        return new FootTrail(campusId, kept, recordedAt);
    }

    public double metres() {
        double total = 0;
        for (int i = 1; i < points.size(); i++) {
            total += points.get(i - 1).distanceTo(points.get(i));
        }
        return total;
    }
}
