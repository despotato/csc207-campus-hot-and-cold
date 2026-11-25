package campus.application.explore;

import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExplorePresenter;
import campus.adapters.explore.ExploreViewModel;
import campus.domain.explore.CampusMap;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.TestCampus;
import campus.infrastructure.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Drives the explore use cases through the controller exactly as the browser does. */
class ExploreFlowTest {
    private final CampusMap map = TestCampus.map();
    private final MutableClock clock = new MutableClock();
    private final List<String> summaries = new java.util.ArrayList<>();
    private ExploreController controller;
    private final List<String> saved = new java.util.ArrayList<>();
    private boolean failSave;

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private ExpeditionSession session;

    @BeforeEach void wire() {
        session = new ExpeditionSession();
        var presenter = new ExplorePresenter();
        LandmarkInfoGateway info = title -> {
            summaries.add(title);
            return Optional.of(new ExploreResponse.LandmarkSummary(title, "A test building.", null, null));
        };
        controller = new ExploreController(
                new StartExpeditionInteractor(map, new Random(3), clock, session, presenter),
                new MoveInteractor(clock, session, presenter), new TravelInteractor(clock, session, presenter),
                new CheckTemperatureInteractor(clock, session, finisher(info), presenter),
                new ChooseHintInteractor(session, presenter),
                new GuessBuildingInteractor(session, finisher(info), presenter), presenter);
    }

    private RoundFinisher finisher(LandmarkInfoGateway info) {
        return new RoundFinisher(info, new ExpeditionResultRepository() {
            @Override
            public void save(java.util.UUID id, String campus, Instant at, String difficulty,
                             ExploreResponse.Result result) throws java.io.IOException {
                if (failSave) {
                    throw new java.io.IOException("disk full");
                }
                saved.add(campus + "|" + difficulty + "|" + result.targetName() + "|" + result.score());
            }
        }, clock);
    }

    private String roundId;

