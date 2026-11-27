package campus.domain.explore;

import java.time.Instant;

/**
 * Badges a finished round can earn, shown with the result and in the page's achievements list. See
 * {@link Expedition#achievements()}. The page keeps a copy of this catalogue (ACHIEVEMENTS in index.html).
 */
public enum Achievement {
    /** Any round started before {@link #BETA_ENDS}. */
    BETA_PLAYER("Beta Player", "Here before launch", "Finish a round while the game is in beta (before 2027)."),
    /** The zone was never found, but a check came within near-miss range. */
    ALMOST_ALMOST("Ahh almost almost", "", "Miss the zone, but get a check within 150 m of finding it."),
    /** The very first check found the zone: no checks spent searching. */
    ABSOLUTE_ZERO("Absolute Zero", "Too cold", "Find the zone with your very first check."),
    /** A perfect round: {@link Expedition#MAX_SCORE} points. */
    SUPERNOVA("Supernova", "Too hot to handle", "Score a perfect 1000.");

    /** Midnight in Toronto, January 1, 2027. */
    public static final Instant BETA_ENDS = Instant.parse("2027-01-01T05:00:00Z");

    private final String title;
    private final String subtitle;
    private final String description;

    Achievement(String title, String subtitle, String description) {
        this.title = title;
        this.subtitle = subtitle;
        this.description = description;
    }

    public String title() {
        return title;
    }

    /** Empty when the achievement has none. */
    public String subtitle() {
        return subtitle;
    }

    /** How to earn it. */
    public String description() {
        return description;
    }
}
