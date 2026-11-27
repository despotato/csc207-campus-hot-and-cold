package campus.application.explore;

import java.util.UUID;

public interface GuessBuildingInputBoundary {
    void execute(UUID roundId, int buildingId);
}
