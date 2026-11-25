package campus.domain.explore;

import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.WalkGraph.Position;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * One two-stage round. Stage one: walk the campus paths and use limited hot/cold checks to reach
 * the zone. Stage two: choose a hint difficulty blind, then identify the building inside the zone.
 */
public final class Expedition {
    public static final int GUESS_LIMIT = 3;
    /** The target sits at most this far from the zone centre, so the centre never gives it away exactly. */
    static final double MAX_ZONE_OFFSET = 50;
    /** Fast jog with slack for network jitter; anything quicker is treated as teleporting. */
    static final double MAX_SPEED = 9;
    static final double MAX_STEP = 60;
    static final int MIN_ZONE_BUILDINGS = 4;

    public enum Stage { FIND_ZONE, IDENTIFY, FINISHED }

    public enum Heat { HOT, WARM, COOL, COLD }

    public enum Trend { FIRST, WARMER, SAME, COLDER }

    public enum HintLevel {
        EASY(1.0), NORMAL(1.5), HARD(2.0);

        private final double multiplier;

        HintLevel(double multiplier) {
            this.multiplier = multiplier;
        }

        public double multiplier() {
            return multiplier;
        }
    }

    public record Check(GeoPoint point, Heat heat, Trend trend, double metresToZone) { }

    public record Score(int routePoints, double efficiency, int identifyPoints, double multiplier, int total) { }

    private final UUID id = UUID.randomUUID();
    private final Difficulty difficulty;
    private final CampusMap map;
    private final CampusLandmark target;
    private final Zone zone;
    private final Position start;
    private final double shortestToZone;
    private final List<Integer> shortestRoute;
    private final Instant startedAt;
    private final List<GeoPoint> track = new ArrayList<>();
    private final List<Check> checks = new ArrayList<>();
    private final List<Integer> guesses = new ArrayList<>();

    private Position position;
    private Instant lastMoveAt;
    private double metresWalked;
    private Stage stage = Stage.FIND_ZONE;
    private Instant zoneReachedAt;
    private HintLevel hintLevel;
    private String outsideClue;
    private boolean won;

    Expedition(CampusMap map, CampusLandmark target, Zone zone, int startNode, Instant now) {
        this(Difficulty.NORMAL, map, target, zone, startNode, now);
    }

    Expedition(Difficulty difficulty, CampusMap map, CampusLandmark target, Zone zone, int startNode, Instant now) {
        this.difficulty = Objects.requireNonNull(difficulty);
        this.map = Objects.requireNonNull(map);
        this.target = Objects.requireNonNull(target);
        this.zone = Objects.requireNonNull(zone);
        WalkGraph walkways = map.walkways();
        if (!walkways.isMainNetwork(startNode)) {
            throw new IllegalArgumentException("The start must be on the main walkway network.");
        }
        if (!zone.contains(map.buildingOf(target).centre())) {
            throw new IllegalArgumentException("The target must be inside the zone.");
        }
        if (zone.contains(walkways.node(startNode))) {
            throw new IllegalArgumentException("The start must be outside the zone.");
        }
        WalkGraph.Route route = walkways.shortestRoute(startNode, node -> zone.contains(walkways.node(node)))
                .orElseThrow(() -> new IllegalArgumentException("The zone cannot be reached on foot."));
        this.start = walkways.at(startNode);
        this.position = start;
        this.shortestToZone = route.metres();
        this.shortestRoute = route.nodes();
        this.startedAt = Objects.requireNonNull(now);
        this.lastMoveAt = now;
        track.add(walkways.node(startNode));
    }

    /** Picks a random target, an offset zone around it, and a start a long walk away. */
    public static Expedition begin(CampusMap map, RandomGenerator random, Instant now) {
        return begin(Difficulty.NORMAL, map, random, now);
    }

