package campus.application;

/** Output port implemented by the presentation layer. */
public interface SubmitGuessOutputBoundary {
    void present(SubmitGuessResponse response);
    void presentFailure(String message);
}

