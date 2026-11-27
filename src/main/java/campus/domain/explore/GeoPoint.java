package campus.domain.explore;

/** A WGS84 coordinate with metre-based helpers for campus-scale distances. */
public record GeoPoint(double latitude, double longitude) {
    private static final double EARTH_RADIUS = 6_371_000;
    private static final double METRES_PER_DEGREE = Math.PI * EARTH_RADIUS / 180;

    public GeoPoint {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid coordinate.");
        }
    }

    /** Great-circle (Haversine) distance in metres. */
    public double distanceTo(GeoPoint other) {
        double latitudeTerm = Math.sin(Math.toRadians(other.latitude - latitude) / 2);
        double longitudeTerm = Math.sin(Math.toRadians(other.longitude - longitude) / 2);
        double haversine = latitudeTerm * latitudeTerm + Math.cos(Math.toRadians(latitude))
                * Math.cos(Math.toRadians(other.latitude)) * longitudeTerm * longitudeTerm;
        return 2 * EARTH_RADIUS * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    /** Compass bearing to another point in degrees, 0 = north, clockwise. */
    public double bearingTo(GeoPoint other) {
        double north = (other.latitude - latitude) * METRES_PER_DEGREE;
        double east = (other.longitude - longitude) * METRES_PER_DEGREE * Math.cos(Math.toRadians(latitude));
        return (Math.toDegrees(Math.atan2(east, north)) + 360) % 360;
    }

    /** The point reached by moving the given metres on a bearing (accurate at campus scale). */
    public GeoPoint offset(double bearingDegrees, double metres) {
        double north = metres * Math.cos(Math.toRadians(bearingDegrees));
        double east = metres * Math.sin(Math.toRadians(bearingDegrees));
        return new GeoPoint(latitude + north / METRES_PER_DEGREE,
                longitude + east / (METRES_PER_DEGREE * Math.cos(Math.toRadians(latitude))));
    }

    /** Linear interpolation; fine for the short edges of a walking graph. */
    public GeoPoint towards(GeoPoint other, double fraction) {
        return new GeoPoint(latitude + (other.latitude - latitude) * fraction,
                longitude + (other.longitude - longitude) * fraction);
    }
}
