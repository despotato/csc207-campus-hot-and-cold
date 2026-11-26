package campus.application.explore;

import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExplorePresenter;
import campus.adapters.explore.ExploreViewModel;
import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.TestCampus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Today's daily survives reloads and app restarts: resume where you left off, see the result once finished. */
class DailyResumeTest {
    private final CampusMap map = TestCampus.withDaily();
    private final ExploreFlowTest.MutableClock clock = new ExploreFlowTest.MutableClock();
    /** Stands in for the files in ~/.campus-hot-and-cold/daily, which outlive the app. */
    private final Set<String> claims = new HashSet<>();
    private final Map<String, Expedition.Snapshot> savedRuns = new HashMap<>();
    private final DailyRunStore store = new DailyRunStore() {
        @Override public void save(String campus, LocalDate date, Expedition.Snapshot round) {
            savedRuns.put(campus + date, round);
        }

        @Override public Optional<Expedition.Snapshot> load(String campus, LocalDate date) {
            return Optional.ofNullable(savedRuns.get(campus + date));
        }
    };
    private final DailyAttempts attempts = new DailyAttempts() {
        @Override public boolean claim(String campus, LocalDate date) {
            return claims.add(campus + date);
        }

        @Override public boolean claimed(String campus, LocalDate date) {
            return claims.contains(campus + date);
        }
    };

    /** A fresh app: new session and controller, sharing only the "files" above. */
    private ExploreController launchApp() {
        var session = new ExpeditionSession(store, clock);
        var presenter = new ExplorePresenter();
        var finisher = new RoundFinisher(LandmarkInfoGateway.NONE, ExpeditionResultRepository.DISABLED, clock);
        CampusCatalog catalog = new CampusCatalog() {
            @Override public List<String> campusIds() { return List.of("test"); }
            @Override public Optional<CampusMap> campus(String id) {
                return "test".equals(id) ? Optional.of(map) : Optional.empty();
            }
        };
        var daily = new DailyRunInteractor(catalog, attempts, store, session, finisher, clock, presenter);
        return new ExploreController(
                new StartExpeditionInteractor(catalog, attempts, new Random(1), clock, session, presenter),
                new MoveInteractor(clock, session, presenter), new TravelInteractor(clock, session, presenter),
                new CheckTemperatureInteractor(clock, session, finisher, presenter),
                new ChooseHintInteractor(session, presenter), new GuessBuildingInteractor(session, finisher, presenter),
                presenter).withDailyRuns(daily, daily);
    }

    private static ExploreViewModel call(ExploreController app, String action, String round, Object... pairs) {
        Map<String, Object> fields = new HashMap<>();
        if (round != null) {
            fields.put("round", round);
        }
        for (int i = 0; i < pairs.length; i += 2) {
            fields.put((String) pairs[i], pairs[i + 1]);
        }
        return app.handle(action, fields);
    }

    private ExploreViewModel walk(ExploreController app, String round, GeoPoint from, GeoPoint to) {
        ExploreViewModel last = null;
        int steps = (int) Math.ceil(from.distanceTo(to) / 10);
        for (int i = 1; i <= steps; i++) {
            GeoPoint p = from.towards(to, (double) i / steps);
            clock.now = clock.now.plusSeconds(1);
            last = call(app, "move", round, "path", List.of(List.of(p.longitude(), p.latitude())));
            assertEquals(200, last.status());
        }
        return last;
    }

