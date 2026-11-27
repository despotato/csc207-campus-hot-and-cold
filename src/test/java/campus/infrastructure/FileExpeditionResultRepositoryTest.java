package campus.infrastructure;

import campus.application.explore.ExploreResponse;
import campus.application.explore.SavedExpedition;
import campus.domain.explore.Achievement;
import campus.domain.explore.Expedition;
import campus.domain.explore.Expedition.HintLevel;
import campus.domain.explore.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileExpeditionResultRepositoryTest {
    @TempDir Path directory;

    private static SavedExpedition result(String time, String campus, String target, int score) {
        GeoPoint start = new GeoPoint(43.66, -79.39);
        GeoPoint zone = new GeoPoint(43.662, -79.392);
        var route = List.of(start, zone);
        var shortest = List.of(start, new GeoPoint(43.661, -79.391), zone);
        var result = new ExploreResponse.Result(true, target, "Library", 10, zone, 400, 0.8,
                250, 100, 50, 0, null, 1.5, score, 420, 336, 75L, 3, 1, HintLevel.NORMAL,
                new ExploreResponse.ZoneView(zone, 90), route, shortest, List.of(
                        new Expedition.Check(start, Expedition.Heat.WARM, Expedition.Trend.FIRST, 200, false),
                        new Expedition.Check(zone, Expedition.Heat.HOT, Expedition.Trend.WARMER, 0, true)),
                List.of(Achievement.BETA_PLAYER), null);
        return new SavedExpedition(UUID.randomUUID(), campus, Instant.parse(time),
                "NORMAL", "daily", LocalDate.parse(time.substring(0, 10)), result);
    }

    @Test void reloadsAcrossRepositoryInstancesAndRestoresReplayRoutes() throws IOException {
        var saved = result("2026-11-21T10:00:00Z", "st-george", "Robarts Library", 900);
        new FileExpeditionResultRepository(directory).save(saved);

        var reopened = new FileExpeditionResultRepository(directory).find(saved.id()).orElseThrow();
        assertEquals(saved, reopened);
        assertEquals(List.of(saved.result().route().getFirst(), saved.result().route().getLast()),
                reopened.result().route());
        assertEquals(2, reopened.result().checks().size());
        assertEquals(List.of(Achievement.BETA_PLAYER), reopened.result().achievements());
    }

    @Test void sortsNewestFirstAndRejectsDuplicateWithDifferentData() throws IOException {
        var earlier = result("2026-11-20T10:00:00Z", "st-george", "Earlier", 700);
        var later = result("2026-11-21T10:00:00Z", "st-george", "Later", 800);
        var repository = new FileExpeditionResultRepository(directory);
        repository.save(later);
        repository.save(earlier);
        repository.save(earlier);
        assertEquals(List.of(later, earlier), repository.all());

        var conflict = new SavedExpedition(earlier.id(), earlier.campusId(), earlier.finishedAt(),
                earlier.difficulty(), earlier.mode(), earlier.dailyDate(),
                result("2026-11-20T10:00:00Z", "st-george", "Changed", 100).result());
        assertThrows(IOException.class, () -> repository.save(conflict));
    }

    @Test void missingDirectoryIsEmptyAndCorruptFilesAreSkippedFromHistory() throws IOException {
        var missing = new FileExpeditionResultRepository(directory.resolve("missing"));
        assertTrue(missing.all().isEmpty());
        assertTrue(missing.find(UUID.randomUUID()).isEmpty());

        var valid = result("2026-11-21T10:00:00Z", "st-george", "Valid", 850);
        var repository = new FileExpeditionResultRepository(directory);
        repository.save(valid);
        Files.writeString(directory.resolve("st-george").resolve(UUID.randomUUID() + ".json"), "{bad");

        assertEquals(List.of(valid), repository.all());
    }

    @Test void corruptRequestedFileAndBlockedWriteAreReported() throws IOException {
        var saved = result("2026-11-21T10:00:00Z", "st-george", "Broken", 850);
        var repository = new FileExpeditionResultRepository(directory);
        repository.save(saved);
        Path file = directory.resolve("st-george").resolve(saved.id() + ".json");
        Files.writeString(file, "{corrupt");

        assertThrows(IOException.class, () -> repository.find(saved.id()));

        Path blocked = Files.writeString(directory.resolve("blocked"), "keep");
        assertThrows(IOException.class,
                () -> new FileExpeditionResultRepository(blocked).save(saved));
        assertEquals("keep", Files.readString(blocked));
    }
}
