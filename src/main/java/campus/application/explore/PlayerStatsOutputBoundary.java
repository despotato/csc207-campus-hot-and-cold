package campus.application.explore;

import campus.domain.explore.PlayerRecord;

public interface PlayerStatsOutputBoundary {
    void presentStats(PlayerRecord record);

    void presentFailure(String message);
}
