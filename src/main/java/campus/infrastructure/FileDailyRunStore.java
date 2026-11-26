package campus.infrastructure;

import campus.application.explore.DailyRunStore;
import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Today's daily round as versioned JSON ({@code <campus>-<date>.run.json}), written atomically. Unreadable
 * or tampered files are treated as missing rather than crashing the game.
 */
public final class FileDailyRunStore implements DailyRunStore {
    private static final int VERSION = 1;

    private final Path directory;

    public FileDailyRunStore(Path directory) {
        this.directory = directory;
    }

    @Override
    public void save(String campusId, LocalDate date, Expedition.Snapshot round) throws IOException {
        Path file = file(campusId, date);
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, campusId + "-", ".tmp");
        try {
            Files.writeString(temp, Json.write(toJson(round)), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public Optional<Expedition.Snapshot> load(String campusId, LocalDate date) throws IOException {
        Path file = file(campusId, date);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(fromJson(Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)))));
        } catch (RuntimeException corrupt) {
            return Optional.empty();
        }
    }

    private Path file(String campusId, LocalDate date) throws IOException {
        if (!campusId.matches("[a-z0-9-]+")) {
            throw new IOException("Invalid campus ID.");
        }
        return directory.resolve(campusId + "-" + date + ".run.json");
    }

    private static Map<String, Object> toJson(Expedition.Snapshot s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", VERSION);
        out.put("id", s.id().toString());
        out.put("difficulty", s.difficulty());
        out.put("target", s.targetLandmarkId());
        out.put("zoneCentre", point(s.zoneCentre()));
        out.put("zoneRadius", s.zoneRadius());
        out.put("start", point(s.start()));
        out.put("startedAt", s.startedAt().toString());
        out.put("position", point(s.position()));
        out.put("lastMoveAt", s.lastMoveAt().toString());
        out.put("metresWalked", s.metresWalked());
        out.put("travelSeconds", s.travelSeconds());
        out.put("stage", s.stage().name());
        out.put("zoneReachedAt", s.zoneReachedAt() == null ? null : s.zoneReachedAt().toString());
        out.put("hintLevel", s.hintLevel() == null ? null : s.hintLevel().name());
        out.put("outsideClue", s.outsideClue());
        out.put("won", s.won());
        out.put("track", s.track().stream().map(FileDailyRunStore::point).toList());
        out.put("checks", s.checks().stream().map(c -> {
            Map<String, Object> check = new LinkedHashMap<>();
            check.put("point", point(c.point()));
            check.put("heat", c.heat().name());
            check.put("trend", c.trend().name());
            check.put("metres", c.metresToZone());
            check.put("inZone", c.inZone());
            return check;
        }).toList());
        out.put("guesses", s.guesses());
        return out;
    }

    private static Expedition.Snapshot fromJson(Map<String, Object> in) {
        if (Json.number(in.get("version")) != VERSION) {
            throw new IllegalArgumentException("Unsupported daily run version.");
        }
        List<Expedition.Check> checks = new ArrayList<>();
        for (Object item : Json.array(in.get("checks"))) {
            Map<String, Object> c = Json.object(item);
            checks.add(new Expedition.Check(geo(c.get("point")), Expedition.Heat.valueOf((String) c.get("heat")),
                    Expedition.Trend.valueOf((String) c.get("trend")), Json.number(c.get("metres")),
                    Boolean.TRUE.equals(c.get("inZone"))));
        }
        return new Expedition.Snapshot(UUID.fromString((String) in.get("id")), (String) in.get("difficulty"),
                (int) Json.number(in.get("target")), geo(in.get("zoneCentre")), Json.number(in.get("zoneRadius")),
                geo(in.get("start")), Instant.parse((String) in.get("startedAt")), geo(in.get("position")),
                Instant.parse((String) in.get("lastMoveAt")), Json.number(in.get("metresWalked")),
                Json.number(in.get("travelSeconds")), Expedition.Stage.valueOf((String) in.get("stage")),
                in.get("zoneReachedAt") instanceof String at ? Instant.parse(at) : null,
                in.get("hintLevel") instanceof String level ? Expedition.HintLevel.valueOf(level) : null,
                (String) in.get("outsideClue"), Boolean.TRUE.equals(in.get("won")),
                Json.array(in.get("track")).stream().map(FileDailyRunStore::geo).toList(), checks,
                Json.array(in.get("guesses")).stream().map(g -> (int) Json.number(g)).toList());
    }

    private static List<Double> point(GeoPoint p) {
        return List.of(p.longitude(), p.latitude());
    }

    private static GeoPoint geo(Object lonLat) {
        List<Object> pair = Json.array(lonLat);
        return new GeoPoint(Json.number(pair.get(1)), Json.number(pair.get(0)));
    }
}
