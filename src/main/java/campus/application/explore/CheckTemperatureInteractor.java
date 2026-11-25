package campus.application.explore;

import campus.domain.explore.Expedition;

import java.util.Objects;

public final class CheckTemperatureInteractor implements CheckTemperatureInputBoundary {
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public CheckTemperatureInteractor(ExpeditionSession session, ExploreOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute() {
        Expedition expedition = session.current().orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed("Start a round first."));
            return;
        }
        try {
            Expedition.Check check = expedition.check();
            // The exact distance stays hidden; only the band and trend are shown.
            output.present(new ExploreResponse.Checked(check.point(), check.heat(), check.trend(),
                    expedition.checksRemaining()));
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
        }
    }
}
