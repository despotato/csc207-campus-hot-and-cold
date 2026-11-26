package campus.application.explore;

import campus.domain.explore.FootTrail;

import java.io.IOException;
import java.util.List;

/**
 * Where anonymous foot trails are kept. Today a file on this computer; the port lets a shared online store
 * (see docs/SHARED_PATHS.md) replace it without changing any use case.
 */
public interface FootTrailRepository {
    void record(FootTrail trail) throws IOException;

    /** Up to {@code limit} trails for a campus, newest first. */
    List<FootTrail> recent(String campusId, int limit) throws IOException;

    /** Null Object: records nothing and has no trails (tests, or setups without storage). */
    FootTrailRepository NONE = new FootTrailRepository() {
        @Override
        public void record(FootTrail trail) {
            // Nothing is kept.
        }

        @Override
        public List<FootTrail> recent(String campusId, int limit) {
            return List.of();
        }
    };
}
