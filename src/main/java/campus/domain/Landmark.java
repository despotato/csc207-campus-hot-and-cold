package campus.domain;

public record Landmark(long id, String name, double latitude, double longitude) {
    public Landmark {
        if (id <= 0 || name == null || name.isBlank() || !Double.isFinite(latitude)
                || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("A landmark needs a positive ID, name, and valid coordinates.");
        }
    }

    public double distanceTo(Landmark other) {
        double latitudeDelta = Math.toRadians(other.latitude - latitude);
        double longitudeDelta = Math.toRadians(other.longitude - longitude);
        double latitudeTerm = Math.sin(latitudeDelta / 2);
        double longitudeTerm = Math.sin(longitudeDelta / 2);
        double haversine = latitudeTerm * latitudeTerm
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * longitudeTerm * longitudeTerm;
        return 2 * 6_371_000 * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    @Override
    public String toString() {
        return name;
    }
}

