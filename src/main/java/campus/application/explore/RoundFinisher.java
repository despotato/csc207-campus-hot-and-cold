package campus.application.explore;

import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.Expedition;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * Builds the end-of-round reveal (looking up the target's summary) and saves the result. Shared by the
 * use cases that can end a round: a final guess, or a final search outside the zone.
 */
public final class RoundFinisher {
    private final LandmarkInfoGateway info;
    private final ExpeditionResultRepository results;
    private final Clock clock;

    public RoundFinisher(LandmarkInfoGateway info, ExpeditionResultRepository results, Clock clock) {
        this.info = Objects.requireNonNull(info);
        this.results = Objects.requireNonNull(results);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Call once, after the expedition has finished. */
    public ExploreResponse.Finished finish(Expedition expedition) {
        CampusLandmark target = expedition.revealedTarget()
                .orElseThrow(() -> new IllegalStateException("The round isn't over."));
        ExploreResponse.Result result = result(expedition, target);
        String saveError = null;
        if (results.enabled()) {
            try {
                results.save(expedition.id(), expedition.map().name(), clock.instant(),
                        expedition.difficulty().name(), result);
            } catch (IOException exception) {
                saveError = "Your result could not be saved.";
            }
        }
        return new ExploreResponse.Finished(result, saveError);
    }

    private ExploreResponse.Result result(Expedition expedition, CampusLandmark target) {
        Expedition.Score score = expedition.score();
        ExploreResponse.LandmarkSummary summary = target.wikipediaTitle() == null ? null
                : info.summary(target.wikipediaTitle()).orElse(null);
        Long secondsToZone = expedition.zoneReachedAt()
                .map(at -> Duration.between(expedition.startedAt(), at).toSeconds()).orElse(null);
        var zone = expedition.revealedZone().orElseThrow();
        return new ExploreResponse.Result(expedition.isWon(), target.name(), target.category(),
                target.buildingId(), expedition.map().buildingOf(target).centre(), score.routePoints(),
                score.efficiency(), score.identifyPoints(), score.multiplier(), score.total(),
                expedition.metresWalked(), expedition.shortestToZone(), secondsToZone, expedition.checksUsed(),
                expedition.guesses().size(), expedition.hintLevel().orElse(null),
                new ExploreResponse.ZoneView(zone.centre(), zone.radiusMetres()), expedition.track(),
                expedition.revealedShortestRoute().orElseThrow(), expedition.checks(), summary);
    }
}
