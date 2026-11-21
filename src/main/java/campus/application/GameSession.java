package campus.application;

import campus.domain.Round;

import java.util.Optional;

/** In-memory round shared by use cases. Commands must be serialized by the UI. */
public final class GameSession {
    private Round round;

    public void start(Round next) {
        round = java.util.Objects.requireNonNull(next);
    }

    public Round currentRound() {
        return findCurrentRound().orElseThrow(() -> new IllegalStateException("Start a round first."));
    }

    public Optional<Round> findCurrentRound() {
        return Optional.ofNullable(round);
    }
}
