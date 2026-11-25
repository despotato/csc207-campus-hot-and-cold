package campus.application.explore;

public interface MoveInputBoundary {
    /** Moves to the spot {@code fraction} of the way from walkway node {@code from} to {@code to}. */
    void execute(int from, int to, double fraction);
}
