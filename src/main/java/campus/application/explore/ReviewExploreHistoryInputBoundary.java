package campus.application.explore;

import java.util.UUID;

/** Actions for listing and reopening saved Explore rounds. */
public interface ReviewExploreHistoryInputBoundary {
    void history(String campusId);

    void recap(UUID roundId);
}
