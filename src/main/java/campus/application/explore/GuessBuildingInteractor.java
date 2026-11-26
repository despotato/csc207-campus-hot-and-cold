package campus.application.explore;

import campus.domain.explore.Expedition;

import java.util.Objects;
import java.util.UUID;

public final class GuessBuildingInteractor implements GuessBuildingInputBoundary {
    private final ExpeditionSession session;
    private final RoundFinisher finisher;
    private final ExploreOutputBoundary output;

    public GuessBuildingInteractor(ExpeditionSession session, RoundFinisher finisher, ExploreOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.finisher = Objects.requireNonNull(finisher);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(UUID roundId, int buildingId) {
        Expedition expedition = session.current(roundId).orElse(null);
        if (expedition == null) {
            output.present(new ExploreResponse.Failed(ExpeditionSession.STALE));
            return;
        }
        boolean correct;
        try {
            correct = expedition.guess(buildingId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            output.present(new ExploreResponse.Failed(exception.getMessage()));
            return;
        }
        session.checkpoint(true);
        int remaining = Expedition.GUESS_LIMIT - expedition.guesses().size();
        ExploreResponse.Finished finished = expedition.stage() == Expedition.Stage.FINISHED
                ? finisher.finish(expedition) : null;
        output.present(new ExploreResponse.Guessed(buildingId, correct, remaining, finished));
    }
}
