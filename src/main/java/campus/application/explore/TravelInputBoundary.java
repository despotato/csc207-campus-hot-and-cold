package campus.application.explore;

import campus.domain.explore.GeoPoint;

public interface TravelInputBoundary {
    /** Fast-travels to a spot picked on the campus map; charged as the shortest walk there. */
    void execute(GeoPoint destination);
}
