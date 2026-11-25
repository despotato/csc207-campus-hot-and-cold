package campus.application.explore;

import campus.domain.explore.GeoPoint;

import java.util.UUID;

public interface TravelInputBoundary {
    /** Fast-travels to a spot picked on the campus map; charged as the shortest walk there. */
    void execute(UUID roundId, GeoPoint destination);
}
