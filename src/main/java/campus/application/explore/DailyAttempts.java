package campus.application.explore;

import java.io.IOException;
import java.time.LocalDate;

/** Remembers who has played each day's daily puzzle, so it can be tried only once. */
public interface DailyAttempts {
    /** Records today's attempt; returns false if this player has already tried this campus's daily on that date. */
    boolean claim(String campusId, LocalDate date) throws IOException;

    /** Whether this player has already started that daily (without claiming it). */
    default boolean claimed(String campusId, LocalDate date) throws IOException {
        return false;
    }
}
