package campus.application.explore;

import java.util.List;

/**
 * Supplies facts about a landmark for hints, with the landmark's name blanked out.
 * The first fact is the best known; later ones are more obscure. Best effort: an empty list when
 * nothing safe is available or the service fails.
 */
@FunctionalInterface
public interface LandmarkFactsGateway {
    List<String> facts(String wikipediaTitle, String landmarkName);

    /** Campus-aware lookup; existing gateways may ignore the context. */
    default List<String> facts(String wikipediaTitle, String landmarkName, String campusName) {
        return facts(wikipediaTitle, landmarkName);
    }

    LandmarkFactsGateway NONE = (title, name) -> List.of();
}