    public static Expedition begin(Difficulty difficulty, CampusMap map, RandomGenerator random, Instant now) {
        WalkGraph walkways = map.walkways();
        int[] network = IntStream.range(0, walkways.size()).filter(walkways::isMainNetwork).toArray();
        for (int attempt = 0; attempt < 200; attempt++) {
            CampusLandmark target = map.landmarks().get(random.nextInt(map.landmarks().size()));
            GeoPoint targetCentre = map.buildingOf(target).centre();
            Zone zone = new Zone(targetCentre.offset(random.nextDouble(360),
                    random.nextDouble(Math.min(MAX_ZONE_OFFSET, difficulty.zoneRadius() * 0.6))),
                    difficulty.zoneRadius());
            if (map.buildingsIn(zone).size() < MIN_ZONE_BUILDINGS) {
                continue;
            }
            int[] starts = IntStream.of(network).filter(node -> {
                double distance = walkways.node(node).distanceTo(zone.centre());
                return distance >= difficulty.minStartDistance() && distance <= difficulty.maxStartDistance();
            }).toArray();
            if (starts.length == 0) {
                continue;
            }
            try {
                return new Expedition(difficulty, map, target, zone, starts[random.nextInt(starts.length)], now);
            } catch (IllegalArgumentException unreachable) {
                // Try another target.
            }
        }
        throw new IllegalStateException("Could not set up a round on this campus.");
    }

    /** Walks to a new spot on the paths. Every metre counts, including backtracking. */
    public void moveTo(Position next, Instant now) {
        requireStage(Stage.FIND_ZONE, "Movement ends once you reach the zone.");
        double seconds = Math.max(0, Duration.between(lastMoveAt, now).toMillis() / 1000.0);
        double allowed = Math.min(MAX_STEP, MAX_SPEED * (seconds + 0.5));
        OptionalDouble step = map.walkways().walkingDistance(position, next, allowed);
        if (step.isEmpty()) {
            throw new IllegalArgumentException("That move is too far or not along a path.");
        }
        metresWalked += step.getAsDouble();
        position = next;
        lastMoveAt = now;
        GeoPoint point = map.walkways().pointAt(next);
        if (point.distanceTo(track.getLast()) >= 3) {
            track.add(point);
        }
        if (zone.contains(point)) {
            track.add(point);
            stage = Stage.IDENTIFY;
            zoneReachedAt = now;
        }
    }

    /** Spends one temperature check at the current spot. */
    public Check check() {
        requireStage(Stage.FIND_ZONE, "Checks are only used while finding the zone.");
        if (checksRemaining() == 0) {
            throw new IllegalStateException("No checks remaining. Keep exploring.");
        }
        GeoPoint here = map.walkways().pointAt(position);
        double distance = here.distanceTo(zone.centre());
        Heat heat = distance < 150 ? Heat.HOT : distance < 300 ? Heat.WARM : distance < 500 ? Heat.COOL : Heat.COLD;
        Trend trend = Trend.FIRST;
        if (!checks.isEmpty()) {
            double change = distance - checks.getLast().metresToZone();
            trend = Math.abs(change) < 5 ? Trend.SAME : change < 0 ? Trend.WARMER : Trend.COLDER;
        }
        Check check = new Check(here, heat, trend, distance);
        checks.add(check);
        return check;
    }

    /** Commits to a hint difficulty before seeing it, then reveals the hint. */
    public String chooseHint(HintLevel level) {
        return chooseHint(level, landmark -> Optional.empty());
    }

    /**
     * As {@link #chooseHint(HintLevel)}, but a hard hint may use an outside clue about the target
     * (for example a redacted encyclopedia sentence) instead of its direction within the zone.
     */
    public String chooseHint(HintLevel level, Function<CampusLandmark, Optional<String>> outsideClue) {
        requireStage(Stage.IDENTIFY, "Reach the zone before choosing a hint.");
        if (hintLevel != null) {
            throw new IllegalStateException("You already chose a hint.");
        }
        Objects.requireNonNull(level);
        if (level == HintLevel.HARD) {
            String name = target.name().toLowerCase(Locale.ROOT);
            outsideClue.apply(target)
                    .filter(clue -> !clue.isBlank() && !clue.toLowerCase(Locale.ROOT).contains(name))
                    .ifPresent(clue -> this.outsideClue = clue);
        }
        hintLevel = level;
        return hint().orElseThrow();
    }

