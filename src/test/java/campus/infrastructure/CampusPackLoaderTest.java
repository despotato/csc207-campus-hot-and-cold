package campus.infrastructure;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CampusPackLoaderTest {
    @Test void jsonRoundTripsAndEscapesHtml() {
        Object value = Json.parse("{\"a\":[1,2.5,-3e2,true,false,null],\"b\":\"x\\\"y\\u00e9\\n\"}");
        Map<String, Object> object = Json.object(value);
        assertEquals(List.of(1.0, 2.5, -300.0, true, false), Json.array(object.get("a")).subList(0, 5));
        assertNull(Json.array(object.get("a")).get(5));
        assertEquals("x\"yé\n", object.get("b"));
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("s", "</script>");
        out.put("n", List.of(1, 0.5));
        assertEquals("{\"s\":\"\\u003c/script\\u003e\",\"n\":[1,0.5]}", Json.write(out));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,2"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("1 2"));
    }

    @Test void stGeorgePackLoadsWithLandmarksBuildingsAndConnectedWalkways() throws Exception {
        CampusMap map = CampusPackLoader.load(CampusPackLoader.ST_GEORGE);
        assertEquals("University of Toronto — St. George", map.name());
        assertEquals(76, map.landmarks().size());
        assertTrue(map.landmarks().stream().anyMatch(l -> l.name().equals("Robarts Library")));
        assertTrue(map.landmarks().stream().anyMatch(l -> l.category().equals("Residence")));
        long mainNetwork = java.util.stream.IntStream.range(0, map.walkways().size())
                .filter(map.walkways()::isMainNetwork).count();
        assertTrue(mainNetwork > 6000, "most of the footpath network is connected");
    }

    @Test void realCampusRoundsAreSetUpCorrectly() throws Exception {
        CampusMap map = CampusPackLoader.load(CampusPackLoader.ST_GEORGE);
        for (int seed = 0; seed < 25; seed++) {
            Expedition round = Expedition.begin(map, new Random(seed), Instant.EPOCH);
            assertTrue(round.shortestToZone() >= 300, "a real walk to the zone");
            assertTrue(round.shortestToZone() < 2000);
        }
    }

    @Test void rejectsUnsupportedPacks() {
        assertThrows(IllegalArgumentException.class, () -> CampusPackLoader.parse("{\"version\":1}"));
    }
}
