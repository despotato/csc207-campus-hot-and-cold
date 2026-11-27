package campus.application.explore;

import campus.domain.explore.FootTrail;
import campus.domain.explore.GeoPoint;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Returns recent foot trails for a campus. Each becomes one walking student, so the more people play, the busier
 * the campus looks. Trails are ambience: if they can't be read, the campus is simply quiet.
 */
public final class CampusTrafficInteractor implements CampusTrafficInputBoundary {
    /** At most this many students walk around at once. */
    public static final int MAX_STUDENTS = 60;
    /** Trails shorter than this (metres) don't make a convincing walk. */
    static final double MIN_TRAIL_METRES = 40;

    private final FootTrailRepository trails;
    private final ExploreOutputBoundary presenter;

    public CampusTrafficInteractor(FootTrailRepository trails, ExploreOutputBoundary presenter) {
        this.trails = Objects.requireNonNull(trails);
        this.presenter = Objects.requireNonNull(presenter);
    }

    @Override
    public void traffic(String campusId) {
        if (campusId == null || !campusId.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Choose a campus.");
        }
        List<List<GeoPoint>> walks;
        try {
            walks = trails.recent(campusId, MAX_STUDENTS * 2).stream()
                    .filter(trail -> trail.metres() >= MIN_TRAIL_METRES)
                    .limit(MAX_STUDENTS).map(FootTrail::points).toList();
        } catch (IOException exception) {
            walks = List.of();
        }
        presenter.present(new ExploreResponse.Traffic(campusId, walks));
    }
}