    @Test void anUnfinishedDailyResumesAfterAnAppRestartAndCanBeFinished() {
        ExploreController app = launchApp();
        assertEquals("available", call(app, "dailyStatus", null).body().get("status"));
        String round = (String) call(app, "start", null, "mode", "daily").body().get("roundId");
        GeoPoint start = ExpeditionTestPoints.START;
        GeoPoint midway = start.offset(90, 200);
        walk(app, round, start, midway);
        clock.now = clock.now.plusSeconds(1);
        assertEquals(1, call(app, "check", round).body().get("number"));

        ExploreController restarted = launchApp();   // the app was closed and reopened
        assertEquals("inProgress", call(restarted, "dailyStatus", null).body().get("status"));
        assertEquals(400, call(restarted, "start", null, "mode", "daily").status(), "no second try");
        ExploreViewModel resumed = call(restarted, "resumeDaily", null);
        assertEquals(200, resumed.status());
        assertEquals("resumed", resumed.body().get("type"));
        assertEquals(round, resumed.body().get("roundId"), "the same round, not a new one");
        assertEquals("walk", resumed.body().get("stage"));
        assertEquals(200L, resumed.body().get("metresWalked"));
        assertEquals(List.of(midway.longitude(), midway.latitude()), resumed.body().get("point"));
        assertEquals(5, resumed.body().get("checksRemaining"));
        assertEquals(1, ((List<?>) resumed.body().get("checks")).size());

        // Carry on from where we were: around the wall into the zone, find it, pick a hint, guess.
        GeoPoint a = start.offset(90, 490).offset(0, 51);
        GeoPoint b = start.offset(90, 510).offset(0, 51);
        GeoPoint inZone = start.offset(90, 912).offset(0, 3);
        walk(restarted, round, midway, a);
        walk(restarted, round, a, b);
        walk(restarted, round, b, inZone);
        clock.now = clock.now.plusSeconds(1);
        assertEquals(true, call(restarted, "check", round).body().get("zoneFound"));
        assertEquals(200, call(restarted, "hint", round, "level", "easy").status());

        ExploreController third = launchApp();       // restart again, mid-identification
        ExploreViewModel atHint = call(third, "resumeDaily", null);
        assertEquals("hint", atHint.body().get("stage"));
        assertNotNull(atHint.body().get("zone"));
        assertEquals("easy", ((Map<?, ?>) atHint.body().get("hint")).get("level"));
        assertFalse(((List<?>) atHint.body().get("zoneBuildings")).isEmpty());
        assertEquals(false, call(third, "guess", round, "buildingId", 4).body().get("correct"));

        ExploreController fourth = launchApp();
        assertEquals("guess", call(fourth, "resumeDaily", null).body().get("stage"));
        ExploreViewModel finished = call(fourth, "guess", round, "buildingId", 1);
        assertEquals(true, finished.body().get("correct"));
        assertNotNull(finished.body().get("result"));
    }

    @Test void aFinishedDailyShowsItsResultAfterRestartsButCannotBeReplayed() {
        ExploreController app = launchApp();
        String round = (String) call(app, "start", null, "mode", "daily").body().get("roundId");
        // Use every check far away, then a failed final search ends the round.
        for (int i = 0; i < 6; i++) {
            call(app, "check", round);
        }
        ExploreViewModel ended = call(app, "check", round);
        assertNotNull(ended.body().get("result"), "the final search outside the zone ends the round");

        ExploreController restarted = launchApp();
        ExploreViewModel status = call(restarted, "dailyStatus", null);
        assertEquals("finished", status.body().get("status"));
        Map<?, ?> result = (Map<?, ?>) status.body().get("result");
        assertEquals("Target Hall", ((Map<?, ?>) result.get("target")).get("name"));
        assertEquals(0, ((Map<?, ?>) result.get("score")).get("total"));
        assertEquals("finished", call(restarted, "resumeDaily", null).body().get("status"),
                "resuming a finished daily just shows the result");
        assertEquals(400, call(restarted, "start", null, "mode", "daily").status());
        assertEquals(200, call(restarted, "start", null, "mode", "unlimited").status());
    }

    @Test void aStartedDailyWithNothingSavedIsReportedAsUsed() {
        claims.add("test" + TestCampus.DAILY_DATE);
        ExploreController app = launchApp();
        assertEquals("used", call(app, "dailyStatus", null).body().get("status"));
        assertEquals(400, call(app, "resumeDaily", null).status());
    }

    @Test void walkingIsCheckpointedAtMostEveryTwoSeconds() {
        ExploreController app = launchApp();
        String round = (String) call(app, "start", null, "mode", "daily").body().get("roundId");
        GeoPoint p = ExpeditionTestPoints.START.offset(90, 5);
        call(app, "move", round, "path", List.of(List.of(p.longitude(), p.latitude())));
        assertEquals(0, savedRuns.get("test" + TestCampus.DAILY_DATE).metresWalked(), "too soon after the start");
        clock.now = clock.now.plusSeconds(3);
        GeoPoint q = p.offset(90, 5);
        call(app, "move", round, "path", List.of(List.of(q.longitude(), q.latitude())));
        assertEquals(10, savedRuns.get("test" + TestCampus.DAILY_DATE).metresWalked(), 0.5);
    }

}
