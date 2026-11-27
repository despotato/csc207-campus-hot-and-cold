package campus.application.explore;

import campus.domain.explore.FootTrail;
import campus.domain.explore.GeoPoint;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CampusTrafficInteractorTest {
    private final List<ExploreResponse> presented = new ArrayList<>();

    private static FootTrail walk(double metres) {
        GeoPoint a = new GeoPoint(43.66, -79.39);
        return new FootTrail("st-george", List.of(a, a.offset(0, metres)), Instant.EPOCH);
    }

    private ExploreResponse.Traffic traffic(FootTrailRepository store) {
        new CampusTrafficInteractor(store, presented::add).traffic("st-george");
        return (ExploreResponse.Traffic) presented.getLast();
    }

    @Test void shortTrailsAreSkippedAndTheCrowdIsCapped() {
        List<FootTrail> many = new ArrayList<>();
        many.add(walk(10));
        for (int i = 0; i < CampusTrafficInteractor.MAX_STUDENTS + 20; i++) {
            many.add(walk(200));
        }
        ExploreResponse.Traffic result = traffic(new FootTrailRepository() {
            @Override public void record(FootTrail trail) { }
            @Override public List<FootTrail> recent(String campusId, int limit) {
                return many.stream().limit(limit).toList();
            }
        });
        assertEquals(CampusTrafficInteractor.MAX_STUDENTS, result.trails().size());
    }

    @Test void unreadableTrailsMeanAQuietCampusNotAnError() {
        ExploreResponse.Traffic result = traffic(new FootTrailRepository() {
            @Override public void record(FootTrail trail) { }
            @Override public List<FootTrail> recent(String campusId, int limit) throws IOException {
                throw new IOException("disk gone");
            }
        });
        assertEquals(List.of(), result.trails());
    }

    @Test void campusIdsAreChecked() {
        assertThrows(IllegalArgumentException.class,
                () -> new CampusTrafficInteractor(FootTrailRepository.NONE, presented::add).traffic("../x"));
    }
}
