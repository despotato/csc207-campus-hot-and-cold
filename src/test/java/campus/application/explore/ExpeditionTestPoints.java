package campus.application.explore;

import campus.domain.explore.GeoPoint;

/** Coordinates of the synthetic test campus (see ExpeditionTest), for tests in this package. */
final class ExpeditionTestPoints {
    static final GeoPoint START = new GeoPoint(43.66, -79.40).offset(90, 0).offset(0, 75);

    private ExpeditionTestPoints() { }
}
