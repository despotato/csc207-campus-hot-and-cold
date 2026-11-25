package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;

import java.util.Objects;

public final class ChooseHintInteractor implements ChooseHintInputBoundary {
    private final ExpeditionSession session;
    private final ExploreOutputBoundary output;

    public ChooseHintInteractor(ExpeditionSession session, ExploreOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(Expedition.HintLevel level) {
        Expedition expedition = session.current().orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed("Start a round first."));
            return;
        }
        if (level == null) {
            output.present(new ExploreResponse.Failed("Choose easy, normal, or hard."));
            return;
        }
        try {
            String hint = expedition.chooseHint(level);
            var zone = expedition.revealedZone().orElseThrow();
            output.present(new ExploreResponse.HintChosen(level, level.multiplier(), hint,
                    expedition.map().buildingsIn(zone).stream().map(CampusMap.Building::id).toList()));
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
        }
    }
}
