package campus.domain.explore;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Shared synthetic campus for tests outside this package. */
public final class TestCampus {
    public static final LocalDate DAILY_DATE = LocalDate.of(2026, 11, 25);

    private TestCampus() { }

    public static CampusMap map() {
        return ExpeditionTest.campus();
    }

    /** The same campus with one daily puzzle (Target Hall, zone at (1000, 75), start at the west end). */
    public static CampusMap withDaily() {
        CampusMap base = ExpeditionTest.campus();
        List<CampusMap.Building> buildings = new java.util.ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            buildings.add(base.building(id).orElseThrow());
        }
        return new CampusMap("test", "Test campus", ZoneOffset.UTC,
                Map.of(DAILY_DATE, new CampusMap.DailyPuzzle(1, ExpeditionTest.at(1000, 75), ExpeditionTest.START)),
                buildings, base.landmarks(), base.openSpace());
    }
}
