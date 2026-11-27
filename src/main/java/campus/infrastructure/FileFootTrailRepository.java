package campus.infrastructure;

import campus.application.explore.FootTrailRepository;
import campus.domain.explore.FootTrail;
import campus.domain.explore.GeoPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Foot trails on this computer: one JSON-lines file per campus ({@code <campus>.trails.jsonl}), one trail per
 * line, oldest first. Keeps only the newest {@link #KEEP} trails. Unreadable lines are skipped, never fatal.
 */
public final class FileFootTrailRepository implements FootTrailRepository {
    static final int KEEP = 400;

    private final Path directory;

    public FileFootTrailRepository(Path directory) {
        this.directory = directory;
    }

    @Override
    public synchronized void record(FootTrail trail) throws IOException {
        Files.createDirectories(directory);
        Path file = file(trail.campusId());
        String line = Json.write(toJson(trail)) + "\n";
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() > KEEP) {
            Path temp = Files.createTempFile(directory, trail.campusId() + "-", ".tmp");
            try {
                Files.write(temp, lines.subList(lines.size() - KEEP, lines.size()), StandardCharsets.UTF_8);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    @Override
    public synchronized List<FootTrail> recent(String campusId, int limit) throws IOException {
        Path file = file(campusId);
        if (!Files.exists(file)) {
            return List.of();
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<FootTrail> newestFirst = new ArrayList<>();
        for (int i = lines.size() - 1; i >= 0 && newestFirst.size() < limit; i--) {
            try {
                newestFirst.add(fromJson(campusId, Json.object(Json.parse(lines.get(i)))));
            } catch (RuntimeException corrupt) {
                // A damaged line is skipped; the rest are still good.
            }
        }
        return newestFirst;
    }

    private Path file(String campusId) {
        if (!campusId.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Bad campus ID.");
        }
        return directory.resolve(campusId + ".trails.jsonl");
    }

    private static Map<String, Object> toJson(FootTrail trail) {
        List<Object> points = new ArrayList<>();
        for (GeoPoint point : trail.points()) {
            points.add(List.of(round(point.longitude()), round(point.latitude())));
        }
        return Map.of("v", 1, "at", trail.recordedAt().toString(), "points", points);
    }

    private static FootTrail fromJson(String campusId, Map<String, Object> json) {
        List<GeoPoint> points = new ArrayList<>();
        for (Object item : Json.array(json.get("points"))) {
            List<Object> pair = Json.array(item);
            points.add(new GeoPoint(Json.number(pair.get(1)), Json.number(pair.get(0))));
        }
        return new FootTrail(campusId, points, Instant.parse((String) json.get("at")));
    }

    /** About 10 cm: plenty for drawing, and it keeps the files small. */
    private static double round(double degrees) {
        return Math.round(degrees * 1e6) / 1e6;
    }
}
