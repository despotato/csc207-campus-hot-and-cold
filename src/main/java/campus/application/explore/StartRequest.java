package campus.application.explore;

import campus.domain.explore.Difficulty;

import java.util.Objects;

/**
 * What the player chose on the start screen. {@code DAILY} is the shared puzzle of the day (one try, always
 * normal difficulty); {@code UNLIMITED} is a fresh random round every time.
 */
public record StartRequest(String campusId, Mode mode, Difficulty difficulty) {
    public enum Mode { DAILY, UNLIMITED }

    public StartRequest {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(difficulty);
    }
}
