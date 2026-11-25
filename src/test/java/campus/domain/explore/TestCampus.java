package campus.domain.explore;

/** Shared synthetic campus for tests outside this package. */
public final class TestCampus {
    private TestCampus() { }

    public static CampusMap map() {
        return ExpeditionTest.campus();
    }
}
