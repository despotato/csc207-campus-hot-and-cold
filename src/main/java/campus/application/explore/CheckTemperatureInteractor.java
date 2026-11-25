package campus.application.explore;

import campus.domain.explore.Expedition;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Spends a hot/cold check; a check inside the zone finds it. With no checks left, this is the final search. */
public final class CheckTemperatureInteractor implements CheckTemperatureInputBoundary {
    private final Clock clock;
    private final ExpeditionSession session;
    private final RoundFinisher finisher;
    private final ExploreOutputBoundary output;

    public CheckTemperatureInteractor(Clock clock, ExpeditionSession session, RoundFinisher finisher,
                                      ExploreOutputBoundary output) {
        this.clock = Objects.requireNonNull(clock);
        this.session = Objects.requireNonNull(session);
        this.finisher = Objects.requireNonNull(finisher);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(UUID roundId) {
        Expedition expedition = session.current(roundId).orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed(ExpeditionSession.STALE));
            return;
        }
        try {
            if (expedition.checksRemaining() > 0) {
                Expedition.Check check = expedition.check(clock.instant());
                // The exact distance stays hidden; only the band, trend, and whether this is the zone.
                output.present(new ExploreResponse.Checked(check.point(), check.heat(), check.trend(),
                        expedition.checksRemaining(), false, zone(expedition), null));
            } else {
                boolean found = expedition.finalSearch(clock.instant());
                output.present(new ExploreResponse.Checked(expedition.position(), null, null, 0, true,
                        found ? zone(expedition) : null, found ? null : finisher.finish(expedition)));
            }
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
        }
    }

    private static ExploreResponse.ZoneView zone(Expedition expedition) {
        return expedition.stage() == Expedition.Stage.IDENTIFY ? expedition.revealedZone()
                .map(zone -> new ExploreResponse.ZoneView(zone.centre(), zone.radiusMetres())).orElse(null) : null;
    }
}
