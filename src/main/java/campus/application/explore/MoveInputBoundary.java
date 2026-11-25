package campus.application.explore;

import campus.domain.explore.GeoPoint;

import java.util.List;

public interface MoveInputBoundary {
    /** Walks through the given points in order: the path walked since the last update. */
    void execute(List<GeoPoint> path);
}
