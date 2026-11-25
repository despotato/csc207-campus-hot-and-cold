package campus.domain.explore;

/**
 * Round settings chosen before an expedition starts.
 *
 * <p>Extension point (team task "Difficulty levels"): only {@link #NORMAL} is defined today.
 * Add EASY and HARD presets here (e.g. a bigger zone and more checks for easy; fewer checks and a
 * farther start for hard), cover them with tests, and let the player choose one on the start screen.
 * The rules in {@link Expedition} already read every value from this record.
 */
public record Difficulty(String name, int checks, double zoneRadius, double minStartDistance,
                         double maxStartDistance) {
    public static final Difficulty NORMAL = new Difficulty("normal", 6, 90, 450, 900);

    public Difficulty {
        if (name == null || name.isBlank() || checks < 0 || !(zoneRadius > 0)
                || !(minStartDistance > zoneRadius) || !(maxStartDistance >= minStartDistance)) {
            throw new IllegalArgumentException("Invalid difficulty settings.");
        }
    }

    /** Looks up a preset by name; unknown names are rejected so the browser can't invent settings. */
    public static Difficulty named(String name) {
        if (NORMAL.name().equalsIgnoreCase(name)) {
            return NORMAL;
        }
        throw new IllegalArgumentException("Unknown difficulty: " + name);
    }
}
