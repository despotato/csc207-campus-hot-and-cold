package campus.infrastructure;

import campus.application.explore.ExpeditionResultRepository;
import campus.application.explore.ExploreResponse;
import campus.application.explore.SavedExpedition;
import campus.domain.explore.Achievement;
import campus.domain.explore.Expedition;
import campus.domain.explore.Expedition.HintLevel;
import campus.domain.explore.GeoPoint;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

/** One validated, versioned JSON file per completed Explore round. */
public record FileExpeditionResultRepository(Path directory) implements ExpeditionResultRepository {
    private static final int VERSION = 1;
    private static final String EXTENSION = ".json";
    private static final long MAX_FILE_BYTES = 8 * 1024 * 1024;
    private static final Object WRITE_MONITOR = new Object();

    @Override
    public void save(SavedExpedition round) throws IOException {
        Files.createDirectories(directory);
        Path campusDirectory = directory.resolve(round.campusId());
        Files.createDirectories(campusDirectory);
        synchronized (WRITE_MONITOR) {
            try (var channel = FileChannel.open(directory.resolve(".write.lock"), CREATE, WRITE);
                 var lock = channel.lock()) {
                writeIfAbsent(round, campusDirectory);
            }
        }
    }

    private void writeIfAbsent(SavedExpedition round, Path campusDirectory) throws IOException {
        Optional<SavedExpedition> existing = find(round.id());
        if (existing.isPresent()) {
            if (!existing.get().equals(round)) {
                throw new IOException("A different Explore result already exists for " + round.id() + ".");
            }
            return;
        }

        Path temporary = Files.createTempFile(campusDirectory, "round-", ".tmp");
        try {
            Files.writeString(temporary, Json.write(toJson(round)), StandardCharsets.UTF_8, WRITE);
            Files.move(temporary, campusDirectory.resolve(round.id() + EXTENSION), ATOMIC_MOVE, REPLACE_EXISTING);
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
    public Optional<SavedExpedition> find(UUID roundId) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The Explore results path is not a directory.");
            }
            return Optional.empty();
        }
        Path match = null;
        try (DirectoryStream<Path> campuses = Files.newDirectoryStream(directory)) {
            for (Path campus : campuses) {
                if (!Files.isDirectory(campus, LinkOption.NOFOLLOW_LINKS)
                        || !campus.getFileName().toString().matches("[a-z0-9-]+")) {
                    continue;
                }
                Path file = campus.resolve(roundId + EXTENSION);
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (match != null) {
                        throw new IOException("Duplicate Explore result ID " + roundId + ".");
                    }
                    match = file;
                }
            }
        } catch (DirectoryIteratorException exception) {
            throw new IOException("Could not scan the Explore results folder.", exception.getCause());
        }
        return match == null ? Optional.empty() : Optional.of(read(match));
    }

    @Override
    public List<SavedExpedition> all() throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The Explore results path is not a directory.");
            }
            return List.of();
        }
        List<SavedExpedition> rounds = new ArrayList<>();
        try (DirectoryStream<Path> campuses = Files.newDirectoryStream(directory)) {
            for (Path campus : campuses) {
                if (!Files.isDirectory(campus, LinkOption.NOFOLLOW_LINKS)
                        || !campus.getFileName().toString().matches("[a-z0-9-]+")) {
                    continue;
                }
                try (DirectoryStream<Path> files = Files.newDirectoryStream(campus, "*" + EXTENSION)) {
                    for (Path file : files) {
                        try {
                            rounds.add(read(file));
                        } catch (IOException ignored) {
                            // A corrupt saved round must not hide valid history or prevent statistics from loading.
                        }
                    }
                } catch (DirectoryIteratorException exception) {
                    throw new IOException("Could not scan an Explore campus folder.", exception.getCause());
                }
            }
        } catch (DirectoryIteratorException exception) {
            throw new IOException("Could not scan the Explore results folder.", exception.getCause());
        }
        rounds.sort(Comparator.comparing(SavedExpedition::finishedAt).reversed()
                .thenComparing(SavedExpedition::id));
        return List.copyOf(rounds);
    }

    private static SavedExpedition read(Path file) throws IOException {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) {
                throw new IOException("Invalid Explore result file.");
            }
            Map<String, Object> root = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            if (integer(root, "version") != VERSION) {
                throw new IOException("Unrecognized Explore result version.");
            }
            UUID id = UUID.fromString(text(root, "roundId"));
            if (!file.getFileName().toString().equals(id + EXTENSION)
                    || !file.getParent().getFileName().toString().equals(text(root, "campusId"))) {
                throw new IOException("Explore result ID or campus does not match its file.");
            }
            String mode = text(root, "mode");
            Object dateValue = root.get("dailyDate");
            LocalDate dailyDate = dateValue == null ? null : LocalDate.parse(asText(dateValue));
            SavedExpedition saved = new SavedExpedition(id, text(root, "campusId"),
                    Instant.parse(text(root, "finishedAt")), text(root, "difficulty"), mode, dailyDate,
                    result(Json.object(root.get("result"))));
            if (!valid(saved.result())) {
                throw new IOException("Invalid Explore result values.");
            }
            return saved;
        } catch (NoSuchFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IOException("Unreadable Explore result " + file.getFileName() + ".", exception);
        } catch (IllegalArgumentException | DateTimeParseException exception) {
            throw new IOException("Unreadable Explore result " + file.getFileName() + ".", exception);
        }
    }

    private static boolean valid(ExploreResponse.Result result) {
        if (!validText(result) || !validScore(result) || !validNumbers(result)) {
            return false;
        }
        return result.checksUsed() >= 0 && result.guessesUsed() >= 0 && result.guessesUsed() <= 3
                && result.zone() != null && !result.route().isEmpty() && !result.shortestRoute().isEmpty();
    }

    private static boolean validText(ExploreResponse.Result result) {
        return !textMissing(result.targetName()) && !textMissing(result.category()) && result.targetBuildingId() > 0;
    }

    private static boolean validScore(ExploreResponse.Result result) {
        return !negative(result.routePoints()) && !negative(result.guessPoints())
                && !negative(result.hintBonus()) && !negative(result.checkBonus())
                && !negative(result.nearMissPoints())
                && !negative(result.score()) && result.score() <= 1000;
    }

    private static boolean validNumbers(ExploreResponse.Result result) {
        return finiteRange(result.efficiency(), 0, 1)
                && finiteRange(result.multiplier(), 0, Double.MAX_VALUE)
                && finiteAtLeastZero(result.metresWalked())
                && finiteAtLeastZero(result.shortestMetres())
                && (result.metresShort() == null || finiteAtLeastZero(result.metresShort()));
    }

    private static boolean textMissing(String value) {
        return value == null || value.isBlank();
    }

    private static boolean negative(int value) {
        return value < 0;
    }

    private static boolean finiteRange(double value, double minimum, double maximum) {
        return Double.isFinite(value) && value >= minimum && value <= maximum;
    }

    private static boolean finiteAtLeastZero(double value) {
        return Double.isFinite(value) && value >= 0;
    }

    private static Map<String, Object> toJson(SavedExpedition round) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", VERSION);
        root.put("roundId", round.id().toString());
        root.put("campusId", round.campusId());
        root.put("finishedAt", round.finishedAt().toString());
        root.put("difficulty", round.difficulty());
        root.put("mode", round.mode());
        root.put("dailyDate", round.dailyDate() == null ? null : round.dailyDate().toString());
        root.put("result", resultJson(round.result()));
        return root;
    }

    private static Map<String, Object> resultJson(ExploreResponse.Result result) {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("won", result.won());
        saved.put("targetName", result.targetName());
        saved.put("category", result.category());
        saved.put("targetBuildingId", result.targetBuildingId());
        saved.put("targetCentre", point(result.targetCentre()));
        saved.put("routePoints", result.routePoints());
        saved.put("efficiency", result.efficiency());
        saved.put("guessPoints", result.guessPoints());
        saved.put("hintBonus", result.hintBonus());
        saved.put("checkBonus", result.checkBonus());
        saved.put("nearMissPoints", result.nearMissPoints());
        saved.put("metresShort", result.metresShort());
        saved.put("multiplier", result.multiplier());
        saved.put("score", result.score());
        saved.put("metresWalked", result.metresWalked());
        saved.put("shortestMetres", result.shortestMetres());
        saved.put("secondsToZone", result.secondsToZone());
        saved.put("checksUsed", result.checksUsed());
        saved.put("guessesUsed", result.guessesUsed());
        saved.put("hintLevel", result.hintLevel() == null ? null : result.hintLevel().name());
        saved.put("zone", Map.of("centre", point(result.zone().centre()),
                "radius", result.zone().radiusMetres()));
        saved.put("route", points(result.route()));
        saved.put("shortestRoute", points(result.shortestRoute()));
        saved.put("checks", result.checks().stream().map(FileExpeditionResultRepository::checkJson).toList());
        saved.put("achievements", result.achievements().stream().map(Enum::name).toList());
        if (result.summary() != null) {
            saved.put("summary", Map.of("title", result.summary().title(), "extract", result.summary().extract(),
                    "imageUrl", result.summary().imageUrl() == null ? "" : result.summary().imageUrl(),
                    "pageUrl", result.summary().pageUrl() == null ? "" : result.summary().pageUrl()));
        } else {
            saved.put("summary", null);
        }
        return saved;
    }

    private static Map<String, Object> checkJson(Expedition.Check check) {
        return Map.of("point", point(check.point()), "heat", check.heat().name(),
                "trend", check.trend().name(), "metresToZone", check.metresToZone(),
                "inZone", check.inZone());
    }

    private static ExploreResponse.Result result(Map<String, Object> saved) {
        Map<String, Object> zone = Json.object(saved.get("zone"));
        List<Expedition.Check> checks = Json.array(saved.get("checks")).stream()
                .map(item -> {
                    Map<String, Object> check = Json.object(item);
                    return new Expedition.Check(point(check.get("point")),
                            Expedition.Heat.valueOf(text(check, "heat")),
                            Expedition.Trend.valueOf(text(check, "trend")),
                            number(check, "metresToZone"), bool(check, "inZone"));
                }).toList();
        ExploreResponse.LandmarkSummary summary = null;
        if (saved.get("summary") != null) {
            Map<String, Object> item = Json.object(saved.get("summary"));
            summary = new ExploreResponse.LandmarkSummary(text(item, "title"), text(item, "extract"),
                    emptyToNull(text(item, "imageUrl")), emptyToNull(text(item, "pageUrl")));
        }
        Object seconds = saved.get("secondsToZone");
        Object hint = saved.get("hintLevel");
        Object metresShort = saved.get("metresShort");
        List<Achievement> achievements = saved.containsKey("achievements")
                ? Json.array(saved.get("achievements")).stream()
                .map(item -> Achievement.valueOf(asText(item))).toList()
                : List.of();
        return new ExploreResponse.Result(bool(saved, "won"), text(saved, "targetName"), text(saved, "category"),
                integer(saved, "targetBuildingId"), point(saved.get("targetCentre")),
                integer(saved, "routePoints"), number(saved, "efficiency"), integer(saved, "guessPoints"),
                integer(saved, "hintBonus"), integer(saved, "checkBonus"),
                saved.containsKey("nearMissPoints") ? integer(saved, "nearMissPoints") : 0,
                metresShort == null ? null : Json.number(metresShort), number(saved, "multiplier"),
                integer(saved, "score"), number(saved, "metresWalked"), number(saved, "shortestMetres"),
                seconds == null ? null : asLong(seconds), integer(saved, "checksUsed"), integer(saved, "guessesUsed"),
                hint == null ? null : HintLevel.valueOf(asText(hint)),
                new ExploreResponse.ZoneView(point(zone.get("centre")), number(zone, "radius")),
                points(saved.get("route")), points(saved.get("shortestRoute")), checks, achievements, summary);
    }

    private static List<Double> point(GeoPoint point) {
        return List.of(point.longitude(), point.latitude());
    }

    private static List<List<Double>> points(List<GeoPoint> points) {
        return points.stream().map(FileExpeditionResultRepository::point).toList();
    }

    private static GeoPoint point(Object value) {
        List<Object> coords = Json.array(value);
        if (coords.size() != 2) {
            throw new IllegalArgumentException("Invalid saved point.");
        }
        return new GeoPoint(Json.number(coords.get(1)), Json.number(coords.get(0)));
    }

    private static List<GeoPoint> points(Object value) {
        return Json.array(value).stream().map(FileExpeditionResultRepository::point).toList();
    }

    private static String text(Map<String, Object> values, String key) {
        return asText(values.get(key));
    }

    private static String asText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Invalid saved text.");
    }

    private static int integer(Map<String, Object> values, String key) {
        double value = number(values, key);
        if (value != Math.rint(value) || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid saved integer.");
        }
        return (int) value;
    }

    private static long asLong(Object value) {
        double number = finite(value);
        if (number != Math.rint(number) || number < Long.MIN_VALUE || number > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid saved long.");
        }
        return (long) number;
    }

    private static double number(Map<String, Object> values, String key) {
        return finite(values.get(key));
    }

    private static double finite(Object value) {
        double number = Json.number(value);
        if (!Double.isFinite(number)) {
            throw new IllegalArgumentException("Invalid saved number.");
        }
        return number;
    }

    private static boolean bool(Map<String, Object> values, String key) {
        if (values.get(key) instanceof Boolean result) {
            return result;
        }
        throw new IllegalArgumentException("Invalid saved boolean.");
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