    /** Sends an action as the browser does, tagged with the current round's ID. */
    private ExploreViewModel call(String action, Map<String, Object> fields) {
        Map<String, Object> request = new java.util.HashMap<>(fields);
        if (roundId != null && !request.containsKey("round")) {
            request.put("round", roundId);
        }
        ExploreViewModel model = controller.handle(action, request);
        if ("started".equals(model.body().get("type"))) {
            roundId = (String) model.body().get("roundId");
        }
        return model;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** Walks the shortest route into the zone in one-second chunks of up to 10 m, as the browser would. */
    private ExploreViewModel walkToZone() {
        var round = session.current().orElseThrow();
        var zone = round.zoneForDiagnostics();
        List<GeoPoint> route = map.openSpace().shortestRoute(round.position(), zone::contains).orElseThrow().points();
        ExploreViewModel last = null;
        GeoPoint here = route.getFirst();
        for (GeoPoint target : route.subList(1, route.size())) {
            int steps = (int) Math.ceil(here.distanceTo(target) / 10);
            for (int i = 1; i <= steps; i++) {
                GeoPoint next = here.towards(target, (double) i / steps);
                clock.now = clock.now.plusSeconds(1);
                last = call("move", Map.of("path", List.of(List.of(next.longitude(), next.latitude()))));
                assertEquals(200, last.status(), () -> "move rejected");
            }
            here = target;
        }
        assertNull(last.body().get("zone"), "walking in doesn't reveal the zone");
        clock.now = clock.now.plusSeconds(1);
        return call("check", Map.of());
    }

    @Test void fullRoundFromStartToRevealWithoutLeakingTheTarget() {
        ExploreViewModel started = call("start", Map.of());
        assertEquals(200, started.status());
        String startJson = Json.write(started.body());
        assertFalse(startJson.contains("Target Hall"), "target hidden at start");
        assertFalse(startJson.contains("zone\""), "zone hidden at start");

        ExploreViewModel checked = call("check", Map.of());
        assertEquals("checked", checked.body().get("type"));
        assertEquals(5, checked.body().get("checksRemaining"));
        assertFalse(Json.write(checked.body()).contains("metres"), "exact distance hidden");

        assertEquals(400, call("guess", Map.of("buildingId", 1)).status(), "can't guess before the zone");

        ExploreViewModel reached = walkToZone();
        assertEquals(true, reached.body().get("zoneFound"));
        assertNotNull(reached.body().get("zone"));
        assertFalse(Json.write(reached.body()).contains("Target Hall"));

        ExploreViewModel hint = call("hint", Map.of("level", "hard"));
        assertEquals(200, hint.status());
        assertEquals(2.0, hint.body().get("multiplier"));
        assertTrue(((List<?>) hint.body().get("zoneBuildings")).contains(1));

        ExploreViewModel wrong = call("guess", Map.of("buildingId", 4));
        assertEquals(false, wrong.body().get("correct"));
        assertNull(wrong.body().get("result"));

        ExploreViewModel right = call("guess", Map.of("buildingId", 1));
        assertEquals(true, right.body().get("correct"));
        Map<String, Object> result = map(right.body().get("result"));
        assertEquals(true, result.get("won"));
        assertEquals("Target Hall", map(result.get("target")).get("name"));
        Map<String, Object> score = map(result.get("score"));
        assertEquals(150, score.get("guess"), "second guess");
        assertEquals(150, score.get("hintBonus"), "hard doubles it");
        assertEquals(1000, score.get("max"));
        assertTrue(((Number) score.get("route")).intValue() >= 390, "walked the shortest route");
        assertFalse(((List<?>) result.get("shortestRoute")).isEmpty());
        assertEquals(List.of("Target Hall"), summaries, "Wikipedia looked up once, at the end");
        assertEquals(1, saved.size(), "saved exactly once when finished");
        assertTrue(saved.getFirst().startsWith("Test campus|normal|Target Hall|"));
        assertNull(right.body().get("saveError"));
        assertEquals("A test building.", map(result.get("summary")).get("extract"));
    }

    @Test void failedSaveIsReportedWithoutLosingTheResult() {
        failSave = true;
        call("start", Map.of("difficulty", "normal"));
        walkToZone();
        call("hint", Map.of("level", "easy"));
        ExploreViewModel right = call("guess", Map.of("buildingId", 1));
        assertNotNull(right.body().get("result"));
        assertEquals("Your result could not be saved.", right.body().get("saveError"));
    }

    @Test void fastTravelMovesThePlayerAndChargesTheWalk() {
        call("start", Map.of());
        GeoPoint here = session.current().orElseThrow().position();
        GeoPoint there = map.openSpace().openCells(3).stream()
                .filter(p -> p.distanceTo(here) > 150 && p.distanceTo(here) < 250
                        && !session.current().orElseThrow().zoneForDiagnostics().contains(p))
                .findFirst().orElseThrow();
        ExploreViewModel travelled = call("travel", Map.of("to", List.of(there.longitude(), there.latitude())));
        assertEquals(200, travelled.status());
        assertEquals("travelled", travelled.body().get("type"));
        assertTrue(((Number) travelled.body().get("metresWalked")).doubleValue() >= 150);
        assertFalse(((List<?>) travelled.body().get("path")).isEmpty());
        assertEquals(400, call("travel", Map.of("to", "nowhere")).status());
    }

    @Test void aStaleTabCannotActOnANewerRound() {
        call("start", Map.of());
        String first = roundId;
        call("start", Map.of());
        ExploreViewModel stale = call("check", Map.of("round", first));
        assertEquals(400, stale.status());
        assertEquals(ExpeditionSession.STALE, stale.body().get("error"));
        assertEquals(200, call("check", Map.of()).status(), "the current round still works");
        assertEquals(400, controller.handle("check", Map.of()).status(), "a round ID is required");
    }

    @Test void onlyKnownDifficultiesCanStartARound() {
        assertEquals(400, call("start", Map.of("difficulty", "nightmare")).status());
        assertEquals(200, call("start", Map.of("difficulty", "NORMAL")).status());
    }

    @Test void badInputIsRejectedWithoutChangingTheRound() {
        assertEquals(400, call("check", Map.of("round", java.util.UUID.randomUUID().toString())).status(),
                "no round yet");
        call("start", Map.of());
        GeoPoint far = session.current().orElseThrow().position().offset(90, 400);
        ExploreViewModel teleport = call("move", Map.of("path", List.of(List.of(far.longitude(), far.latitude()))));
        assertEquals(409, teleport.status());
        assertEquals(0L, teleport.body().get("metresWalked"));
        assertNotNull(teleport.body().get("point"), "the page is told where the player really is");
        assertEquals(400, call("move", Map.of("path", "x")).status());
        assertEquals(400, call("move", Map.of("path", List.of(List.of(1)))).status());
        assertEquals(400, call("move", Map.of("path", List.of())).status());
        assertEquals(400, call("hint", Map.of("level", "impossible")).status());
        assertEquals(404, call("cheat", Map.of()).status());
    }
}
