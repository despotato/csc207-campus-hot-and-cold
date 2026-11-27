package campus.domain.explore;

import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The start-of-round zone clue: a street photo of a building in the zone and a sentence about it. It depends only on
 * the map, the zone and the target, so a daily's clue is the same for everyone. Never the target, never a name and
 * never where the building is. The page mirrors this (LocalRules clueCandidates, clueBuilding, clueText).
 */
final class ZoneClue {
    /** Clue buildings stand this far inside the zone edge where possible. */
    static final double INSET = 10;

    private final CampusMap map;
    private final Zone zone;
    private final int targetBuilding;
    private final Set<Integer> landmarkBuildings;

    ZoneClue(CampusMap map, Zone zone, int targetBuilding) {
        this.map = map;
        this.zone = zone;
        this.targetBuilding = targetBuilding;
        this.landmarkBuildings = map.landmarks().stream().map(CampusLandmark::buildingId).collect(Collectors.toSet());
    }

    /**
     * A photographed ordinary building among the {@link #candidates()}, or empty when none has a stored photo. Never a
     * landmark: a famous building in the photo would give the zone away.
     */
    Optional<Integer> building() {
        return pick(candidates().stream().filter(building -> !isLandmark(building))
                .filter(building -> map.hasCluePhoto(building.id())).toList()).map(Building::id);
    }

    /**
     * The clue in words, for when there's no photo (or it can't load): about the photographed building if there is
     * one; else recorded history of a candidate, or failing that a deliberately vague look. A landmark only ever gets
     * the look (storeys and facade), since its history would name it. Empty only when the zone holds no building but
     * the target.
     */
    Optional<String> text() {
        Optional<Building> photographed = building().map(id -> map.building(id).orElseThrow());
        if (photographed.isPresent()) {
            return photographed.map(ZoneClue::describe);
        }
        List<Building> candidates = candidates();
        if (!candidates.isEmpty() && isLandmark(candidates.getFirst())) {
            return pick(candidates).map(ZoneClue::look);
        }
        return pick(candidates.stream().filter(ZoneClue::hasHistory).toList()).or(() -> pick(candidates))
                .map(ZoneClue::describe);
    }

    /**
     * Buildings the clue may be about, never the target: ordinary buildings 10 m or more inside the zone; else
     * ordinary ones anywhere in it; else, where every building in the zone is a landmark, the other landmarks (10 m
     * inside first). The page measures these with the same haversine formula, so both copies agree at the edges.
     */
    List<Building> candidates() {
        List<Building> inner = map.buildingsIn(new Zone(zone.centre(), zone.radiusMetres() - INSET)).stream()
                .filter(building -> building.id() != targetBuilding).toList();
        List<Building> whole = map.buildingsIn(zone).stream().filter(building -> building.id() != targetBuilding)
                .toList();
        for (List<Building> pool : List.of(inner.stream().filter(building -> !isLandmark(building)).toList(),
                whole.stream().filter(building -> !isLandmark(building)).toList(), inner)) {
            if (!pool.isEmpty()) {
                return pool;
            }
        }
        return whole;
    }

    private boolean isLandmark(Building building) {
        return landmarkBuildings.contains(building.id());
    }

    /** The same pick for everyone: an index taken from the zone's centre. */
    private Optional<Building> pick(List<Building> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        long seed = Math.round(zone.centre().latitude() * 1e6) + Math.round(zone.centre().longitude() * 1e6);
        return Optional.of(candidates.get((int) Math.floorMod(seed, (long) candidates.size())));
    }

    private static boolean hasHistory(Building building) {
        return usableFormerName(building) || building.architect() != null || building.built() != null;
    }

    private static boolean usableFormerName(Building building) {
        return building.formerName() != null
                && (building.name() == null || !LandmarkNames.givesAway(building.formerName(), building.name()));
    }

    /** History first (a former name, the architect, the decade it was built); else storeys and facade only. */
    static String describe(Building building) {
        if (usableFormerName(building)) {
            return "One building in the zone used to be called " + building.formerName() + ".";
        }
        if (building.architect() != null) {
            return "One building in the zone was designed by " + building.architect() + ".";
        }
        if (building.built() != null) {
            return "One building in the zone dates from the " + (building.built() / 10 * 10) + "s.";
        }
        return look(building);
    }

    /** Only what anyone can see: storeys and facade. */
    static String look(Building building) {
        long floors = Math.max(1, Math.round(building.height() / 3.66));
        return "One building in the zone is " + floors + (floors == 1 ? " storey, " : " storeys, ")
                + building.material() + ".";
    }
}
