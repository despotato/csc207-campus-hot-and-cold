package campus.application.explore;

import java.util.Optional;

/**
 * Supplies an outside clue about the hidden landmark for the hard hint.
 *
 * <p>Extension point (team task "Wikipedia clue hint"): the game currently uses {@link #NONE}, so the
 * hard hint falls back to the target's direction within the zone. An infrastructure implementation
 * can call the MediaWiki API for the landmark's article, pick a sentence, and blank out the landmark's
 * name and obvious give-aways. Implementations must never return text that names the landmark.
 */
@FunctionalInterface
public interface LandmarkClueGateway {
    /** Best effort: empty when no safe clue is available or the service fails. */
    Optional<String> clue(String wikipediaTitle, String landmarkName);

    LandmarkClueGateway NONE = (title, name) -> Optional.empty();
}
