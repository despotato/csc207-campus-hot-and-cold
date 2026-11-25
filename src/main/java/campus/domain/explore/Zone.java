package campus.domain.explore;

import java.util.Objects;

/** The circular search zone around (but not centred on) the target building. */
public record Zone(GeoPoint centre, double radiusMetres) {
    public Zone {
        Objects.requireNonNull(centre);
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException("A zone needs a positive radius.");
        }
    }

    public boolean contains(GeoPoint point) {
        return centre.distanceTo(point) <= radiusMetres;
    }
}
