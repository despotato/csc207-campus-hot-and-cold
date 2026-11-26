package campus.infrastructure;

import campus.domain.explore.FootTrail;
import campus.domain.explore.GeoPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileFootTrailRepositoryTest {
    @TempDir Path directory;

    private static FootTrail trail(String campus, int seconds) {
        GeoPoint a = new GeoPoint(43.66, -79.39);
        return new FootTrail(campus, List.of(a, a.offset(90, 50 + seconds)), Instant.EPOCH.plusSeconds(seconds));
    }

    @Test void trailsComeBackNewestFirstAcrossInstances() throws Exception {
        new FileFootTrailRepository(directory).record(trail("st-george", 1));
        new FileFootTrailRepository(directory).record(trail("st-george", 2));
        new FileFootTrailRepository(directory).record(trail("uwaterloo", 3));
        List<FootTrail> back = new FileFootTrailRepository(directory).recent("st-george", 10);
        assertEquals(2, back.size(), "only that campus");
        assertEquals(Instant.EPOCH.plusSeconds(2), back.getFirst().recordedAt(), "newest first");
        assertEquals(2, back.getFirst().points().size());
        assertEquals(1, new FileFootTrailRepository(directory).recent("st-george", 1).size(), "limit respected");
    }

    @Test void keepsOnlyTheNewestTrails() throws Exception {
        var store = new FileFootTrailRepository(directory);
        for (int i = 0; i < FileFootTrailRepository.KEEP + 15; i++) {
            store.record(trail("guelph", i));
        }
        List<FootTrail> all = store.recent("guelph", 10_000);
        assertEquals(FileFootTrailRepository.KEEP, all.size());
        assertEquals(Instant.EPOCH.plusSeconds(FileFootTrailRepository.KEEP + 14), all.getFirst().recordedAt());
    }

    @Test void damagedLinesAreSkippedAndMissingFilesAreEmpty() throws Exception {
        var store = new FileFootTrailRepository(directory);
        assertTrue(store.recent("laurier", 5).isEmpty());
        store.record(trail("laurier", 1));
        Files.writeString(directory.resolve("laurier.trails.jsonl"), "{not json\n", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        store.record(trail("laurier", 2));
        assertEquals(2, store.recent("laurier", 5).size(), "the damaged line is skipped");
    }

    @Test void campusIdsCannotEscapeTheFolder() {
        assertThrows(IllegalArgumentException.class, () -> new FileFootTrailRepository(directory).recent("../x", 1));
    }
}
