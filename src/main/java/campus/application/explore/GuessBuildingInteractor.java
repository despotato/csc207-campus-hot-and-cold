package campus.application.explore;

import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.Expedition;

import java.time.Duration;
import java.util.Objects;

public final class GuessBuildingInteractor implements GuessBuildingInputBoundary {
    private final ExpeditionSession session;
    private final LandmarkInfoGateway info;
    private final ExploreOutputBoundary output;

    public GuessBuildingInteractor(ExpeditionSession session, LandmarkInfoGateway info,
                                   ExploreOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.info = Objects.requireNonNull(info);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(int buildingId) {
        Expedition expedition = session.current().orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed("Start a round first."));
            return;
        }
        boolean correct;
        try {
            correct = expedition.guess(buildingId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
            return;
        }
        int remaining = Expedition.GUESS_LIMIT - expedition.guesses().size();
        ExploreResponse.Result result = expedition.revealedTarget()
                .map(target -> result(expedition, target)).orElse(null);
        output.present(new ExploreResponse.Guessed(buildingId, correct, remaining, result));
    }

    private ExploreResponse.Result result(Expedition expedition, CampusLandmark target) {
        Expedition.Score score = expedition.score();
        ExploreResponse.LandmarkSummary summary = target.wikipediaTitle() == null ? null
                : info.summary(target.wikipediaTitle()).orElse(null);
        return new ExploreResponse.Result(expedition.isWon(), target.name(), target.category(),
                target.buildingId(), expedition.map().buildingOf(target).centre(), score.routePoints(),
                score.efficiency(), score.identifyPoints(), score.multiplier(), score.total(),
                expedition.metresWalked(), expedition.shortestToZone(),
                Duration.between(expedition.startedAt(), expedition.zoneReachedAt().orElseThrow()).toSeconds(),
                Expedition.CHECK_LIMIT - expedition.checksRemaining(), expedition.guesses().size(),
                expedition.hintLevel().orElseThrow(), expedition.track(),
                expedition.revealedShortestRoute().orElseThrow(), expedition.checks(), summary);
    }
}
