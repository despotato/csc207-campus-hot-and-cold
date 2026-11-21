package campus.application;

public interface StartRoundOutputBoundary {
    void present(StartRoundResponse response);
    void presentFailure(String message);
}
