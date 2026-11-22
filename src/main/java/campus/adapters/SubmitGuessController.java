package campus.adapters;

import campus.application.SubmitGuessInputBoundary;
import campus.application.SubmitGuessRequest;

/** Routes the UI action through the submit-guess input port. */
public record SubmitGuessController(SubmitGuessInputBoundary input) {
    public void submit(long landmarkId) {
        input.execute(new SubmitGuessRequest(landmarkId));
    }
}

