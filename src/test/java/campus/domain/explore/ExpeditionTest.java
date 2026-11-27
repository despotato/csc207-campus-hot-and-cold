package campus.domain.explore;

import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.Expedition.HintLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpeditionTest {
    static final GeoPoint ORIGIN = new GeoPoint(43.66, -79.40);
    static final Instant T0 = Instant.parse("2026-11-25T12:00:00Z");
    static final GeoPoint START = at(0, 75);

    /** A point {@code east} and {@code north} metres from the origin. */
    static GeoPoint at(double east, double north) {
        return ORIGIN.offset(90, east).offset(0, north);
    }

    static List<List<GeoPoint>> square(double east, double north, double half) {
        return List.of(List.of(at(east - half, north - half), at(east + half, north - half),
                at(east + half, north + half), at(east - half, north + half), at(east - half, north - half)));
    }

    /**
     * A 1,300 m x 220 m open campus. A thin wall at 500 m east runs from the south edge up to 120 m north,
     * so walking east means going around its north end. Target Hall stands at (1000, 75).
     */
    static CampusMap campus() {
        return campus(Set.of());
    }

    /** The same campus, with stored clue photos of {@code cluePhotos}. */
    static CampusMap campus(Set<Integer> cluePhotos) {
        List<List<List<GeoPoint>>> solid = new ArrayList<>(List.of(
                square(1000, 75, 10), square(1010, 130, 10), square(1060, 75, 10), square(950, 75, 10),
                square(100, 140, 10), square(1000, 20, 10)));
        solid.add(List.of(List.of(at(498, -20), at(502, -20), at(502, 120), at(498, 120), at(498, -20))));
        OpenSpace ground = new OpenSpace(at(-20, -20), at(1300, 200), 1.5, solid);
        List<Building> buildings = List.of(
                new Building(1, "Target Hall", at(1000, 75), 22), new Building(2, "North Hall", at(1010, 130), 11),
                new Building(3, "East Hall", at(1060, 75), 11), new Building(4, "West Hall", at(950, 75), 11),
                new Building(5, "Far Hall", at(100, 140), 11), new Building(6, null, at(1000, 20), 5));
        List<CampusLandmark> landmarks = List.of(
                new CampusLandmark(1, 1, "Target Hall", "Library", "Target Hall"),
                new CampusLandmark(2, 5, "Far Hall", "Residence", null));
        return new CampusMap("campus", "Test campus", java.time.ZoneOffset.UTC, java.util.Map.of(), buildings, landmarks,
                ground, cluePhotos);
    }

    static Expedition round() {
        CampusMap map = campus();
        return new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
    }

    /** Around the wall's north end and into the zone. */
    static final List<GeoPoint> ROUTE = List.of(at(490, 126), at(510, 126), at(912, 78));

    /** Walks to each waypoint in 10 m steps, one second per step (10 m/s, under the speed limit). */
    static Instant walk(Expedition round, List<GeoPoint> waypoints, Instant time) {
        GeoPoint here = round.position();
        for (GeoPoint target : waypoints) {
            int steps = (int) Math.ceil(here.distanceTo(target) / 10);
            for (int i = 1; i <= steps && round.stage() == Expedition.Stage.FIND_ZONE; i++) {
                time = time.plusSeconds(1);
                round.moveAlong(List.of(here.towards(target, (double) i / steps)), time);
            }
            here = target;
        }
        return time;
    }

    /** Walks the route into the zone and checks there, which is how the zone is found. */
    static Instant reachZone(Expedition round, Instant time) {
        time = walk(round, ROUTE, time).plusSeconds(1);
        assertTrue(round.check(time).inZone());
        return time;
    }

    static Instant reachZone(Expedition round) {
        return reachZone(round, T0);
    }

    @Test void openSpaceBlocksBuildingsButNotCourtyards() {
        List<List<GeoPoint>> ring = new ArrayList<>(square(50, 50, 30));
        ring.addAll(square(50, 50, 10)); // courtyard
        OpenSpace ground = new OpenSpace(at(0, 0), at(100, 100), 1.0, List.of(ring));
        assertTrue(ground.isOpen(at(5, 5)));
        assertFalse(ground.isOpen(at(30, 50)), "inside the building");
        assertTrue(ground.isOpen(at(50, 50)), "inside the open courtyard");
        assertFalse(ground.isOpen(at(150, 50)), "outside the campus bounds");
        assertFalse(ground.clearPath(at(5, 50), at(95, 50)));
        assertTrue(ground.clearPath(at(5, 5), at(95, 5)));
    }

    @Test void shortestRouteGoesAroundBuildings() {
        Expedition round = round();
        // Straight to within reach of the zone edge is 898 m; going around the wall's end adds a little.
        assertTrue(round.shortestToZone() > 897 && round.shortestToZone() < 923, () -> "" + round.shortestToZone());
        assertTrue(round.revealedShortestRoute().isEmpty(), "Route stays hidden during play");
    }

    @Test void aCheckWithinReachOfTheZoneEdgeFindsIt() {
        Zone zone = new Zone(at(1000, 75), 90);
        GeoPoint edge = at(896, 75);   // 14 m outside the edge, inside the 15 m reach
        assertFalse(zone.contains(edge), "the player's centre is still outside");
        assertTrue(zone.overlaps(edge, Expedition.PLAYER_RADIUS), "the check reaches the zone");
        assertFalse(zone.overlaps(at(893, 75), Expedition.PLAYER_RADIUS), "beyond the reach is still outside");

        Expedition round = round();
        Instant time = walk(round, List.of(at(490, 126), at(510, 126), edge), T0);
        assertTrue(round.check(time.plusSeconds(1)).inZone());
    }

    @Test void walkingCountsEveryMetreIncludingBacktracking() {
        Expedition round = round();
        Instant time = walk(round, List.of(at(100, 75)), T0);
        walk(round, List.of(at(60, 75)), time);
        assertEquals(140, round.metresWalked(), 0.5);
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
    }

    @Test void teleportsAndWalkingThroughBuildingsAreRejected() {
        Expedition round = round();
        assertThrows(IllegalArgumentException.class,
                () -> round.moveAlong(List.of(at(300, 75)), T0.plusSeconds(1)), "300 m in a second");
        assertThrows(IllegalArgumentException.class,
                () -> round.moveAlong(List.of(at(110, 75)), T0.plusSeconds(1)), "faster than the 47 m/s walk allows");
        Instant time = walk(round, List.of(at(490, 75)), T0);
        assertThrows(IllegalArgumentException.class,
                () -> round.moveAlong(List.of(at(506, 75)), time.plusSeconds(1)), "through the wall");
        assertThrows(IllegalArgumentException.class, () -> round.moveAlong(List.of(), time.plusSeconds(1)));
        assertEquals(490, round.metresWalked(), 0.5);
    }

    @Test void aMoveMaySendSeveralPointsWalkedSinceTheLastUpdate() {
        Expedition round = round();
        round.moveAlong(List.of(at(5, 75), at(5, 80), at(10, 80)), T0.plusSeconds(1));
        assertEquals(15, round.metresWalked(), 0.5);
        assertEquals(10, round.position().distanceTo(at(0, 80)), 0.5);
    }

    @Test void fastTravelIsChargedAsTheShortestWalk() {
        Expedition round = round();
        round.travelTo(at(700, 75), T0.plusSeconds(1));
        // Around the wall's north end: longer than the 700 m straight line.
        assertTrue(round.metresWalked() > 705 && round.metresWalked() < 730, () -> "" + round.metresWalked());
        assertEquals(0, round.position().distanceTo(at(700, 75)), 0.01);
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
        assertThrows(IllegalArgumentException.class, () -> round.travelTo(at(1000, 20), T0.plusSeconds(2)),
                "can't travel into a building");
        assertEquals(1 + Math.round(round.metresWalked() / 47), round.secondsElapsed(T0.plusSeconds(1)),
                "the trip costs the time walking it would take");
    }

    @Test void fastTravelIntoTheZoneIsAlsoSilent() {
        Expedition round = round();
        round.travelTo(at(930, 78), T0.plusSeconds(1));
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
        assertTrue(round.check(T0.plusSeconds(2)).inZone());
    }

    @Test void checksGiveHeatAndTrendAndAreLimited() {
        Expedition round = round();
        Expedition.Check first = round.check(T0);
        assertEquals(Expedition.Heat.COLD, first.heat());
        assertEquals(Expedition.Trend.FIRST, first.trend());
        Instant time = walk(round, List.of(at(400, 75)), T0);
        Expedition.Check second = round.check(time);
        assertEquals(Expedition.Trend.WARMER, second.trend());
        assertEquals(Expedition.Heat.COLD, second.heat()); // 600 m away
        Instant later = walk(round, List.of(at(490, 126), at(510, 126), at(720, 75)), time);
        Expedition.Check warm = round.check(later);
        assertEquals(Expedition.Heat.WARM, warm.heat()); // 280 m away
        assertEquals(26, warm.temperature(), "40°C at the zone, a degree cooler per 20 m");
        assertEquals(10, second.temperature(), "600 m away");
        assertEquals(-10, first.temperature(), "about 1 km away: floored at -10°C");
        while (round.checksRemaining() > 0) {
            round.check(later);
        }
        assertThrows(IllegalStateException.class, () -> round.check(later));
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage(), "Out of checks: keep exploring without feedback");
    }

    @Test void walkingIntoTheZoneIsSilentUntilACheckThere() {
        Expedition round = round();
        Instant time = walk(round, ROUTE, T0);
        assertEquals(Expedition.Stage.FIND_ZONE, round.stage(), "entering the zone isn't announced");
        assertTrue(round.revealedZone().isEmpty());
        Expedition.Check check = round.check(time);
        assertTrue(check.inZone());
        assertEquals(Expedition.Stage.IDENTIFY, round.stage());
        assertTrue(round.revealedZone().isPresent());
        assertTrue(round.revealedTarget().isEmpty());
        assertEquals(time, round.zoneReachedAt().orElseThrow());
        assertThrows(IllegalStateException.class, () -> round.moveAlong(List.of(at(915, 78)), time.plusSeconds(2)));
        assertThrows(IllegalStateException.class, () -> round.check(time));
    }

    @Test void finalSearchInsideTheZoneFindsIt() {
        Expedition round = round();
        while (round.checksRemaining() > 0) {
            round.check(T0);
        }
        Instant time = walk(round, ROUTE, T0);
        assertThrows(IllegalStateException.class, () -> round.check(time));
        assertTrue(round.finalSearch(time));
        assertEquals(Expedition.Stage.IDENTIFY, round.stage());
    }

    @Test void finalSearchOutsideTheZoneEndsTheRound() {
        Expedition round = round();
        assertThrows(IllegalStateException.class, () -> round.finalSearch(T0), "checks come first");
        while (round.checksRemaining() > 0) {
            round.check(T0);
        }
        Instant time = walk(round, List.of(at(300, 75)), T0);
        assertFalse(round.finalSearch(time));
        assertEquals(Expedition.Stage.FINISHED, round.stage());
        assertEquals("Target Hall", round.revealedTarget().orElseThrow().name());
        assertEquals(0, round.score().total(), "never found the zone");
        assertThrows(IllegalStateException.class, () -> round.chooseHint(HintLevel.EASY), "round is over");
    }

    @Test void hintMustBeChosenBlindBeforeGuessingAndOnlyOnce() {
        Expedition round = round();
        reachZone(round);
        assertThrows(IllegalStateException.class, () -> round.guess(1));
        assertEquals("It's the tallest of the 5 buildings in the zone: a 6-storey brick building. "
                        + "Its name starts with \"T\" and is 2 words long.",
                round.chooseHint(HintLevel.EASY), "easy: what anyone can see");
        assertThrows(IllegalStateException.class, () -> round.chooseHint(HintLevel.HARD));
    }

    @Test void normalAndHardHintsUseFactsAndFallBackToHeight() {
        List<String> facts = List.of("It opened in 1931 as a library.", "A film crew used its roof in 2009.");
        Expedition normal = round();
        reachZone(normal);
        assertEquals("It's a library building. It opened in 1931 as a library.",
                normal.chooseHint(HintLevel.NORMAL, target -> facts), "normal: category plus best-known fact, no location");
        Expedition hard = round();
        reachZone(hard);
        assertEquals("A film crew used its roof in 2009.", hard.chooseHint(HintLevel.HARD, target -> facts),
                "hard: an obscure fact only");
        Expedition none = round();
        reachZone(none);
        assertEquals("Little is recorded about its history. Its name is 2 words long and starts with \"T\".", none.chooseHint(HintLevel.HARD, target -> List.of()),
                "hard never falls back to a size clue");
        Expedition single = round();
        reachZone(single);
        assertEquals("It's a library building. Students go there to study and borrow books.",
                single.chooseHint(HintLevel.NORMAL, target -> List.of("A film crew used its roof in 2009.")),
                "a single fact is kept for hard, so normal and hard never repeat each other");
        Expedition student = round();
        reachZone(student);
        assertEquals("It's a library building. Students go there to study and borrow books.",
                student.chooseHint(HintLevel.NORMAL, target -> List.of()), "normal fallback: what it's used for");
    }

    @Test void normalFallsBackToTheServicesInsideAndNeverToASpellingClue() {
        CampusMap base = campus();
        List<CampusMap.Building> buildings = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(id -> base.building(id).orElseThrow()).toList();
        var landmarks = List.of(new CampusMap.CampusLandmark(1, 1, "Target Hall", "Teaching", null, List.of(),
                List.of("coffee", "the bookstore", "printing")));
        CampusMap map = new CampusMap("Test campus", buildings, landmarks, base.openSpace());
        Expedition round = new Expedition(map, landmarks.getFirst(), new Zone(at(1000, 75), 90), START, T0);
        reachZone(round);
        assertEquals("It's a teaching building. Students go there for coffee, the bookstore, and printing.",
                round.chooseHint(HintLevel.NORMAL, target -> List.of()));
        assertEquals("coffee and printing", Expedition.listed(List.of("coffee", "printing")));
        for (String category : List.of("Library", "Residence", "College", "Athletics", "Campus life", "Teaching")) {
            var plain = new CampusMap.CampusLandmark(1, 1, "Target Hall", category, null);
            CampusMap other = new CampusMap("Test campus", buildings, List.of(plain), base.openSpace());
            Expedition r = new Expedition(other, plain, new Zone(at(1000, 75), 90), START, T0);
            reachZone(r);
            String hint = r.chooseHint(HintLevel.NORMAL, target -> List.of());
            assertFalse(hint.contains("starts with") || hint.contains("words"), hint);
            assertTrue(hint.contains("Students"), hint);
        }
    }

    @Test void hardHintFallsBackToRecordedHistory() {
        CampusMap base = campus();
        List<CampusMap.Building> buildings = new java.util.ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            CampusMap.Building b = base.building(id).orElseThrow();
            buildings.add(id == 1 ? new CampusMap.Building(1, b.name(), b.centre(), 22, "stone", true, 1931,
                    "Sproatt and Rolph", "Old Library") : b);
        }
        CampusMap map = new CampusMap("Test campus", buildings, base.landmarks(), base.openSpace());
        Expedition round = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        reachZone(round);
        assertEquals("It used to be known as the Old Library.", round.chooseHint(HintLevel.HARD, target -> List.of()));
        Expedition easy = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        reachZone(easy);
        assertEquals("It's the tallest of the 5 buildings in the zone: a 6-storey stone building with a courtyard. "
                        + "Its name starts with \"T\" and is 2 words long.",
                easy.chooseHint(HintLevel.EASY));
    }

    @Test void aOneStoreyTargetIsDescribedInTheSingular() {
        CampusMap base = campus();
        List<CampusMap.Building> buildings = new java.util.ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            CampusMap.Building b = base.building(id).orElseThrow();
            buildings.add(id == 1 ? new CampusMap.Building(1, b.name(), b.centre(), 4) : b);
        }
        CampusMap map = new CampusMap("Test campus", buildings, base.landmarks(), base.openSpace());
        Expedition round = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        reachZone(round);
        assertTrue(round.chooseHint(HintLevel.EASY).contains("a 1-storey"), "easy describes what you can see");
    }

    @Test void randomRoundsOnlyTargetLandmarksWithAFactOrRecordedHistory() {
        CampusMap base = campus();
        var landmarks = List.of(new CampusMap.CampusLandmark(1, 1, "Target Hall", "Library", null,
                        List.of("It opened in 1931.")),
                new CampusMap.CampusLandmark(2, 5, "Far Hall", "Residence", null));
        List<CampusMap.Building> buildings = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(id -> base.building(id).orElseThrow()).toList();
        CampusMap map = new CampusMap("Test campus", buildings, landmarks, base.openSpace());
        assertEquals(List.of(landmarks.getFirst()), map.targets());
        assertEquals(base.landmarks(), base.targets(), "with no facts anywhere, every landmark can be a target");
    }

    @Test void factsThatGiveAwayTheNameAreIgnored() {
        Expedition leaky = round();
        reachZone(leaky);
        assertEquals("Little is recorded about its history. Its name is 2 words long and starts with \"T\".", leaky.chooseHint(HintLevel.HARD,
                target -> List.of("Target Hall opened in 1900.", "The target building of 1900.")),
                "the full name, or its distinctive word 'target', gives it away");
        Expedition easy = round();
        reachZone(easy);
        assertTrue(easy.chooseHint(HintLevel.EASY, target -> List.of("ignored")).startsWith("It's the tallest"),
                "easy never uses outside facts");
    }

    @Test void guessesMustBeInsideTheZoneAndDistinct() {
        Expedition round = round();
        reachZone(round);
        round.chooseHint(HintLevel.NORMAL);
        assertThrows(IllegalArgumentException.class, () -> round.guess(5), "Far Hall is outside the zone");
        assertThrows(IllegalArgumentException.class, () -> round.guess(99));
        assertFalse(round.guess(4));
        assertThrows(IllegalArgumentException.class, () -> round.guess(4));
        assertTrue(round.guess(1));
        assertEquals(Expedition.Stage.FINISHED, round.stage());
        assertEquals("Target Hall", round.revealedTarget().orElseThrow().name());
        assertTrue(round.revealedShortestRoute().orElseThrow().size() >= 2);
    }

    @Test void scoreIsOutOfOneThousandAndTalliesEachPart() {
        Expedition direct = round();
        reachZone(direct); // one check used, the one that found the zone
        direct.chooseHint(HintLevel.HARD);
        direct.guess(1);
        Expedition.Score best = direct.score();
        assertEquals(400, best.routePoints(), "near-shortest walk earns the full route points");
        assertEquals(250, best.guessPoints(), "first guess");
        assertEquals(250, best.hintBonus(), "hard doubles it");
        assertEquals(100, best.checkBonus(), "only the finding check used");
        assertEquals(Expedition.MAX_SCORE, best.total(), "a perfect round scores exactly 1000");
        assertEquals(List.of(Achievement.BETA_PLAYER, Achievement.ABSOLUTE_ZERO, Achievement.SUPERNOVA),
                direct.achievements());

        Expedition detour = round();
        Instant time = walk(detour, List.of(at(200, 75), at(0, 75)), T0); // 400 m wasted
        detour.check(time);
        detour.check(time);
        reachZone(detour, time);
        detour.chooseHint(HintLevel.NORMAL);
        detour.guess(4);
        detour.guess(1);
        Expedition.Score worse = detour.score();
        double efficiency = detour.shortestToZone() / detour.metresWalked();
        assertEquals(Math.round(400 * Math.min(1, efficiency / 0.8)), worse.routePoints());
        assertTrue(worse.routePoints() < 400, "a 400 m detour costs route points");
        assertEquals(200, worse.guessPoints(), "second guess");
        assertEquals(100, worse.hintBonus(), "normal adds half");
        assertEquals(60, worse.checkBonus(), "3 of 5 saveable checks saved");
        assertEquals(List.of(Achievement.BETA_PLAYER), detour.achievements(), "checks spent, short of 1000");
    }

    @Test void missingTheZoneNarrowlyEarnsNearMissPoints() {
        Expedition close = round();
        Instant time = walk(close, List.of(at(490, 126), at(510, 126), at(845, 75)), T0); // 50 m beyond a check's reach of the zone edge
        while (close.checksRemaining() > 0) {
            close.check(time);
        }
        assertFalse(close.finalSearch(time));
        assertEquals(50, close.metresShortOfZone().orElseThrow(), 1);
        Expedition.Score score = close.score();
        assertEquals(67, score.nearMissPoints(), "two thirds of the way inside the 150 m near-miss band");
        assertEquals(67, score.total(), "nothing else is earned without the zone");
        assertEquals(List.of(Achievement.BETA_PLAYER, Achievement.ALMOST_ALMOST), close.achievements());

        Expedition far = round();
        far.check(T0);
        assertEquals(0, far.score().nearMissPoints(), "checking 1 km away is no near miss");
        assertEquals(List.of(Achievement.BETA_PLAYER), far.achievements());

        Expedition found = round();
        reachZone(found);
        assertTrue(found.metresShortOfZone().isEmpty());
        assertEquals(0, found.score().nearMissPoints());
    }

    @Test void theClueIsAPhotographedOrdinaryBuildingInsideTheZone() {
        // In the zone: Target Hall (target, landmark), North/East/West Hall and an unnamed shed. Far Hall is outside.
        CampusMap map = campus(Set.of(1, 2, 3, 5));
        Expedition round = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        int clue = round.clueBuilding().orElseThrow();
        assertTrue(clue == 2 || clue == 3, "photographed, in the zone and not a landmark: " + clue);
        Expedition again = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        assertEquals(clue, again.clueBuilding().orElseThrow(), "the same zone always shows the same photo");
    }

    @Test void theWrittenClueIsAboutThePhotographedBuildingOrAnotherOrdinaryOne() {
        CampusMap map = campus(Set.of(2));
        Expedition photographed = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START,
                T0);
        assertEquals(2, photographed.clueBuilding().orElseThrow());
        assertEquals("One building in the zone is 3 storeys, brick.", photographed.clueText().orElseThrow(),
                "North Hall: 11 m, no recorded history");
        String words = round().clueText().orElseThrow();
        for (String name : List.of("Hall", "Target")) {
            assertFalse(words.contains(name), "never a name: " + words);
        }
        Expedition onlyLandmark = new Expedition(map, map.landmarks().get(1), new Zone(at(100, 140), 30), START, T0);
        assertTrue(onlyLandmark.clueText().isEmpty(), "Far Hall is a landmark");
    }

    /** Target Hall plus {@code others}, all landmarks except the unnamed ones; North Hall has {@code northFacts}. */
    private static CampusMap allLandmarks(Set<Integer> cluePhotos, List<String> northFacts, Building... others) {
        List<Building> buildings = new ArrayList<>(List.of(new Building(1, "Target Hall", at(1000, 75), 22)));
        buildings.addAll(List.of(others));
        List<CampusLandmark> landmarks = new ArrayList<>(List.of(
                new CampusLandmark(1, 1, "Target Hall", "Library", "Target Hall")));
        for (Building other : others) {
            if (other.name() != null) {
                landmarks.add(new CampusLandmark(other.id(), other.id(), other.name(), "Teaching", null,
                        other.name().equals("North Hall") ? northFacts : List.of()));
            }
        }
        OpenSpace ground = new OpenSpace(at(-20, -20), at(1300, 200), 1.5, List.of(square(1000, 75, 10)));
        return new CampusMap("campus", "Test campus", java.time.ZoneOffset.UTC, java.util.Map.of(), buildings,
                landmarks, ground, cluePhotos);
    }

    private static CampusMap allLandmarks(Set<Integer> cluePhotos, Building... others) {
        return allLandmarks(cluePhotos, List.of(), others);
    }

    private static Expedition targetHallRound(CampusMap map) {
        return new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
    }

    @Test void aZoneOfLandmarksGetsAnotherLandmarksObscureHistoryAndNoPhoto() {
        Building north = new Building(2, "North Hall", at(1010, 130), 11, "stone", false, 1907, "Darling & Pearson",
                null);
        Expedition round = targetHallRound(allLandmarks(Set.of(1, 2), List.of("It is where first-years live.",
                "It was moved here brick by brick in 1931."), north));
        assertTrue(round.clueBuilding().isEmpty(), "a landmark photo would give the zone away");
        assertEquals("About one building in the zone: it was moved here brick by brick in 1931.",
                round.clueText().orElseThrow(), "the hard hint's fact, not the well-known one");

        assertEquals("One building in the zone was designed by Darling & Pearson.",
                targetHallRound(allLandmarks(Set.of(), north)).clueText().orElseThrow(),
                "no reviewed facts: what OpenStreetMap records");
        assertEquals("One building in the zone was designed by Darling & Pearson.", targetHallRound(allLandmarks(
                Set.of(), List.of("It was built beside Target Hall in 1920."), north)).clueText().orElseThrow(),
                "a fact naming the target is skipped");
        assertEquals("One building in the zone was designed by Darling & Pearson.", targetHallRound(allLandmarks(
                Set.of(), List.of("It houses the registrar."), north)).clueText().orElseThrow(),
                "an undated fact is what students know, not history");
        assertEquals("One building in the zone is 3 storeys, brick.", targetHallRound(allLandmarks(Set.of(),
                new Building(2, "North Hall", at(1010, 130), 11))).clueText().orElseThrow(), "no history: the look");

        Expedition alone = targetHallRound(allLandmarks(Set.of(1)));
        assertTrue(alone.clueBuilding().isEmpty(), "the target's photo is never used");
        assertTrue(alone.clueText().isEmpty(), "nothing but the target");
    }

    @Test void factsReadOnAfterTheColon() {
        assertEquals("it opened in 1978.", ZoneClue.startOfSentence("It opened in 1978."));
        assertEquals("Queen Victoria opened it.", ZoneClue.startOfSentence("Queen Victoria opened it."));
    }

    @Test void anOrdinaryBuildingNearTheEdgeComesBeforeLandmarks() {
        CampusMap map = allLandmarks(Set.of(6), new Building(2, "North Hall", at(1010, 130), 11),
                new Building(6, null, at(1085, 75), 7));
        Expedition round = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        assertEquals(6, round.clueBuilding().orElseThrow(), "the unnamed shed 85 m out, not North Hall");
        assertEquals("One building in the zone is 2 storeys, brick.", round.clueText().orElseThrow());
    }

    @Test void historyComesFirstAndNeverRepeatsTheCurrentName() {
        assertEquals("One building in the zone used to be called the Physics Building.", ZoneClue.describe(
                new Building(9, "Sandford Fleming", at(0, 0), 15, "stone", true, 1907, "Darling", "the Physics Building")));
        assertEquals("One building in the zone was designed by Darling & Pearson.", ZoneClue.describe(
                new Building(9, "Old Hall", at(0, 0), 15, "stone", true, 1892, "Darling & Pearson", "Old Hall Annex")),
                "a former name that contains the current one is skipped");
        assertEquals("One building in the zone dates from the 1890s.", ZoneClue.describe(
                new Building(9, null, at(0, 0), 15, "stone", true, 1892, null, null)));
        assertEquals("One building in the zone is 1 storey, glass.", ZoneClue.describe(
                new Building(9, null, at(0, 0), 3, "glass", false, null, null, null)));
    }

    @Test void noPhotoNoClue() {
        assertTrue(round().clueBuilding().isEmpty(), "no stored photos");
        CampusMap map = campus(Set.of(1, 5));
        Expedition round = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        assertTrue(round.clueBuilding().isEmpty(), "only landmarks are photographed");
    }

    @Test void betaPlayerOnlyBeforeLaunch() {
        CampusMap map = campus();
        Expedition later = new Expedition(map, map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START,
                Achievement.BETA_ENDS);
        later.check(Achievement.BETA_ENDS);
        assertFalse(later.achievements().contains(Achievement.BETA_PLAYER));
        assertTrue(round().achievements().contains(Achievement.BETA_PLAYER));
    }

    @Test void spendingTheOnlyCheckToFindTheZoneEarnsNoCheckBonus() {
        CampusMap map = campus();
        Expedition round = new Expedition(new Difficulty("perfect", 1, 90, 450, 900), map,
                map.landmarks().getFirst(), new Zone(at(1000, 75), 90), START, T0);
        reachZone(round);
        round.chooseHint(HintLevel.HARD);
        round.guess(1);
        // The only check was spent finding the zone: nothing could be saved, so no check bonus.
        assertEquals(0, round.score().checkBonus());
        assertEquals(round.score().routePoints() + 500, round.score().total());
    }

    @Test void threeWrongGuessesEndTheRoundWithNoIdentifyPoints() {
        Expedition round = round();
        reachZone(round);
        round.chooseHint(HintLevel.NORMAL);
        assertFalse(round.guess(2));
        assertFalse(round.guess(3));
        assertFalse(round.guess(6));
        assertEquals(Expedition.Stage.FINISHED, round.stage());
        assertFalse(round.isWon());
        assertEquals(0, round.score().identifyPoints());
        assertTrue(round.score().routePoints() > 0, "found the zone, so the route still scores");
    }

    @Test void zonesAlwaysOfferSeveralRealBuildings() {
        CampusMap map = campus();
        assertTrue(Expedition.offersARealChoice(map, new Zone(at(1000, 75), 90)), "5 buildings, 4 named");
        assertFalse(Expedition.offersARealChoice(map, new Zone(at(100, 140), 90)), "Far Hall stands alone");
        assertFalse(Expedition.offersARealChoice(map, new Zone(at(1000, 75), 50)), "too few within 50 m");
    }

    @Test void theDailyPuzzleIsTheSameForEveryone() {
        CampusMap map = TestCampus.withDaily();
        Expedition first = Expedition.daily(map, TestCampus.DAILY_DATE, T0);
        Expedition second = Expedition.daily(map, TestCampus.DAILY_DATE, T0.plusSeconds(3600));
        assertEquals(first.start(), second.start());
        assertEquals(first.zoneForDiagnostics(), second.zoneForDiagnostics());
        assertEquals(first.shortestToZone(), second.shortestToZone());
        assertEquals(Difficulty.NORMAL, first.difficulty(), "daily is always normal");
        assertThrows(IllegalStateException.class, () -> Expedition.daily(map, TestCampus.DAILY_DATE.plusDays(1), T0));
    }

    @Test void randomRoundsStartOnOpenGroundOutsideTheZone() {
        CampusMap map = campus();
        for (int seed = 0; seed < 10; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), T0);
            assertTrue(map.openSpace().isOpen(round.start()));
            assertFalse(round.zoneForDiagnostics().contains(round.start()));
            assertEquals(Expedition.Stage.FIND_ZONE, round.stage());
            assertTrue(round.shortestToZone() > 300);
        }
    }

    @Test void startsStayOnCampusNotInTheSurroundingCity() {
        CampusMap map = campus();
        // Far Hall stands at (100, 140): a start 40 m from it is on campus, one 300 m from every landmark is not.
        assertEquals(List.of(at(100, 100)), Expedition.onCampus(map, List.of(at(100, 100), at(400, 100))));
        // When nothing is near campus, the candidates closest to it are kept rather than none.
        assertEquals(List.of(at(300, 140), at(320, 140)),
                Expedition.onCampus(map, List.of(at(300, 140), at(320, 140), at(700, 140))));
    }
}
