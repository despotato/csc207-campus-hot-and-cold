package campus.domain.explore;

import campus.infrastructure.PackCatalog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real campus packs: every upcoming daily opens with a zone clue, and its photo never shows the target. */
class DailyClueTest {
    @Test void everyUpcomingDailyOnEveryCampusHasAZoneClueThatIsNeverTheTarget() throws Exception {
        PackCatalog catalog = new PackCatalog();
        for (String id : catalog.campusIds()) {
            CampusMap map = catalog.campus(id).orElseThrow();
            for (LocalDate date = LocalDate.of(2026, 11, 27); date.isBefore(LocalDate.of(2027, 12, 6));
                 date = date.plusDays(1)) {
                CampusMap.DailyPuzzle puzzle = map.dailyPuzzle(date).orElseThrow();
                CampusMap.CampusLandmark target = map.landmark(puzzle.landmarkId()).orElseThrow();
                ZoneClue clue = new ZoneClue(map, new Zone(puzzle.zoneCentre(), Difficulty.NORMAL.zoneRadius()),
                        target.buildingId());
                LocalDate day = date;
                assertTrue(clue.text().isPresent(), () -> id + " " + day + ": no zone clue");
                assertNotEquals(Optional.of(target.buildingId()), clue.building(),
                        () -> id + " " + day + ": the photo shows the target");
            }
        }
    }
}
