package campus.application.explore;

import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class MoveInteractor implements MoveInputBoundary {
    private final Clock clock;
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public MoveInteractor(Clock clock, ExpeditionSession session, ExploreOutputBoundary output) {
        this.clock = Objects.requireNonNull(clock);
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(List<GeoPoint> path) {
        Expedition expedition = session.current().orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed("Start a round first."));
            return;
        }
        try {
            expedition.moveAlong(path, clock.instant());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.present(new ExploreResponse.Rejected(exception.getMessage(), expedition.position(),
                    expedition.metresWalked()));
            return;
        }
        boolean reached = expedition.stage() != Expedition.Stage.FIND_ZONE;
        long elapsed = Duration.between(expedition.startedAt(),
                expedition.zoneReachedAt().orElse(clock.instant())).toSeconds();
        output.present(new ExploreResponse.Moved(expedition.metresWalked(), elapsed, reached,
                expedition.revealedZone().map(zone -> new ExploreResponse.ZoneView(zone.centre(),
                        zone.radiusMetres())).orElse(null)));
    }
}
