package campus.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An immutable recap. Active rounds cannot produce one. */
public record CompletedRoundResult(UUID id, String campus, Instant completedAt, Landmark answer,
                                   List<Attempt> guesses, boolean won, int score) {
    public CompletedRoundResult {
        Objects.requireNonNull(id);
        Objects.requireNonNull(completedAt);
        Objects.requireNonNull(answer);
        guesses = List.copyOf(guesses);
        if (campus == null || campus.isBlank() || guesses.isEmpty() || guesses.size() > Round.GUESS_LIMIT
                || guesses.stream().map(guess -> guess.landmark().id()).distinct().count() != guesses.size()) {
            throw new IllegalArgumentException("Invalid completed round.");
        }
        for (Attempt guess : guesses) {
            double expected = guess.landmark().distanceTo(answer);
            // Allow for floating-point differences across runtimes.
            double tolerance = 0.000001 + expected * 0.00000001;
            if (Math.abs(guess.distanceMetres() - expected) > tolerance) {
                throw new IllegalArgumentException("Guess distance does not match the landmarks.");
            }
        }
        boolean correctlySolved = guesses.getLast().landmark().equals(answer)
                && guesses.getLast().distanceMetres() == 0;
        long answerGuesses = guesses.stream().filter(guess -> guess.landmark().id() == answer.id()).count();
        if (won != correctlySolved || answerGuesses != (won ? 1 : 0)
                || (!won && guesses.size() != Round.GUESS_LIMIT)
                || score != (won ? Round.GUESS_LIMIT + 1 - guesses.size() : 0)) {
            throw new IllegalArgumentException("Result guesses, outcome, and score are inconsistent.");
        }
    }

    public record Attempt(Landmark landmark, double distanceMetres) {
        public Attempt {
            Objects.requireNonNull(landmark);
            if (!Double.isFinite(distanceMetres) || distanceMetres < 0) {
                throw new IllegalArgumentException("Invalid guess distance.");
            }
        }
    }
}
