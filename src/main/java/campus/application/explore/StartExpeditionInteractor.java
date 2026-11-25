package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Difficulty;
import campus.domain.explore.Expedition;
import campus.domain.explore.WalkGraph.Position;

import java.time.Clock;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class StartExpeditionInteractor implements StartExpeditionInputBoundary {
    private final CampusMap map;
    private final RandomGenerator random;
    private final Clock clock;
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public StartExpeditionInteractor(CampusMap map, RandomGenerator random, Clock clock,
                                     ExpeditionSession session, ExploreOutputBoundary output) {
        this.map = Objects.requireNonNull(map);
        this.random = Objects.requireNonNull(random);
        this.clock = Objects.requireNonNull(clock);
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(Difficulty difficulty) {
        if (difficulty == null) {
            output.present(new ExploreResponse.Failed("Choose a difficulty."));
            return;
        }
        Expedition expedition;
        try {
            expedition = Expedition.begin(difficulty, map, random, clock.instant());
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed("Could not set up a round. Please retry."));
            return;
        }
        session.start(expedition);
        Position start = expedition.start();
        output.present(new ExploreResponse.Started(map.name(), map.walkways().pointAt(start), start.from(),
                start.to(), start.fraction(), expedition.checksRemaining(), Expedition.GUESS_LIMIT, difficulty.name()));
    }
}
