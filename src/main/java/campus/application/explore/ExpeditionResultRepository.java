package campus.application.explore;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores finished explore rounds.
 *
 * <p>Extension point (team task "Save and review explore results"): the game currently uses
 * {@link #DISABLED}, so nothing is saved. A file-backed implementation in the infrastructure layer,
 * plus a "review explore history" use case and a history panel on the explore page, complete the
 * feature. {@link GuessBuildingInteractor} already calls {@link #save} exactly once per finished
 * round, and reports a failed save to the player without losing the on-screen result.
 */
public interface ExpeditionResultRepository {
    void save(UUID roundId, String campus, Instant finishedAt, String difficulty, ExploreResponse.Result result)
            throws IOException;

    /** False when saving is not set up; the page then hides save messages. */
    default boolean enabled() {
        return true;
    }

    ExpeditionResultRepository DISABLED = new ExpeditionResultRepository() {
        @Override
        public void save(UUID roundId, String campus, Instant finishedAt, String difficulty,
                         ExploreResponse.Result result) { }

        @Override
        public boolean enabled() {
            return false;
        }
    };
}
