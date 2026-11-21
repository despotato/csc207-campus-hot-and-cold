package campus.infrastructure;

import campus.domain.CompletedRoundResult;
import campus.domain.CompletedRoundResult.Attempt;
import campus.domain.Landmark;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class FileRoundRepositoryTest {
    @TempDir Path directory;

    private static CompletedRoundResult result(String time) {
        Landmark answer = new Landmark(1, "Hart House", 43.663, -79.394);
        Landmark other = new Landmark(2, "Bibliothèque", 43.665, -79.395);
        return new CompletedRoundResult(UUID.randomUUID(), "St. George", Instant.parse(time),
                answer, List.of(new Attempt(other, other.distanceTo(answer)), new Attempt(answer, 0)), true, 5);
    }

    @Test
    void restoresTheFullRecapAcrossRepositoryInstances() throws IOException {
        var result = result("2026-11-21T10:00:00.123456789Z");
        new FileRoundRepository(directory).save(result);
        var reopened = new FileRoundRepository(directory).find(result.id()).orElseThrow();

        assertEquals(result, reopened);
        assertEquals(List.of("Bibliothèque", "Hart House"),
                reopened.guesses().stream().map(guess -> guess.landmark().name()).toList());
        assertEquals(236.61, reopened.guesses().getFirst().distanceMetres(), 0.5);
    }

    @Test
    void historySortsByCompletionNewestFirst() throws IOException {
        var earlier = result("2026-11-20T10:00:00Z");
        var later = result("2026-11-21T10:00:00Z");
        var repository = new FileRoundRepository(directory);
        repository.save(later);
        repository.save(earlier);
        assertEquals(List.of(later, earlier), repository.list());
    }

    @Test
    void retryKeepsOneFileAndRefusesConflictingDataForTheSameId() throws IOException {
        var result = result("2026-11-21T10:00:00Z");
        var repository = new FileRoundRepository(directory);
        repository.save(result);
        new FileRoundRepository(directory).save(result);
        var conflict = new CompletedRoundResult(result.id(), "Different campus", result.completedAt(),
                result.answer(), result.guesses(), result.won(), result.score());

        assertThrows(IOException.class, () -> repository.save(conflict));
        assertEquals(List.of(result), repository.list());
        try (var files = Files.newDirectoryStream(directory, "*.round")) {
            assertEquals(List.of(result.id() + ".round"),
                    java.util.stream.StreamSupport.stream(files.spliterator(), false)
                            .map(path -> path.getFileName().toString()).toList());
        }
    }

    @Test
    void missingDirectoryOrIdIsEmpty() throws IOException {
        var repository = new FileRoundRepository(directory.resolve("missing"));
        assertTrue(repository.list().isEmpty());
        assertTrue(repository.find(UUID.randomUUID()).isEmpty());
    }

    @Test
    void corruptDataIsReportedAndNeverOverwrittenByARetry() throws IOException {
        var result = result("2026-11-21T10:00:00Z");
        var repository = new FileRoundRepository(directory);
        repository.save(result);
        Path savedFile;
        try (var files = Files.newDirectoryStream(directory, "*.round")) {
            savedFile = files.iterator().next();
        }
        Files.writeString(savedFile, "corrupt");

        assertThrows(IOException.class, repository::list);
        assertThrows(IOException.class, () -> repository.find(result.id()));
        assertThrows(IOException.class, () -> repository.save(result));
        assertEquals("corrupt", Files.readString(savedFile));
    }

    @Test
    void rejectsAValidLookingDistanceThatDoesNotMatchSavedCoordinates() throws IOException {
        var result = result("2026-11-21T10:00:00Z");
        var repository = new FileRoundRepository(directory);
        repository.save(result);
        Path file = directory.resolve(result.id() + ".round");
        byte[] bytes = Files.readAllBytes(file);
        // Replace the first distance, preserving the remaining fields and record size.
        int offset = bytes.length - Double.BYTES - (Long.BYTES + Short.BYTES
                + "Hart House".length() + 2 * Double.BYTES) - Double.BYTES;
        ByteBuffer.wrap(bytes).putDouble(offset, 500);
        Files.write(file, bytes);

        var error = assertThrows(IOException.class, () -> repository.find(result.id()));
        assertTrue(error.getMessage().contains(file.getFileName().toString()));
    }

    @Test
    void concurrentConflictingWritersPublishExactlyOneResult() throws Exception {
        var first = result("2026-11-21T10:00:00Z");
        var second = new CompletedRoundResult(first.id(), "Different campus", first.completedAt(),
                first.answer(), first.guesses(), first.won(), first.score());
        var ready = new CountDownLatch(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> tryConcurrentSave(ready, first));
            var b = workers.submit(() -> tryConcurrentSave(ready, second));
            boolean firstSaved = a.get();
            assertNotEquals(firstSaved, b.get());
            assertEquals(List.of(firstSaved ? first : second), new FileRoundRepository(directory).list());
        }
    }

    private boolean tryConcurrentSave(CountDownLatch ready, CompletedRoundResult result)
            throws InterruptedException {
        ready.countDown();
        ready.await();
        try {
            new FileRoundRepository(directory).save(result);
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    @Test
    void invalidStoragePathReportsWriteFailure() throws IOException {
        Path blocked = Files.writeString(directory.resolve("blocked"), "keep this");
        var repository = new FileRoundRepository(blocked);
        assertThrows(IOException.class, () -> repository.save(result("2026-11-21T10:00:00Z")));
        assertThrows(IOException.class, repository::list);
        assertEquals("keep this", Files.readString(blocked));
    }
}
