package campus.application.explore;

import java.util.Optional;

/** Looks up a short public description of a landmark, for the end-of-round reveal. */
@FunctionalInterface
public interface LandmarkInfoGateway {
    /** Best effort: returns empty when the title is unknown or the service is unavailable. */
    Optional<ExploreResponse.LandmarkSummary> summary(String title);

    LandmarkInfoGateway NONE = title -> Optional.empty();
}
