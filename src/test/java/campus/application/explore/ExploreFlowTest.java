package campus.application.explore;

import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExplorePresenter;
import campus.adapters.explore.ExploreViewModel;
import campus.domain.explore.CampusMap;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.TestCampus;
import campus.domain.explore.WalkGraph;
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

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-25T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @BeforeEach void wire() {
        var session = new ExpeditionSession();
        var presenter = new ExplorePresenter();
        LandmarkInfoGateway info = title -> {
            summaries.add(title);
            return Optional.of(new ExploreResponse.LandmarkSummary(title, "A test building.", null, null));
        };
        controller = new ExploreController(
                new StartExpeditionInteractor(map, new Random(3), clock, session, presenter),
                new MoveInteractor(clock, session, presenter),
                new CheckTemperatureInteractor(session, presenter),
                new ChooseHintInteractor(session, presenter),
                new GuessBuildingInteractor(session, info, presenter), presenter);
    }

    private ExploreViewModel call(String action, Map<String, Object> fields) {
        return controller.handle(action, fields);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    /** Walks node to node along the shortest route to the target area, 7 s per 50 m edge. */
    private ExploreViewModel walkToZone(int startNode) {
        WalkGraph walkways = map.walkways();
        GeoPoint target = map.building(1).orElseThrow().centre();
        var route = walkways.shortestRoute(startNode, node -> walkways.node(node).distanceTo(target) < 30)
                .orElseThrow().nodes();
        ExploreViewModel last = null;
        for (int i = 1; i < route.size(); i++) {
            clock.now = clock.now.plusSeconds(7);
            last = call("move", Map.of("from", route.get(i), "to", route.get(i - 1), "fraction", 0));
            assertEquals(200, last.status(), () -> "move rejected");
            if (Boolean.TRUE.equals(last.body().get("zoneReached"))) {
                return last;
            }
        }
        return last;
    }

    @Test void fullRoundFromStartToRevealWithoutLeakingTheTarget() {
        ExploreViewModel started = call("start", Map.of());
        assertEquals(200, started.status());
        String startJson = Json.write(started.body());
        assertFalse(startJson.contains("Target Hall"), "target hidden at start");
        assertFalse(startJson.contains("zone\""), "zone hidden at start");
        int startNode = ((Number) map(started.body().get("position")).get("from")).intValue();

        ExploreViewModel checked = call("check", Map.of());
        assertEquals("checked", checked.body().get("type"));
        assertEquals(5, checked.body().get("checksRemaining"));
        assertFalse(Json.write(checked.body()).contains("metres"), "exact distance hidden");

        assertEquals(400, call("guess", Map.of("buildingId", 1)).status(), "can't guess before the zone");

        ExploreViewModel reached = walkToZone(startNode);
        assertEquals(true, reached.body().get("zoneReached"));
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
        assertEquals(400, score.get("identify"), "second guess (200) x hard (2)");
        assertEquals(500, score.get("route"), "walked the shortest route");
        assertFalse(((List<?>) result.get("shortestRoute")).isEmpty());
        assertEquals(List.of("Target Hall"), summaries, "Wikipedia looked up once, at the end");
        assertEquals("A test building.", map(result.get("summary")).get("extract"));
    }

    @Test void badInputIsRejectedWithoutChangingTheRound() {
        assertEquals(400, call("check", Map.of()).status(), "no round yet");
        call("start", Map.of());
        ExploreViewModel teleport = call("move", Map.of("from", 0, "to", 1, "fraction", 0.5));
        assertEquals(409, teleport.status());
        assertEquals(0L, teleport.body().get("metresWalked"));
        assertEquals(400, call("move", Map.of("from", "x")).status());
        assertEquals(400, call("move", Map.of("from", 1.5, "to", 2, "fraction", 0)).status());
        assertEquals(400, call("hint", Map.of("level", "impossible")).status());
        assertEquals(404, call("cheat", Map.of()).status());
    }
}
