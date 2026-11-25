package campus.application;

import campus.domain.Round;

import java.util.List;
import java.util.Objects;

/** Applies one guess to the shared round and publishes only permitted feedback. */
public final class SubmitGuessInteractor implements SubmitGuessInputBoundary {
    private final GameSession session;
    private final SubmitGuessOutputBoundary output;

    public SubmitGuessInteractor(GameSession session, SubmitGuessOutputBoundary output) {
        this.session = Objects.requireNonNull(session);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void execute(SubmitGuessRequest request) {
        if (request == null) {
            output.presentFailure("Choose a landmark before guessing.");
            return;
        }
        Round round = session.findCurrentRound().orElse(null);
        if (round == null) {
            output.presentFailure("Start a round first.");
            return;
        }
        try {
            round.submitGuess(request.landmarkId());
            output.present(response(round, true, null));
        } catch (IllegalArgumentException exception) {
            output.present(response(round, false, safeDomainMessage(exception)));
        } catch (IllegalStateException exception) {
            output.present(response(round, false, "This round is already complete."));
        }
    }

    private static SubmitGuessResponse response(Round round, boolean accepted, String error) {
        boolean completed = round.revealedLandmark().isPresent();
        var guesses = round.guesses();
        List<StartRoundResponse.Candidate> candidates = completed ? List.of() : round.landmarks().stream()
                .filter(landmark -> guesses.stream()
                        .noneMatch(guess -> guess.landmark().id() == landmark.id()))
                .map(landmark -> new campus.application.StartRoundResponse.Candidate(
                        landmark.id(), landmark.name()))
                .toList();
        List<SubmitGuessResponse.Feedback> feedback = guesses.stream()
                .map(guess -> new SubmitGuessResponse.Feedback(guess.landmark().id(), guess.landmark().name(),
                        guess.heat(), guess.trend(), completed ? guess.distanceMetres() : null))
                .toList();
        SubmitGuessResponse.RevealedLandmark answer = completed
                ? round.revealedLandmark().map(landmark ->
                        new SubmitGuessResponse.RevealedLandmark(landmark.id(), landmark.name())).orElseThrow()
                : null;
        String status = error;
        if (status == null) {
            status = completed
                    ? (round.isWon() ? "Solved in " + guesses.size() + " guesses. Score: " + round.score() + "."
                    : "No guesses remaining. Score: 0.")
                    : round.guessesRemaining() + " guesses left.";
        }
        return new SubmitGuessResponse(accepted, completed, round.isWon(), round.guessesRemaining(),
                round.score(), status, candidates, feedback, answer);
    }

    private static String safeDomainMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if ("You already guessed that landmark.".equals(message)) {
            return message;
        }
        return "Unknown landmark.";
    }
}

