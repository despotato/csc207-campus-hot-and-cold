package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;

import java.util.Objects;

public final class ChooseHintInteractor implements ChooseHintInputBoundary {
    private final ExpeditionSession session;
    private final LandmarkClueGateway clues;
    private final ExploreOutputBoundary output;

    public ChooseHintInteractor(ExpeditionSession session, ExploreOutputBoundary output) {
        this(session, LandmarkClueGateway.NONE, output);
    }

    public ChooseHintInteractor(ExpeditionSession session, LandmarkClueGateway clues, ExploreOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.clues = Objects.requireNonNull(clues);
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
            String hint = expedition.chooseHint(level, target -> target.wikipediaTitle() == null
                    ? java.util.Optional.empty() : clues.clue(target.wikipediaTitle(), target.name()));
            var zone = expedition.revealedZone().orElseThrow();
            output.present(new ExploreResponse.HintChosen(level, level.multiplier(), hint,
                    expedition.map().buildingsIn(zone).stream().map(CampusMap.Building::id).toList()));
        } catch (IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
        }
    }
}
