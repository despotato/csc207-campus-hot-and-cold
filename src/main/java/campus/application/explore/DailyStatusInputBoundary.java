package campus.application.explore;

public interface DailyStatusInputBoundary {
    /** Reports today's daily on a campus: not started, in progress, finished (with the result), or used up. */
    void status(String campusId);
}
