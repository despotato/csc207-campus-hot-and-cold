package campus.domain.explore;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which words in a landmark's name would give it away if a hint mentioned them. */
public final class LandmarkNames {
    private static final Set<String> GENERIC = Set.of("hall", "college", "building", "library", "centre", "center",
            "house", "university", "toronto", "residence", "faculty", "school", "theatre", "information", "technology",
            "science", "sciences", "studies", "laboratories", "laboratory", "campus", "student", "students", "services",
            "management", "innovation", "chemical", "physical", "engineering", "health", "rare", "book", "stadium",
            "arena", "tower", "facility", "central", "exams", "with", "from", "the", "and", "for");

    private LandmarkNames() { }

    /** Distinctive words (e.g. "robarts", "wetmore"), lower case, at least four letters. */
    public static List<String> distinctiveWords(String name) {
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 4 && !GENERIC.contains(word)).distinct().toList();
    }

    /** True if the text names the landmark or uses one of its distinctive words. */
    public static boolean givesAway(String text, String name) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return distinctiveWords(name).stream().anyMatch(word -> lower.matches("(?s).*\\b" + word + "\\b.*"));
    }
}
