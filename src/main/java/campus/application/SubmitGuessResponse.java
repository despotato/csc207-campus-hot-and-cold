package campus.application;

import campus.domain.Guess;

import java.util.List;
import campus.application.StartRoundResponse.Candidate;

/** Public guess output. Exact distances and the answer are withheld until completion. */
public record SubmitGuessResponse(
        boolean guessAccepted,
        boolean completed,
        boolean won,
        int guessesRemaining,
        int score,
        String status,
        List<Candidate> candidates,
        List<Feedback> feedback,
        RevealedLandmark answer) {

    public SubmitGuessResponse {
        candidates = List.copyOf(candidates);
        feedback = List.copyOf(feedback);
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("A response needs a status message.");
        }
        if (completed != (answer != null)) {
            throw new IllegalArgumentException("The answer is available only after completion.");
        }
        if (!completed && feedback.stream().anyMatch(item -> item.distanceMetres() != null)) {
            throw new IllegalArgumentException("Exact distances are hidden during an active round.");
        }
        if (completed && feedback.stream().anyMatch(item -> item.distanceMetres() == null)) {
            throw new IllegalArgumentException("A completed recap needs every exact distance.");
        }
    }

    public record Feedback(long landmarkId, String landmarkName, Guess.Heat heat,
                           Guess.Trend trend, Double distanceMetres) { }

    public record RevealedLandmark(long id, String name) { }
}

