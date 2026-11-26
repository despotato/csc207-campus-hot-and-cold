package campus.domain.explore;

/** Badges a finished round can earn, shown with the result. See {@link Expedition#achievements()}. */
public enum Achievement {
    /** The zone was never found, but a check came within near-miss range. */
    ALMOST_ALMOST("Ahh, almost almost", "So close you could feel the heat"),
    /** The very first check found the zone: no checks spent searching. */
    ABSOLUTE_ZERO("Absolute Zero", "Too cold"),
    /** A perfect round: {@link Expedition#MAX_SCORE} points. */
    SUPERNOVA("Supernova", "Too hot to handle");

    private final String title;
    private final String subtitle;

    Achievement(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }
}
