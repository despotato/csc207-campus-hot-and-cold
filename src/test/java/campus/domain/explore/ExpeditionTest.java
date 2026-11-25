package campus.domain.explore;

import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.Expedition.HintLevel;
import campus.domain.explore.WalkGraph.Position;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ExpeditionTest {
    /** A 25 x 3 grid of path nodes 50 m apart running east, so node (col, row) = row * 25 + col. */
    static final int COLS = 25;
    static final GeoPoint ORIGIN = new GeoPoint(43.66, -79.40);
    static final Instant T0 = Instant.parse("2026-11-25T12:00:00Z");

    static GeoPoint grid(double col, double row) {
        return ORIGIN.offset(90, col * 50).offset(0, row * 50);
    }

    static WalkGraph gridGraph() {
        List<GeoPoint> nodes = new ArrayList<>();
        List<int[]> edges = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < COLS; col++) {
                nodes.add(grid(col, row));
                int id = row * COLS + col;
                if (col > 0) edges.add(new int[]{id - 1, id});
                if (row > 0) edges.add(new int[]{id - COLS, id});
            }
        }
        nodes.add(grid(0, 10)); // an isolated node that is not on the main network
        return new WalkGraph(nodes, edges);
    }

    /** Target building sits between rows 1 and 2 near column 20; five buildings nearby. */
    static CampusMap campus() {
        List<Building> buildings = List.of(
                new Building(1, "Target Hall", grid(20, 1.5)),
                new Building(2, "North Hall", grid(20.5, 2.4)),
                new Building(3, "East Hall", grid(21.2, 1.5)),
                new Building(4, "West Hall", grid(19, 1.5)),
                new Building(5, "Far Hall", grid(2, 1.5)),
                new Building(6, null, grid(20, 0.6)));
        List<CampusLandmark> landmarks = List.of(
                new CampusLandmark(1, 1, "Target Hall", "Library", "Target Hall"),
                new CampusLandmark(2, 5, "Far Hall", "Residence", null));
        return new CampusMap("Test campus", buildings, landmarks, gridGraph());
    }

    static Expedition round() {
        CampusMap map = campus();
        return new Expedition(map, map.landmarks().getFirst(), new Zone(grid(20, 1.5), 90), COLS, T0);
    }

    /** Walks east along row 1 one edge at a time, 6 seconds per 50 m edge. */
    static Instant walkEast(Expedition round, int fromCol, int toCol, Instant time) {
        for (int col = fromCol; col < toCol && round.stage() == Expedition.Stage.FIND_ZONE; col++) {
            time = time.plusSeconds(6);
            round.moveTo(new Position(COLS + col + 1, COLS + col, 0), time);
        }
        return time;
    }

    @Test void shortestRouteIsComputedToTheZoneEdge() {
        Expedition round = round();
        // Zone centre at column 20 with radius 90 m: the first node inside is column 19 (50 m away).
        assertEquals(19 * 50, round.shortestToZone(), 1);
        assertTrue(round.revealedShortestRoute().isEmpty(), "Route stays hidden during play");
    }

    @Test void walkingCountsEveryMetreIncludingBacktracking() {
        Expedition round = round();
        Instant time = walkEast(round, 0, 4, T0);
        time = time.plusSeconds(6);
        round.moveTo(new Position(COLS + 3, COLS + 4, 0), time); // back one edge
        assertEquals(250, round.metresWalked(), 1);
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
    }

    @Test void teleportsAndOffPathMovesAreRejected() {
        Expedition round = round();
        assertThrows(IllegalArgumentException.class,
                () -> round.moveTo(new Position(COLS + 10, COLS + 11, 0), T0.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> round.moveTo(new Position(COLS, COLS + 12, 0), T0.plusSeconds(1)));
        // 50 m in one second is faster than the speed limit.
        assertThrows(IllegalArgumentException.class,
                () -> round.moveTo(new Position(COLS + 1, COLS, 0), T0.plusSeconds(1)));
        assertEquals(0, round.metresWalked());
    }

    @Test void checksGiveHeatAndTrendAndAreLimited() {
        Expedition round = round();
        Expedition.Check first = round.check();
        assertEquals(Expedition.Heat.COLD, first.heat());
        assertEquals(Expedition.Trend.FIRST, first.trend());
        Instant time = walkEast(round, 0, 8, T0);
        Expedition.Check second = round.check();
        assertEquals(Expedition.Trend.WARMER, second.trend());
        assertEquals(Expedition.Heat.COLD, second.heat()); // about 600 m away
        walkEast(round, 8, 14, time);
        assertEquals(Expedition.Heat.COOL, round.check().heat()); // about 301 m away
        while (round.checksRemaining() > 0) {
            round.check();
        }
        assertThrows(IllegalStateException.class, round::check);
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage(), "Out of checks: keep exploring without feedback");
    }

    @Test void enteringTheZoneStartsStageTwoAndRevealsTheZoneOnly() {
        Expedition round = round();
        assertTrue(round.revealedZone().isEmpty());
        walkEast(round, 0, 19, T0);
        assertEquals(Expedition.Stage.IDENTIFY, round.stage());
        assertTrue(round.revealedZone().isPresent());
        assertTrue(round.revealedTarget().isEmpty());
        assertEquals(T0.plusSeconds(19 * 6), round.zoneReachedAt().orElseThrow());
        assertThrows(IllegalStateException.class,
                () -> round.moveTo(new Position(COLS + 20, COLS + 19, 0), T0.plusSeconds(200)));
        assertThrows(IllegalStateException.class, round::check);
    }

    @Test void hintMustBeChosenBlindBeforeGuessingAndOnlyOnce() {
        Expedition round = round();
        walkEast(round, 0, 19, T0);
        assertThrows(IllegalStateException.class, () -> round.guess(1));
        assertEquals("It's a library building, and its name starts with \"T\".", round.chooseHint(HintLevel.EASY));
        assertThrows(IllegalStateException.class, () -> round.chooseHint(HintLevel.HARD));
    }

    @Test void hardHintGivesDirectionWithinZone() {
        CampusMap map = campus();
        Expedition round = new Expedition(map, map.landmarks().getFirst(),
                new Zone(grid(20, 1.5).offset(270, 40), 90), COLS, T0);
        walkEast(round, 0, 19, T0);
        assertEquals("It's in the east part of the zone.", round.chooseHint(HintLevel.HARD));
    }

    @Test void guessesMustBeInsideTheZoneAndDistinct() {
        Expedition round = round();
        walkEast(round, 0, 19, T0);
        round.chooseHint(HintLevel.NORMAL);
        assertThrows(IllegalArgumentException.class, () -> round.guess(5), "Far Hall is outside the zone");
        assertThrows(IllegalArgumentException.class, () -> round.guess(99));
        assertFalse(round.guess(4));
        assertThrows(IllegalArgumentException.class, () -> round.guess(4));
        assertTrue(round.guess(1));
        assertEquals(Expedition.Stage.FINISHED, round.stage());
        assertEquals("Target Hall", round.revealedTarget().orElseThrow().name());
        assertTrue(round.revealedShortestRoute().isPresent());
    }

    @Test void scoreRewardsEfficiencyHarderHintsAndFewerGuesses() {
        Expedition perfect = round();
        walkEast(perfect, 0, 19, T0);
        perfect.chooseHint(HintLevel.HARD);
        perfect.guess(1);
        Expedition.Score best = perfect.score();
        assertEquals(500, best.routePoints());
        assertEquals(600, best.identifyPoints());
        assertEquals(1100, best.total());

        Expedition detour = round();
        Instant time = walkEast(detour, 0, 4, T0);
        for (int i = 0; i < 4; i++) { // back, forward, back, forward along one edge: +200 m
            time = time.plusSeconds(6);
            int node = i % 2 == 0 ? COLS + 3 : COLS + 4;
            detour.moveTo(new Position(node, i % 2 == 0 ? COLS + 4 : COLS + 3, 0), time);
        }
        walkEast(detour, 4, 19, time);
        detour.chooseHint(HintLevel.EASY);
        detour.guess(4);
        detour.guess(1);
        Expedition.Score worse = detour.score();
        assertEquals(Math.round(500 * 950 / 1150.0), worse.routePoints());
        assertEquals(200, worse.identifyPoints());
    }

    @Test void threeWrongGuessesEndTheRoundWithNoIdentifyPoints() {
        Expedition round = round();
        walkEast(round, 0, 19, T0);
        round.chooseHint(HintLevel.NORMAL);
        assertFalse(round.guess(2));
        assertFalse(round.guess(3));
        assertFalse(round.guess(6));
        assertEquals(Expedition.Stage.FINISHED, round.stage());
        assertFalse(round.isWon());
        assertEquals(0, round.score().identifyPoints());
        assertEquals(500, round.score().routePoints());
    }

    @Test void randomRoundsAreValid() {
        CampusMap map = campus();
        for (int seed = 0; seed < 20; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), T0);
            GeoPoint start = map.walkways().pointAt(round.start());
            assertTrue(map.walkways().isMainNetwork(round.start().from()));
            assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
            assertTrue(round.shortestToZone() > 0);
            assertTrue(start.distanceTo(map.buildingOf(map.landmarks().getFirst()).centre()) > 300
                    || start.distanceTo(map.buildingOf(map.landmarks().get(1)).centre()) > 300);
        }
    }
}
