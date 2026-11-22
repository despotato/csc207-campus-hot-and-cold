package campus.adapters;

import campus.application.StartRoundResponse.Candidate;

import java.util.List;

/** Immutable Swing-ready state; it contains no hidden target coordinates or active distances. */
public record SubmitGuessViewModel(boolean guessAccepted, boolean completed, boolean won,
                                   int guessesRemaining, int score, String status,
                                   List<Candidate> candidates, List<String> feedbackLines,
                                   String revealedAnswer) {
    public SubmitGuessViewModel {
        candidates = List.copyOf(candidates);
        feedbackLines = List.copyOf(feedbackLines);
    }
}

