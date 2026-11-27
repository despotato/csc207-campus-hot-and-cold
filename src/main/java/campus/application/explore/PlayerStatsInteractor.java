package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.PlayerRecord;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Builds PlayerRecord from the saved-round repository. */
public final class PlayerStatsInteractor implements PlayerStatsInputBoundary {
    private final CampusCatalog campuses;
    private final ExpeditionResultRepository results;
    private final PlayerStatsOutputBoundary output;
    private final Clock clock;

    public PlayerStatsInteractor(CampusCatalog campuses, ExpeditionResultRepository results,
                                 PlayerStatsOutputBoundary output, Clock clock) {
        this.campuses = Objects.requireNonNull(campuses);
        this.results = Objects.requireNonNull(results);
        this.output = Objects.requireNonNull(output);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void stats(String campusId) {
        String homeId = campusId == null ? campuses.defaultCampusId() : campusId;
        CampusMap home = campuses.campus(homeId).orElse(null);
        if (home == null) {
            output.presentFailure("Unknown campus.");
            return;
        }
        try {
            LocalDate today = LocalDate.now(clock.withZone(home.timezone()));
            output.presentStats(PlayerRecord.from(results.all(), home.id(), home.timezone(), today));
        } catch (IOException exception) {
            output.presentFailure("Could not load player statistics.");
        }
    }
}
