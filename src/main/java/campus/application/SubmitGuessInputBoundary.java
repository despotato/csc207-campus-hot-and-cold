package campus.application;

/** Input port for submitting one landmark guess. */
public interface SubmitGuessInputBoundary {
    void execute(SubmitGuessRequest request);
}

