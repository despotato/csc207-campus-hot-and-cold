package campus.application.explore;

import campus.domain.explore.Expedition;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Optional;

/** Keeps today's daily round on disk, so it can be resumed after a reload or an app restart. */
public interface DailyRunStore {
    void save(String campusId, LocalDate date, Expedition.Snapshot round) throws IOException;

    Optional<Expedition.Snapshot> load(String campusId, LocalDate date) throws IOException;

    /** For tests and setups without saving: nothing is kept. */
    DailyRunStore NONE = new DailyRunStore() {
        @Override
        public void save(String campusId, LocalDate date, Expedition.Snapshot round) { }

        @Override
        public Optional<Expedition.Snapshot> load(String campusId, LocalDate date) {
            return Optional.empty();
        }
    };
}
