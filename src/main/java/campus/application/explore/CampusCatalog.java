package campus.application.explore;

import campus.domain.explore.CampusMap;

import java.util.List;
import java.util.Optional;

/** The campuses players can choose from. Adding a campus means adding a pack, not changing use cases. */
public interface CampusCatalog {
    List<String> campusIds();

    Optional<CampusMap> campus(String id);

    /** The campus used when the player hasn't chosen one. */
    default String defaultCampusId() {
        return campusIds().getFirst();
    }
}
