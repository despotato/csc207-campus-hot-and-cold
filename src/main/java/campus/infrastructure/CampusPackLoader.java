package campus.infrastructure;

import campus.domain.explore.CampusMap;
import campus.domain.explore.CampusMap.Building;
import campus.domain.explore.CampusMap.CampusLandmark;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.WalkGraph;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a campus pack (see tools/build_pack.py) from the classpath. The raw pack is also served to
 * the browser for drawing; this loader extracts only what the game rules need.
 */
public final class CampusPackLoader {
    public static final String ST_GEORGE = "/campus/packs/st-george.json";

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
        for (Object item : Json.array(pack.get("buildings"))) {
            Map<String, Object> building = Json.object(item);
            buildings.add(new Building((int) Json.number(building.get("id")), (String) building.get("name"),
                    new GeoPoint(Json.number(building.get("lat")), Json.number(building.get("lon")))));
        }
        List<CampusLandmark> landmarks = new ArrayList<>();
        for (Object item : Json.array(pack.get("landmarks"))) {
            Map<String, Object> landmark = Json.object(item);
            landmarks.add(new CampusLandmark((int) Json.number(landmark.get("id")),
                    (int) Json.number(landmark.get("building")), (String) landmark.get("name"),
                    (String) landmark.get("category"), (String) landmark.get("wikipedia")));
        }
        Map<String, Object> walkways = Json.object(pack.get("walkways"));
        List<GeoPoint> nodes = new ArrayList<>();
        for (Object item : Json.array(walkways.get("nodes"))) {
            List<Object> lonLat = Json.array(item);
            nodes.add(new GeoPoint(Json.number(lonLat.get(1)), Json.number(lonLat.get(0))));
        }
        List<int[]> edges = new ArrayList<>();
        for (Object item : Json.array(walkways.get("edges"))) {
            List<Object> edge = Json.array(item);
            edges.add(new int[]{(int) Json.number(edge.get(0)), (int) Json.number(edge.get(1))});
        }
        return new CampusMap((String) pack.get("campus"), buildings, landmarks, new WalkGraph(nodes, edges));
    }
}
