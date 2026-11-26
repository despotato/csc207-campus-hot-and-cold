package campus.application.explore;

public interface ResumeDailyInputBoundary {
    /** Continues today's unfinished daily on a campus, after a reload or an app restart. */
    void resume(String campusId);
}
