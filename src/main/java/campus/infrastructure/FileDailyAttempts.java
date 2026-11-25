package campus.infrastructure;

import campus.application.explore.DailyAttempts;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/** One marker file per campus and date (e.g. st-george-2026-11-25) under the player's game folder. */
public final class FileDailyAttempts implements DailyAttempts {
    private final Path directory;

    public FileDailyAttempts(Path directory) {
        this.directory = directory;
    }

    @Override
    public boolean claim(String campusId, LocalDate date) throws IOException {
        if (!campusId.matches("[a-z0-9-]+")) {
            throw new IOException("Invalid campus ID.");
        }
        Files.createDirectories(directory);
        try {
            Files.createFile(directory.resolve(campusId + "-" + date));
            return true;
        } catch (FileAlreadyExistsException alreadyPlayed) {
            return false;
        }
    }
}
