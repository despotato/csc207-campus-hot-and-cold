package campus.domain.explore;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootTrailTest {
    @Test void walkedPathsAreThinnedButKeepTheirEnds() {
        GeoPoint start = new GeoPoint(43.66, -79.39);
        List<GeoPoint> walked = new ArrayList<>();
        for (int metres = 0; metres <= 101; metres += 3) {
            walked.add(start.offset(90, metres));
        }
        FootTrail trail = FootTrail.of("st-george", walked, Instant.EPOCH);
        assertEquals(walked.getFirst(), trail.points().getFirst());
        assertEquals(walked.getLast(), trail.points().getLast());
        assertTrue(trail.points().size() < walked.size() / 2, "about one point every 8 m");
        assertEquals(99, trail.metres(), 3);
    }

    @Test void aTrailNeedsACampusAndTwoPoints() {
        GeoPoint p = new GeoPoint(43.66, -79.39);
        assertThrows(IllegalArgumentException.class, () -> new FootTrail("st-george", List.of(p), Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new FootTrail("../x", List.of(p, p), Instant.EPOCH));
    }
}
