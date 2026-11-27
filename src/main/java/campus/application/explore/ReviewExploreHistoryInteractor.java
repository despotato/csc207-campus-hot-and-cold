package campus.application.explore;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Loads saved Explore history without depending on the file format or browser. */
public final class ReviewExploreHistoryInteractor implements ReviewExploreHistoryInputBoundary {
    private final CampusCatalog campuses;
    private final ExpeditionResultRepository results;
    private final ExploreOutputBoundary output;

    public ReviewExploreHistoryInteractor(CampusCatalog campuses, ExpeditionResultRepository results,
                                          ExploreOutputBoundary output) {
        this.campuses = Objects.requireNonNull(campuses);
        this.results = Objects.requireNonNull(results);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void history(String campusId) {
        if (campusId != null && campuses.campus(campusId).isEmpty()) {
            output.present(new ExploreResponse.Failed("Unknown campus."));
            return;
        }
        try {
            var rounds = results.all().stream()
                    .filter(round -> campusId == null || campusId.equals(round.campusId()))
                    .sorted(Comparator.comparing(SavedExpedition::finishedAt).reversed()
                            .thenComparing(SavedExpedition::id))
                    .toList();
            output.present(new ExploreResponse.History(rounds));
        } catch (IOException exception) {
            output.present(new ExploreResponse.Failed("Could not load Explore history. Please retry."));
        }
    }

    @Override
    public void recap(UUID roundId) {
        if (roundId == null) {
            output.present(new ExploreResponse.Failed("Choose a saved round."));
            return;
        }
        try {
            var round = results.find(roundId);
            if (round.isEmpty()) {
                output.present(new ExploreResponse.Failed("This saved round is missing."));
                return;
            }
            output.present(new ExploreResponse.Recap(round.get()));
        } catch (IOException exception) {
            output.present(new ExploreResponse.Failed("Could not read this saved round. Check the file and retry."));
        }
    }
}
