package campus.application.explore;

import campus.domain.explore.GeoPoint;

import java.util.List;
import java.util.UUID;

public interface MoveInputBoundary {
    /** Walks through the given points in order: the path walked since the last update. */
    void execute(UUID roundId, List<GeoPoint> path);
}
