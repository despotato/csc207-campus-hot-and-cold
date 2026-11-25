package campus.application.explore;

import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class TravelInteractor implements TravelInputBoundary {
    private final Clock clock;
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public TravelInteractor(Clock clock, ExpeditionSession session, ExploreOutputBoundary output) {
        this.clock = Objects.requireNonNull(clock);
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(UUID roundId, GeoPoint destination) {
        Expedition expedition = session.current(roundId).orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed(ExpeditionSession.STALE));
            return;
        }
        int before = expedition.track().size();
        try {
            expedition.travelTo(destination, clock.instant());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
            return;
        }
        var track = expedition.track();
        output.present(new ExploreResponse.Travelled(expedition.position(), track.subList(before, track.size()),
                expedition.metresWalked(), Duration.between(expedition.startedAt(), clock.instant()).toSeconds()));
    }
}
