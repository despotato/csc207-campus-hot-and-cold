package campus.infrastructure;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CampusPackLoaderTest {
    @Test void jsonRoundTripsAndEscapesHtml() {
        Object value = Json.parse("{\"a\":[1,2.5,-3e2,true,false,null],\"b\":\"x\\\"y\\u00e9\\n\"}");
        Map<String, Object> object = Json.object(value);
        assertEquals(List.of(1.0, 2.5, -300.0, true, false), Json.array(object.get("a")).subList(0, 5));
        assertNull(Json.array(object.get("a")).get(5));
        assertEquals("x\"yé\n", object.get("b"));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("s", "</script>");
        out.put("n", List.of(1, 0.5));
        assertEquals("{\"s\":\"\\u003c/script\\u003e\",\"n\":[1,0.5]}", Json.write(out));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("1 2"));
    }

    @Test void stGeorgePackLoadsWithLandmarksBuildingsAndConnectedWalkways() throws Exception {
        CampusMap map = CampusPackLoader.load(CampusPackLoader.ST_GEORGE);
        assertEquals("University of Toronto — St. George", map.name());
        assertEquals(76, map.landmarks().size());
        assertTrue(map.landmarks().stream().anyMatch(l -> l.name().equals("Robarts Library")));
        assertTrue(map.landmarks().stream().anyMatch(l -> l.category().equals("Residence")));
        var robarts = map.landmarks().stream().filter(l -> l.name().equals("Robarts Library")).findFirst().orElseThrow();
        assertFalse(map.openSpace().isOpen(map.buildingOf(robarts).centre()), "buildings are solid");
        assertTrue(map.openSpace().openCells(4).size() > 30_000, "about two thirds of campus is walkable");
    }

    @Test void realCampusRoundsAreSetUpCorrectly() throws Exception {
        CampusMap map = CampusPackLoader.load(CampusPackLoader.ST_GEORGE);
        for (int seed = 0; seed < 10; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), Instant.EPOCH);
            assertTrue(map.openSpace().isOpen(round.start()), "starts outdoors");
            assertTrue(round.shortestToZone() >= 300, "a real walk to the zone");
            assertTrue(round.shortestToZone() < 2000);
        }
    }

    @Test void catalogListsCampusesAndLoadsTheirDailyPuzzles() throws Exception {
        var catalog = new PackCatalog();
        assertEquals(List.of("st-george", "uwaterloo", "mcmaster"), catalog.campusIds());
        CampusMap map = catalog.campus("st-george").orElseThrow();
        assertEquals("America/Toronto", map.timezone().getId());
        var date = java.time.LocalDate.of(2026, 11, 25);
        var daily = Expedition.daily(map, date, Instant.EPOCH);
        assertTrue(map.openSpace().isOpen(daily.start()));
        assertTrue(daily.shortestToZone() > 300);
        assertTrue(catalog.campus("nowhere").isEmpty());
    }

    @Test void everyDailyOnEveryCampusStartsOutdoorsInsideTheMap() throws Exception {
        var catalog = new PackCatalog();
        for (String id : catalog.campusIds()) {
            CampusMap map = catalog.campus(id).orElseThrow();
            int days = 0;
            for (var date = java.time.LocalDate.of(2026, 11, 1); date.isBefore(java.time.LocalDate.of(2027, 12, 6));
                 date = date.plusDays(1)) {
                var puzzle = map.dailyPuzzle(date).orElseThrow(() -> new AssertionError(id + " has a gap"));
                var start = map.openSpace().nearestOpen(puzzle.start(), 8);
                var day = date;
                assertTrue(start.isPresent(), () -> id + " " + day + ": the daily start isn't on open ground");
                assertTrue(map.landmark(puzzle.landmarkId()).isPresent(), () -> id + " " + day + ": unknown target");
                days++;
            }
            assertEquals(400, days, id);
        }
    }

    @Test void mcmasterPackSupportsDailyAndRandomRounds() throws Exception {
        CampusMap map = new PackCatalog().campus("mcmaster").orElseThrow();
        assertEquals("McMaster University — Main campus", map.name());
        assertEquals("America/Toronto", map.timezone().getId());
        assertEquals(53, map.landmarks().size());
        assertTrue(map.landmarks().stream().anyMatch(l -> l.name().equals("Mills Memorial Library")));
        assertTrue(map.landmarks().stream().anyMatch(l -> l.category().equals("Residence")));
        var date = java.time.LocalDate.of(2026, 11, 25);
        var daily = Expedition.daily(map, date, Instant.EPOCH);
        assertTrue(map.openSpace().isOpen(daily.start()), "daily start is outdoors");
        assertTrue(daily.shortestToZone() > 300);
        var puzzle = map.dailyPuzzle(date).orElseThrow();
        var destination = map.openSpace().nearestOpen(puzzle.zoneCentre(), 90).orElseThrow();
        daily.travelTo(destination, Instant.EPOCH.plusSeconds(1));
        assertTrue(daily.check(Instant.EPOCH.plusSeconds(2)).inZone());
        String target = map.landmark(puzzle.landmarkId()).orElseThrow().name();
        assertFalse(daily.chooseHint(Expedition.HintLevel.EASY).contains(target));
        for (int seed = 0; seed < 10; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), Instant.EPOCH);
            assertTrue(map.openSpace().isOpen(round.start()), "random start is outdoors");
            assertTrue(round.shortestToZone() > 300);
        }
    }

    @Test void waterlooPackSupportsDailyAndRandomRounds() throws Exception {
        CampusMap map = new PackCatalog().campus("uwaterloo").orElseThrow();
        assertEquals("University of Waterloo — Main campus", map.name());
        assertEquals("America/Toronto", map.timezone().getId());
        assertEquals(53, map.landmarks().size());
        assertTrue(map.landmarks().stream().anyMatch(l -> l.name().equals("Dana Porter Library")));
        var daily = Expedition.daily(map, java.time.LocalDate.of(2026, 11, 25), Instant.EPOCH);
        assertTrue(map.openSpace().isOpen(daily.start()), "daily start is outdoors");
        assertTrue(daily.shortestToZone() > 300);
        var puzzle = map.dailyPuzzle(java.time.LocalDate.of(2026, 11, 25)).orElseThrow();
        assertEquals("Biology 2", map.landmark(puzzle.landmarkId()).orElseThrow().name());
        var zone = new campus.domain.explore.Zone(puzzle.zoneCentre(), 90);
        var destination = map.openSpace().nearestOpen(puzzle.zoneCentre(), 90).orElseThrow();
        daily.travelTo(destination, Instant.EPOCH.plusSeconds(1));
        assertTrue(daily.check(Instant.EPOCH.plusSeconds(2)).inZone());
        String hint = daily.chooseHint(Expedition.HintLevel.NORMAL);
        assertTrue(hint.contains("#2 of 4"), "fallback identifies one named building by its position");
        assertFalse(hint.contains(map.landmark(puzzle.landmarkId()).orElseThrow().name()));
        assertTrue(map.buildingsIn(zone).size() >= 5);
        for (int seed = 0; seed < 10; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), Instant.EPOCH);
            assertTrue(map.openSpace().isOpen(round.start()), "random start is outdoors");
            assertTrue(round.shortestToZone() > 300);
        }
    }

    @Test void dailyAttemptsAreRecordedOncePerCampusAndDate(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        var attempts = new FileDailyAttempts(dir);
        var date = java.time.LocalDate.of(2026, 11, 25);
        assertTrue(attempts.claim("st-george", date));
        assertFalse(attempts.claim("st-george", date), "second try the same day");
        assertTrue(new FileDailyAttempts(dir).claim("st-george", date.plusDays(1)), "tomorrow is a new puzzle");
        assertFalse(new FileDailyAttempts(dir).claim("st-george", date), "remembered across restarts");
        assertThrows(java.io.IOException.class, () -> attempts.claim("../escape", date));
    }

    @Test void dailyRunsSurviveARoundTripAndCorruptFilesAreIgnored(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws Exception {
        var store = new FileDailyRunStore(dir);
        var date = java.time.LocalDate.of(2026, 11, 25);
        var map = campus.domain.explore.TestCampus.withDaily();
        var round = Expedition.daily(map, campus.domain.explore.TestCampus.DAILY_DATE, Instant.EPOCH);
        round.check(Instant.EPOCH.plusSeconds(5));
        store.save("test", date, round.snapshot());
        var restored = Expedition.restore(map, store.load("test", date).orElseThrow());
        assertEquals(round.id(), restored.id());
        assertEquals(round.position(), restored.position());
        assertEquals(round.checks(), restored.checks());
        assertEquals(round.shortestToZone(), restored.shortestToZone(), 1e-9);
        assertTrue(store.load("test", date.plusDays(1)).isEmpty());
        java.nio.file.Files.writeString(dir.resolve("test-" + date + ".run.json"), "{not json");
        assertTrue(store.load("test", date).isEmpty(), "a corrupt file reads as nothing saved");
        assertThrows(java.io.IOException.class, () -> store.load("../x", date));
    }

    @Test void rejectsUnsupportedPacks() {
        assertThrows(IllegalArgumentException.class, () -> CampusPackLoader.parse("{\"version\":1}"));
    }
}
