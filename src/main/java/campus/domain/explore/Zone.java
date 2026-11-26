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

    /** Whether a circular player footprint reaches any part of this zone. */
    public boolean overlaps(GeoPoint playerCentre, double playerRadiusMetres) {
        if (!Double.isFinite(playerRadiusMetres) || playerRadiusMetres < 0) {
            throw new IllegalArgumentException("A player radius must be non-negative.");
        }
        return centre.distanceTo(playerCentre) <= radiusMetres + playerRadiusMetres;
    }
}
