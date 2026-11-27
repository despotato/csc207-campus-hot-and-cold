package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Difficulty;
import campus.domain.explore.Expedition;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Starts a round on the chosen campus: today's shared daily puzzle (one try) or an unlimited random round. */
public final class StartExpeditionInteractor implements StartExpeditionInputBoundary {
    private final CampusCatalog campuses;
    private final DailyAttempts attempts;
    private final RandomGenerator random;
    private final Clock clock;
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public StartExpeditionInteractor(CampusCatalog campuses, DailyAttempts attempts, RandomGenerator random,
                                     Clock clock, ExpeditionSession session, ExploreOutputBoundary output) {
        this.campuses = Objects.requireNonNull(campuses);
        this.attempts = Objects.requireNonNull(attempts);
        this.random = Objects.requireNonNull(random);
        this.clock = Objects.requireNonNull(clock);
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(StartRequest request) {
        if (request == null) {
            output.present(new ExploreResponse.Failed("Choose a campus and mode."));
            return;
        }
        String campusId = request.campusId() == null ? campuses.defaultCampusId() : request.campusId();
        CampusMap map = campuses.campus(campusId).orElse(null);
        if (map == null) {
            output.present(new ExploreResponse.Failed("Unknown campus."));
            return;
        }
        boolean daily = request.mode() == StartRequest.Mode.DAILY;
        LocalDate today = LocalDate.now(clock.withZone(map.timezone()));
        Expedition expedition;
        try {
            if (daily) {
                if (map.dailyPuzzle(today).isEmpty()) {
                    output.present(new ExploreResponse.Failed("There's no daily puzzle today. Try unlimited mode."));
                    return;
                }
                if (!attempts.claim(map.id(), today)) {
                    output.present(new ExploreResponse.Failed(
                            "You've already played today's daily. Come back tomorrow, or play unlimited."));
                    return;
                }
                expedition = Expedition.daily(map, today, clock.instant());
            } else {
                expedition = Expedition.begin(request.difficulty(), map, random, clock.instant());
            }
        } catch (IOException exception) {
            output.present(new ExploreResponse.Failed("Couldn't record today's daily attempt. Please retry."));
            return;
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed("Could not set up a round. Please retry."));
            return;
        }
        if (daily) {
            try {
                session.startDaily(expedition, map.id(), today);
            } catch (IOException exception) {
                session.start(expedition);   // still playable; just not resumable after a restart
            }
        } else {
            session.start(expedition);
        }
        Difficulty difficulty = daily ? Difficulty.NORMAL : request.difficulty();
        output.present(new ExploreResponse.Started(expedition.id(), map.id(), map.name(), expedition.start(),
                expedition.checksRemaining(), Expedition.GUESS_LIMIT, difficulty.name(),
                daily ? "daily" : "unlimited", daily ? today.toString() : null));
    }
}
