package campus.domain.explore;

import campus.application.explore.ExploreResponse;
import campus.application.explore.SavedExpedition;
import campus.domain.explore.Expedition.HintLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerRecordTest {
    private static SavedExpedition round(String campus, String mode, LocalDate date, int score,
                                         boolean won, HintLevel hint) {
        GeoPoint point = new GeoPoint(43.66, -79.39);
        var result = new ExploreResponse.Result(won, "Target", "Library", 1, point,
                400, 0.8, 250, 100, 50, 1.5, score, 400, 320, 60L, 3, 1, hint,
                new ExploreResponse.ZoneView(point, 90), List.of(point), List.of(point),
                List.of(), null);
        return new SavedExpedition(UUID.randomUUID(), campus, Instant.parse("2026-11-21T10:00:00Z"),
                "normal", mode, date, result);
    }

    @Test void emptyRecordIsFriendlyAndAllNumbersAreZero() {
        var record = PlayerRecord.from(List.of(), "home", ZoneOffset.UTC, LocalDate.parse("2026-11-21"));
        assertEquals(0, record.roundsPlayed());
        assertEquals(0, record.winRate());
        assertEquals(0, record.bestScore());
        assertEquals("none", record.favouriteHintLevel());
        assertTrue(record.scoreHistogram().isEmpty());
    }

    @Test void oneRoundProducesAveragesBestsAndHistogramBucket() {
        var record = PlayerRecord.from(List.of(round("st-george", "unlimited", null, 750, true,
                HintLevel.NORMAL)), "st-george", ZoneOffset.UTC, LocalDate.parse("2026-11-21"));
        assertEquals(1, record.roundsPlayed());
        assertEquals(1, record.winRate());
        assertEquals(750, record.averageScore());
        assertEquals(750, record.bestScore());
        assertEquals(750, record.bestScorePerCampus().get("st-george"));
        assertEquals(1L, record.scoreHistogram().get(700));
    }

    @Test void missedDayEndsAndLongDailyStreakIsPreserved() {
        var a = round("home", "daily", LocalDate.parse("2026-11-19"), 500, true, HintLevel.EASY);
        var b = round("home", "daily", LocalDate.parse("2026-11-20"), 600, true, HintLevel.EASY);
        var c = round("home", "daily", LocalDate.parse("2026-11-22"), 700, true, HintLevel.HARD);
        var record = PlayerRecord.from(List.of(a, b, c), "home", ZoneOffset.UTC,
                LocalDate.parse("2026-11-23"));
        assertEquals(2, record.longestDailyStreak());
        assertEquals(1, record.currentDailyStreak());
    }

    @Test void campusTimezoneCanCrossMidnightButUsesLocalDailyDates() {
        var first = round("home", "daily", LocalDate.parse("2026-11-25"), 500, true, HintLevel.NORMAL);
        var second = round("home", "daily", LocalDate.parse("2026-11-26"), 600, true, HintLevel.NORMAL);
        var record = PlayerRecord.from(List.of(first, second), "home",
                java.time.ZoneId.of("America/New_York"), LocalDate.parse("2026-11-26"));
        assertEquals(2, record.currentDailyStreak());
        assertEquals(2, record.longestDailyStreak());
    }

    @Test void perCampusBestsAndCorruptEntriesAreRepresentedByValidRoundsOnly() {
        var st = round("st-george", "unlimited", null, 500, true, HintLevel.EASY);
        var stTwo = round("st-george", "unlimited", null, 800, false, HintLevel.HARD);
        var ubc = round("ubc", "unlimited", null, 650, true, HintLevel.NORMAL);
        var record = PlayerRecord.from(List.of(st, stTwo, ubc), "st-george", ZoneOffset.UTC,
                LocalDate.parse("2026-11-21"));
        assertEquals(800, record.bestScorePerCampus().get("st-george"));
        assertEquals(650, record.bestScorePerCampus().get("ubc"));
        assertEquals(3, record.roundsPlayed());
        assertEquals("easy", record.favouriteHintLevel());
    }

    @Test void histogramUsesOneHundredPointBuckets() {
        var record = PlayerRecord.from(List.of(
                round("home", "unlimited", null, 99, true, null),
                round("home", "unlimited", null, 100, true, null),
                round("home", "unlimited", null, 199, true, null),
                round("home", "unlimited", null, 1000, true, null)),
                "home", ZoneOffset.UTC, LocalDate.parse("2026-11-21"));
        assertEquals(1L, record.scoreHistogram().get(0));
        assertEquals(2L, record.scoreHistogram().get(100));
        assertEquals(1L, record.scoreHistogram().get(1000));
    }
}
