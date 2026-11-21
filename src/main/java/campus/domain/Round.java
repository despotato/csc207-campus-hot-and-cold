package campus.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Round {
    public static final int GUESS_LIMIT = 6;

    private final UUID id = UUID.randomUUID();
    private final Campus campus;
    private final List<Landmark> landmarks;
    private final Landmark target;
    private final List<Guess> guesses = new ArrayList<>();

    private CompletedRoundResult completedResult;

    public Round(List<Landmark> landmarks, Landmark target) {
        this(Campus.ST_GEORGE, landmarks, target);
    }

    public Round(Campus campus, List<Landmark> landmarks, Landmark target) {
        this.campus = Objects.requireNonNull(campus);
        if (campus.name() == null || campus.name().isBlank()) {
            throw new IllegalArgumentException("A round needs a campus name.");
        }
        this.landmarks = List.copyOf(landmarks);
        if (landmarks.size() < GUESS_LIMIT || !landmarks.contains(target)
                || landmarks.stream().map(Landmark::id).distinct().count() != landmarks.size()) {
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

    public Optional<CompletedRoundResult> completedResult() {
        return Optional.ofNullable(completedResult);
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
        if (revealedLandmark().isPresent()) {
            var attempts = guesses.stream()
                    .map(guess -> new CompletedRoundResult.Attempt(guess.landmark(), guess.distanceMetres()))
                    .toList();
            completedResult = new CompletedRoundResult(id, campus.name(), Instant.now(),
                    target, attempts, isWon(), score());
        }
        return result;
    }
}

