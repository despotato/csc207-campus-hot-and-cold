package campus.application.explore;

import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.Expedition;
import campus.domain.explore.FootTrail;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Builds the end-of-round reveal (looking up the target's summary) and saves the result. Shared by the
 * use cases that can end a round: a final guess, or a final search outside the zone.
 */
public final class RoundFinisher {
    private final LandmarkInfoGateway info;
    private final ExpeditionResultRepository results;
    private final Clock clock;
    private final FootTrailRepository trails;
    private final Function<UUID, Optional<LocalDate>> dailyDate;

    public RoundFinisher(LandmarkInfoGateway info, ExpeditionResultRepository results, Clock clock) {
        this(info, results, clock, FootTrailRepository.NONE, ignored -> Optional.empty());
    }

    /** Also records each finished round's path, anonymously, for other players to see as a walking student. */
    public RoundFinisher(LandmarkInfoGateway info, ExpeditionResultRepository results, Clock clock,
                         FootTrailRepository trails) {
        this(info, results, clock, trails, ignored -> Optional.empty());
    }

    /** Also tags saved results with the campus-local date of a daily round. */
    public RoundFinisher(LandmarkInfoGateway info, ExpeditionResultRepository results, Clock clock,
                         FootTrailRepository trails, Function<UUID, Optional<LocalDate>> dailyDate) {
        this.info = Objects.requireNonNull(info);
        this.results = Objects.requireNonNull(results);
        this.clock = Objects.requireNonNull(clock);
        this.trails = Objects.requireNonNull(trails);
        this.dailyDate = Objects.requireNonNull(dailyDate);
    }

    /** The reveal for a finished round, without saving it again (e.g. showing today's daily after a restart). */
    public ExploreResponse.Result describe(Expedition expedition) {
        CampusLandmark target = expedition.revealedTarget()
                .orElseThrow(() -> new IllegalStateException("The round isn't over."));
        return result(expedition, target);
    }

    /** Call once, after the expedition has finished. */
    public ExploreResponse.Finished finish(Expedition expedition) {
        CampusLandmark target = expedition.revealedTarget()
                .orElseThrow(() -> new IllegalStateException("The round isn't over."));
        ExploreResponse.Result result = result(expedition, target);
        String saveError = null;
        if (results.enabled()) {
            try {
                LocalDate date = dailyDate.apply(expedition.id()).orElse(null);
                results.save(new SavedExpedition(expedition.id(), expedition.map().id(), clock.instant(),
                        expedition.difficulty().name(), date == null ? "unlimited" : "daily", date, result));
            } catch (IOException exception) {
                saveError = "Your result could not be saved.";
            }
        }
        recordTrail(expedition);
        return new ExploreResponse.Finished(result, saveError);
    }

    /** Best effort: a trail that can't be stored never affects the player's round. */
    private void recordTrail(Expedition expedition) {
        if (expedition.track().size() < 2) {
            return;
        }
        try {
            trails.record(FootTrail.of(expedition.map().id(), expedition.track(), clock.instant()));
        } catch (IOException | IllegalArgumentException ignored) {
            // Ambient feature only.
        }
    }

    private static Double shortBy(Expedition expedition) {
        var shortBy = expedition.metresShortOfZone();
        return shortBy.isPresent() ? shortBy.getAsDouble() : null;
    }

    private ExploreResponse.Result result(Expedition expedition, CampusLandmark target) {
        Expedition.Score score = expedition.score();
        ExploreResponse.LandmarkSummary summary = target.wikipediaTitle() == null ? null
                : info.summary(target.wikipediaTitle()).orElse(null);
        Long secondsToZone = expedition.zoneReachedAt().isPresent() ? expedition.secondsElapsed(clock.instant()) : null;
        var zone = expedition.revealedZone().orElseThrow();
        return new ExploreResponse.Result(expedition.isWon(), target.name(), target.category(),
                target.buildingId(), expedition.map().buildingOf(target).centre(), score.routePoints(),
                score.efficiency(), score.guessPoints(), score.hintBonus(), score.checkBonus(),
                score.nearMissPoints(), shortBy(expedition), score.multiplier(), score.total(),
                expedition.metresWalked(), expedition.shortestToZone(), secondsToZone, expedition.checksUsed(),
                expedition.guesses().size(), expedition.hintLevel().orElse(null),
                new ExploreResponse.ZoneView(zone.centre(), zone.radiusMetres()), expedition.track(),
                expedition.revealedShortestRoute().orElseThrow(), expedition.checks(), expedition.achievements(),
                summary);
    }
}
