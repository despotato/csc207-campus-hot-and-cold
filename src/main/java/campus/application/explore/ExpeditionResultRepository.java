package campus.application.explore;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores and reads completed Explore rounds. */
public interface ExpeditionResultRepository {
    void save(SavedExpedition round) throws IOException;

    Optional<SavedExpedition> find(UUID roundId) throws IOException;

    /** Returns valid rounds newest first; implementations may omit individual corrupt records. */
    List<SavedExpedition> all() throws IOException;

    /** False when saving is not set up; the page then hides save messages. */
    default boolean enabled() {
        return true;
    }

    ExpeditionResultRepository DISABLED = new ExpeditionResultRepository() {
        @Override
        public void save(SavedExpedition round) { }

        @Override
        public Optional<SavedExpedition> find(UUID roundId) {
            return Optional.empty();
        }

        @Override
        public List<SavedExpedition> all() {
            return List.of();
        }

        @Override
        public boolean enabled() {
            return false;
        }
    };
}
