package campus.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Round {
    public static final int GUESS_LIMIT = 6;

    private final List<Landmark> landmarks;
    private final Landmark target;
    private final List<Guess> guesses = new ArrayList<>();

    public Round(List<Landmark> landmarks, Landmark target) {
        this.landmarks = List.copyOf(landmarks);
        if (landmarks.size() < GUESS_LIMIT || !landmarks.contains(target)) {
            throw new IllegalArgumentException("A round needs six landmarks and a valid target.");
        }
        this.target = target;
    }

    public List<Landmark> landmarks() {
        return landmarks;
    }

    public List<Guess> guesses() {
        return List.copyOf(guesses);
    }

    public int guessesRemaining() {
        return GUESS_LIMIT - guesses.size();
    }

    public boolean isWon() {
        return !guesses.isEmpty() && guesses.getLast().heat() == Guess.Heat.CORRECT;
    }

    public Optional<Landmark> revealedLandmark() {
        return isWon() || guessesRemaining() == 0 ? Optional.of(target) : Optional.empty();
    }

    public int score() {
        return isWon() ? guessesRemaining() + 1 : 0;
    }

    public Guess submitGuess(long landmarkId) {
        if (revealedLandmark().isPresent()) {
            throw new IllegalStateException("This round is already complete.");
        }
        if (guesses.stream().anyMatch(guess -> guess.landmark().id() == landmarkId)) {
            throw new IllegalArgumentException("You already guessed that landmark.");
        }
        Landmark landmark = landmarks.stream().filter(item -> item.id() == landmarkId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown landmark."));
        Guess previous = guesses.isEmpty() ? null : guesses.getLast();
        Guess result = Guess.evaluate(landmark, target, previous);
        guesses.add(result);
        return result;
    }
}