    public Optional<String> hint() {
        if (hintLevel == null) {
            return Optional.empty();
        }
        String category = target.category().toLowerCase(Locale.ROOT);
        String article = category.matches("^[aeiou].*") ? "an " : "a ";
        return Optional.of(switch (hintLevel) {
            case EASY -> "It's " + article + category + " building, and its name starts with \""
                    + Character.toUpperCase(target.name().charAt(0)) + "\".";
            case NORMAL -> "It's " + article + category + " building.";
            case HARD -> outsideClue != null ? outsideClue : directionInZone();
        });
    }

    /** Guesses a building inside the zone. Returns true if it is the target. */
    public boolean guess(int buildingId) {
        requireStage(Stage.IDENTIFY, "Reach the zone before guessing.");
        if (hintLevel == null) {
            throw new IllegalStateException("Choose a hint first.");
        }
        Building building = map.building(buildingId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown building."));
        if (!zone.contains(building.centre())) {
            throw new IllegalArgumentException("That building is outside the zone.");
        }
        if (guesses.contains(buildingId)) {
            throw new IllegalArgumentException("You already guessed that building.");
        }
        guesses.add(buildingId);
        won = buildingId == target.buildingId();
        if (won || guesses.size() == GUESS_LIMIT) {
            stage = Stage.FINISHED;
        }
        return won;
    }

    public Score score() {
        double efficiency = metresWalked <= 0 ? 0 : Math.min(1, shortestToZone / metresWalked);
        int routePoints = stage == Stage.FIND_ZONE ? 0 : (int) Math.round(500 * efficiency);
        int base = won ? new int[]{300, 200, 100}[guesses.size() - 1] : 0;
        double multiplier = hintLevel == null ? 1 : hintLevel.multiplier();
        int identifyPoints = (int) Math.round(base * multiplier);
        return new Score(routePoints, efficiency, identifyPoints, multiplier, routePoints + identifyPoints);
    }

    private String directionInZone() {
        GeoPoint centre = zone.centre();
        GeoPoint building = map.buildingOf(target).centre();
        if (centre.distanceTo(building) < 20) {
            return "It's near the middle of the zone.";
        }
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return "It's in the " + names[(int) Math.round(centre.bearingTo(building) / 45) % 8] + " part of the zone.";
    }

    private void requireStage(Stage expected, String message) {
        if (stage != expected) {
            throw new IllegalStateException(message);
        }
    }

    public UUID id() { return id; }
    public CampusMap map() { return map; }
    public Stage stage() { return stage; }
    public Position position() { return position; }
    public Position start() { return start; }
    public double metresWalked() { return metresWalked; }
    public int checksRemaining() { return difficulty.checks() - checks.size(); }
    public int checksUsed() { return checks.size(); }
    public Difficulty difficulty() { return difficulty; }
    public List<Check> checks() { return List.copyOf(checks); }
    public List<GeoPoint> track() { return List.copyOf(track); }
    public List<Integer> guesses() { return List.copyOf(guesses); }
    public Optional<HintLevel> hintLevel() { return Optional.ofNullable(hintLevel); }
    public boolean isWon() { return won; }
    public Instant startedAt() { return startedAt; }
    public Optional<Instant> zoneReachedAt() { return Optional.ofNullable(zoneReachedAt); }

    /** The zone is shown to the player only after they have walked into it. */
    public Optional<Zone> revealedZone() {
        return stage == Stage.FIND_ZONE ? Optional.empty() : Optional.of(zone);
    }

    /** The target is revealed only when the round is over. */
    public Optional<CampusLandmark> revealedTarget() {
        return stage == Stage.FINISHED ? Optional.of(target) : Optional.empty();
    }

    /** The shortest route to the zone, for the end-of-round replay only. */
    public Optional<List<GeoPoint>> revealedShortestRoute() {
        return stage == Stage.FINISHED
                ? Optional.of(shortestRoute.stream().map(map.walkways()::node).toList())
                : Optional.empty();
    }

    public double shortestToZone() { return shortestToZone; }

    /**
     * The hidden zone, for automated visual tests and diagnostics only. Player-facing code must use
     * {@link #revealedZone()}; the explore server exposes this only when started in debug mode.
     */
    public Zone zoneForDiagnostics() { return zone; }
}
