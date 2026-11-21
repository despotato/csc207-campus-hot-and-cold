package campus.infrastructure;

import campus.application.RoundRepository;
import campus.domain.CompletedRoundResult;
import campus.domain.CompletedRoundResult.Attempt;
import campus.domain.Landmark;
import campus.domain.Round;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/** Versioned data records, one atomic file per completed round. */
public record FileRoundRepository(Path directory) implements RoundRepository {
    private static final int MAGIC = 0x43484352; // CHCR
    private static final int VERSION = 1;
    private static final String EXTENSION = ".round";
    private static final Object WRITE_MONITOR = new Object();

    @Override
    public void save(CompletedRoundResult result) throws IOException {
        Files.createDirectories(directory);
        // Java file locks coordinate processes. The monitor prevents overlapping locks in one JVM.
        synchronized (WRITE_MONITOR) {
            try (var channel = FileChannel.open(directory.resolve(".write.lock"), CREATE, WRITE);
                 var lock = channel.lock()) {
                writeIfAbsent(result);
            }
        }
    }

    private void writeIfAbsent(CompletedRoundResult result) throws IOException {
        Path destination = pathFor(result.id());
        var existing = find(result.id());
        if (existing.isPresent()) {
            if (!existing.get().equals(result)) {
                throw new IOException("A different result already exists for " + destination.getFileName() + ".");
            }
            return;
        }

        Path temporary = Files.createTempFile(directory, "round-", ".tmp");
        try {
            write(temporary, result);
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupError) {
                exception.addSuppressed(cleanupError);
            }
            throw exception;
        }
    }

    @Override
    public Optional<CompletedRoundResult> find(UUID id) throws IOException {
        try {
            return Optional.of(read(pathFor(id)));
        } catch (NoSuchFileException exception) {
            return Optional.empty();
        }
    }

    @Override
    public List<CompletedRoundResult> list() throws IOException {
        List<CompletedRoundResult> results = new ArrayList<>();
        var files = openDirectory();
        if (files == null) {
            return List.of();
        }
        try (files) {
            for (Path file : files) {
                results.add(read(file));
            }
        } catch (DirectoryIteratorException exception) {
            throw new IOException("Could not scan the saved rounds folder.", exception.getCause());
        }
        results.sort(Comparator.comparing(CompletedRoundResult::completedAt).reversed()
                .thenComparing(CompletedRoundResult::id));
        return List.copyOf(results);
    }

    private DirectoryStream<Path> openDirectory() throws IOException {
        try {
            return Files.newDirectoryStream(directory, "*" + EXTENSION);
        } catch (NoSuchFileException exception) {
            return null;
        } catch (IOException exception) {
            throw new IOException("Could not open the saved rounds folder.", exception);
        }
    }

    private Path pathFor(UUID id) {
        return directory.resolve(id.toString() + EXTENSION);
    }

    private static CompletedRoundResult read(Path file) throws IOException {
        try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unrecognized round file format.");
            }
            var id = new UUID(input.readLong(), input.readLong());
            if (!file.getFileName().toString().equals(id + EXTENSION)) {
                throw new IOException("Round ID does not match file name.");
            }
            String campus = input.readUTF();
            Instant completedAt = Instant.parse(input.readUTF());
            Landmark answer = readLandmark(input);
            int outcome = input.readUnsignedByte();
            int score = input.readUnsignedByte();
            int count = input.readUnsignedByte();
            if (outcome > 1 || count < 1 || count > Round.GUESS_LIMIT) {
                throw new IOException("Invalid outcome or guess count.");
            }
            List<Attempt> guesses = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                guesses.add(new Attempt(readLandmark(input), input.readDouble()));
            }
            if (input.read() != -1) {
                throw new IOException("Unexpected trailing data.");
            }
            return new CompletedRoundResult(id, campus, completedAt, answer, guesses, outcome == 1, score);
        } catch (NoSuchFileException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException | DateTimeParseException exception) {
            throw new IOException("Unreadable saved round " + file.getFileName() + ".", exception);
        }
    }

    private static void write(Path file, CompletedRoundResult result) throws IOException {
        try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            output.writeLong(result.id().getMostSignificantBits());
            output.writeLong(result.id().getLeastSignificantBits());
            output.writeUTF(result.campus());
            output.writeUTF(result.completedAt().toString());
            writeLandmark(output, result.answer());
            output.writeBoolean(result.won());
            output.writeByte(result.score());
            output.writeByte(result.guesses().size());
            for (Attempt guess : result.guesses()) {
                writeLandmark(output, guess.landmark());
                output.writeDouble(guess.distanceMetres());
            }
        }
    }

    private static Landmark readLandmark(DataInputStream input) throws IOException {
        return new Landmark(input.readLong(), input.readUTF(), input.readDouble(), input.readDouble());
    }

    private static void writeLandmark(DataOutputStream output, Landmark landmark) throws IOException {
        output.writeLong(landmark.id());
        output.writeUTF(landmark.name());
        output.writeDouble(landmark.latitude());
        output.writeDouble(landmark.longitude());
    }
}
