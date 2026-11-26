package campus.infrastructure;

import campus.domain.explore.CampusMap;
import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.OpenSpace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a campus pack (see tools/build_pack.py) from the classpath. The raw pack is also served to
 * the browser for drawing; this loader extracts only what the game rules need.
 */
public final class CampusPackLoader {
    public static final String ST_GEORGE = "/campus/packs/st-george.json";
    /** Walkability grid resolution; the browser uses the same value (see the explore page). */
    public static final double CELL_METRES = 1.5;

    private CampusPackLoader() { }

    public static String raw(String resource) throws IOException {
        try (InputStream in = CampusPackLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Campus pack not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static CampusMap load(String resource) throws IOException {
        try {
            return parse(raw(resource));
        } catch (IllegalArgumentException | ClassCastException | NullPointerException exception) {
            throw new IOException("Campus pack is invalid: " + exception.getMessage(), exception);
        }
    }

    static CampusMap parse(String json) {
        Map<String, Object> pack = Json.object(Json.parse(json));
        if (Json.number(pack.get("version")) != 2) {
            throw new IllegalArgumentException("Unsupported campus pack version.");
        }
        List<Building> buildings = new ArrayList<>();
        List<List<List<GeoPoint>>> solid = new ArrayList<>();
        for (Object item : Json.array(pack.get("buildings"))) {
            Map<String, Object> building = Json.object(item);
            buildings.add(new Building((int) Json.number(building.get("id")), (String) building.get("name"),
                    new GeoPoint(Json.number(building.get("lat")), Json.number(building.get("lon"))),
                    Json.number(building.get("height")), (String) building.get("material"),
                    Boolean.TRUE.equals(building.get("courtyard")),
                    building.get("built") instanceof Number year ? year.intValue() : null,
                    (String) building.get("architect"), (String) building.get("formerName")));
            if (!Boolean.TRUE.equals(building.get("flat"))) {
                for (Object polygon : Json.array(building.get("outline"))) {
                    solid.add(Json.array(polygon).stream().map(CampusPackLoader::ring).toList());
                }
            }
        }
        List<CampusLandmark> landmarks = new ArrayList<>();
        for (Object item : Json.array(pack.get("landmarks"))) {
            Map<String, Object> landmark = Json.object(item);
            List<String> facts = landmark.get("facts") instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList() : List.of();
            landmarks.add(new CampusLandmark((int) Json.number(landmark.get("id")),
                    (int) Json.number(landmark.get("building")), (String) landmark.get("name"),
                    (String) landmark.get("category"), (String) landmark.get("wikipedia"), facts));
        }
        Map<LocalDate, CampusMap.DailyPuzzle> daily = new HashMap<>();
        if (pack.get("daily") instanceof Map<?, ?> schedule) {
            for (Map.Entry<?, ?> entry : schedule.entrySet()) {
                Map<String, Object> puzzle = Json.object(entry.getValue());
                daily.put(LocalDate.parse((String) entry.getKey()), new CampusMap.DailyPuzzle(
                        (int) Json.number(puzzle.get("landmark")), point(puzzle.get("zone")),
                        point(puzzle.get("start"))));
            }
        }
        Map<String, Object> bounds = Json.object(pack.get("bounds"));
        OpenSpace ground = new OpenSpace(
                new GeoPoint(Json.number(bounds.get("south")), Json.number(bounds.get("west"))),
                new GeoPoint(Json.number(bounds.get("north")), Json.number(bounds.get("east"))), CELL_METRES, solid);
        String id = pack.get("id") instanceof String text ? text : "campus";
        ZoneId zone = pack.get("timezone") instanceof String tz ? ZoneId.of(tz) : ZoneOffset.UTC;
        Set<Integer> cluePhotos = new HashSet<>();
        if (pack.get("cluePhotos") instanceof List<?> photos) {
            for (Object photo : photos) {
                cluePhotos.add((int) Json.number(Json.object(photo).get("building")));
            }
        }
        return new CampusMap(id, (String) pack.get("campus"), zone, daily, buildings, landmarks, ground, cluePhotos);
    }

    private static GeoPoint point(Object lonLat) {
        List<Object> pair = Json.array(lonLat);
        return new GeoPoint(Json.number(pair.get(1)), Json.number(pair.get(0)));
    }

    private static List<GeoPoint> ring(Object value) {
        return Json.array(value).stream().map(item -> {
            List<Object> lonLat = Json.array(item);
            return new GeoPoint(Json.number(lonLat.get(1)), Json.number(lonLat.get(0)));
        }).toList();
    }
}
