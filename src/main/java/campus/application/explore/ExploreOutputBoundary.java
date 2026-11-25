package campus.application.explore;

/** Receives the outcome of every explore use case. */
public interface ExploreOutputBoundary {
    void present(ExploreResponse response);
}
