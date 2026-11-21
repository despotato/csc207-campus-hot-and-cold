package campus.application;

import campus.domain.Round;

/** In-memory round shared by use cases. Commands must be serialized by the UI. */
public final class GameSession {
    private Round round;

    public void start(Round next) {
        round = java.util.Objects.requireNonNull(next);
    }

    public Round currentRound() {
        if (round == null) {
            throw new IllegalStateException("Start a round first.");
        }
        return round;
    }
}
