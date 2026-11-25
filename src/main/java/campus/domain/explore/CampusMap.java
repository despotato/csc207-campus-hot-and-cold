package campus.domain.explore;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A playable campus: its buildings, the landmarks that can be targets, and the open ground to walk on. */
public final class CampusMap {
    private final String name;
    private final Map<Integer, Building> buildings;
    private final List<CampusLandmark> landmarks;
    private final OpenSpace openSpace;

    public record Building(int id, String name, GeoPoint centre) {
        public Building {
            Objects.requireNonNull(centre);
            if (id <= 0) {
                throw new IllegalArgumentException("Building IDs must be positive.");
            }
        }
    }

    public record CampusLandmark(int id, int buildingId, String name, String category, String wikipediaTitle) {
        public CampusLandmark {
            if (id <= 0 || name == null || name.isBlank() || category == null || category.isBlank()) {
                throw new IllegalArgumentException("A landmark needs an ID, name, and category.");
            }
        }
    }

    public CampusMap(String name, List<Building> buildings, List<CampusLandmark> landmarks, OpenSpace openSpace) {
        if (name == null || name.isBlank() || landmarks.isEmpty()) {
            throw new IllegalArgumentException("A campus needs a name and landmarks.");
        }
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

    public String name() {
        return name;
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

    /** Buildings whose centre lies inside the zone: the choices for stage two. */
    public List<Building> buildingsIn(Zone zone) {
        return buildings.values().stream().filter(building -> zone.contains(building.centre()))
                .sorted((a, b) -> Integer.compare(a.id(), b.id())).toList();
    }
}
