package campus.adapters.explore;

import campus.application.explore.PlayerStatsOutputBoundary;
import campus.domain.explore.PlayerRecord;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerStatsPresenter implements PlayerStatsOutputBoundary {
    private PlayerStatsViewModel viewModel = new PlayerStatsViewModel(400, Map.of("error", "No action yet."));

    @Override
    public void presentStats(PlayerRecord record) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "stats");
        body.put("roundsPlayed", record.roundsPlayed());
        body.put("winRate", Math.round(record.winRate() * 100));
        body.put("averageScore", Math.round(record.averageScore()));
        body.put("bestScore", record.bestScore());
        body.put("averageRouteEfficiency", Math.round(record.averageRouteEfficiency() * 100));
        body.put("favouriteHintLevel", record.favouriteHintLevel());
        body.put("bestScorePerCampus", record.bestScorePerCampus());
        body.put("currentDailyStreak", record.currentDailyStreak());
        body.put("longestDailyStreak", record.longestDailyStreak());
        body.put("scoreHistogram", record.scoreHistogram());
        viewModel = new PlayerStatsViewModel(200, body);
    }

    @Override
    public void presentFailure(String message) {
        viewModel = new PlayerStatsViewModel(400, Map.of("type", "error", "error", message));
    }

    public PlayerStatsViewModel viewModel() {
        return viewModel;
    }
}
