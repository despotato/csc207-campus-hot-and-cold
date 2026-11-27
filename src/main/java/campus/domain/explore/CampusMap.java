package campus.domain.explore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A playable campus: its buildings, the landmarks that can be targets, and the open ground to walk on. */
public final class CampusMap {
    private final String id;
    private final String name;
    private final java.time.ZoneId timezone;
    private final Map<java.time.LocalDate, DailyPuzzle> daily;
    private final Map<Integer, Building> buildings;
    private final List<CampusLandmark> landmarks;
    private final OpenSpace openSpace;

    /**
     * A building as players see it. {@code material} is its facade (brick, stone, glass or concrete); the
     * history fields come from OpenStreetMap and may be null.
     */
    public record Building(int id, String name, GeoPoint centre, double height, String material, boolean courtyard,
                           Integer built, String architect, String formerName) {
        public Building {
            Objects.requireNonNull(centre);
            if (id <= 0) {
                throw new IllegalArgumentException("Building IDs must be positive.");
            }
            material = material == null ? "brick" : material;
        }

        public Building(int id, String name, GeoPoint centre, double height) {
            this(id, name, centre, height, "brick", false, null, null, null);
        }
    }

    /**
     * A building that can be the hidden target. {@code facts} are reviewed facts about it (name blanked), best known
     * first then historical, stored in the campus pack so every player sees the same hints.
     */
    public record CampusLandmark(int id, int buildingId, String name, String category, String wikipediaTitle,
                                 List<String> facts) {
        public CampusLandmark {
            if (id <= 0 || name == null || name.isBlank() || category == null || category.isBlank()) {
                throw new IllegalArgumentException("A landmark needs an ID, name, and category.");
            }
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        public CampusLandmark(int id, int buildingId, String name, String category, String wikipediaTitle) {
            this(id, buildingId, name, category, wikipediaTitle, List.of());
        }
    }

    /**
     * Landmarks that make good targets: ones with a real fact or recorded history for the hard hint. If a campus
     * has none (e.g. in tests), every landmark qualifies.
     */
    public List<CampusLandmark> targets() {
        List<CampusLandmark> good = landmarks.stream().filter(landmark -> !landmark.facts().isEmpty()
                || hasHistory(buildingOf(landmark))).toList();
        return good.isEmpty() ? landmarks : good;
    }

    private static boolean hasHistory(Building building) {
        return building.formerName() != null || building.built() != null || building.architect() != null;
    }

    /** Today's shared puzzle: everyone playing the daily on this date gets the same target, zone and start. */
    public record DailyPuzzle(int landmarkId, GeoPoint zoneCentre, GeoPoint start) {
        public DailyPuzzle {
            Objects.requireNonNull(zoneCentre);
            Objects.requireNonNull(start);
        }
    }

    public CampusMap(String name, List<Building> buildings, List<CampusLandmark> landmarks, OpenSpace openSpace) {
        this("campus", name, java.time.ZoneOffset.UTC, Map.of(), buildings, landmarks, openSpace);
    }

    public CampusMap(String id, String name, java.time.ZoneId timezone, Map<java.time.LocalDate, DailyPuzzle> daily,
                     List<Building> buildings, List<CampusLandmark> landmarks, OpenSpace openSpace) {
        if (id == null || id.isBlank() || name == null || name.isBlank() || landmarks.isEmpty()) {
            throw new IllegalArgumentException("A campus needs an ID, a name and landmarks.");
        }
        this.id = id;
        this.timezone = Objects.requireNonNull(timezone);
        this.daily = Map.copyOf(daily);
        this.name = name;
        this.buildings = buildings.stream().collect(Collectors.toUnmodifiableMap(Building::id, Function.identity()));
        this.landmarks = List.copyOf(landmarks);
        this.openSpace = Objects.requireNonNull(openSpace);
        for (CampusLandmark landmark : landmarks) {
            if (!this.buildings.containsKey(landmark.buildingId())) {
                throw new IllegalArgumentException("Landmark " + landmark.name() + " has no building.");
            }
        }
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** The campus's local time zone, which decides when the daily puzzle changes. */
    public java.time.ZoneId timezone() {
        return timezone;
    }

    public Optional<DailyPuzzle> dailyPuzzle(java.time.LocalDate date) {
        return Optional.ofNullable(daily.get(date));
    }

    public Optional<CampusLandmark> landmark(int id) {
        return landmarks.stream().filter(landmark -> landmark.id() == id).findFirst();
    }

    public List<CampusLandmark> landmarks() {
        return landmarks;
    }

    /** Where players can walk: everywhere on campus outside buildings. */
    public OpenSpace openSpace() {
        return openSpace;
    }

    public Optional<Building> building(int id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public Building buildingOf(CampusLandmark landmark) {
        return buildings.get(landmark.buildingId());
    }

    /**
     * How far {@code point} is from the nearest campus landmark building, in metres. The pack's bounds also take in
     * the surrounding city, so this is how rounds tell campus ground from a street several blocks away.
     */
    public double metresFromCampus(GeoPoint point) {
        return landmarks.stream().mapToDouble(landmark -> buildingOf(landmark).centre().distanceTo(point))
                .min().orElse(Double.POSITIVE_INFINITY);
    }

    /** Buildings whose centre lies inside the zone: the choices for stage two. */
    public List<Building> buildingsIn(Zone zone) {
        return buildings.values().stream().filter(building -> zone.contains(building.centre()))
                .sorted((a, b) -> Integer.compare(a.id(), b.id())).toList();
    }
}
