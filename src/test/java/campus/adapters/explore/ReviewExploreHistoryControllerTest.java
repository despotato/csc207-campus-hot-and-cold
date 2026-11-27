package campus.adapters.explore;

import campus.application.explore.*;
import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReviewExploreHistoryControllerTest {
    private static ExploreResponse.Result result(int score) {
        GeoPoint point = new GeoPoint(43.66, -79.39);
        return new ExploreResponse.Result(true, "Robarts Library", "Library", 10, point,
                400, 1, 250, 100, 50, 0, null, 1.5, score, 400, 320, 60L, 3, 1,
                Expedition.HintLevel.NORMAL, new ExploreResponse.ZoneView(point, 90),
                List.of(point), List.of(point), List.of(), List.of(), null);
    }

    private static CampusCatalog catalog() {
        return new CampusCatalog() {
            @Override public List<String> campusIds() { return List.of("st-george"); }
            @Override public Optional<campus.domain.explore.CampusMap> campus(String id) {
                return "st-george".equals(id) ? Optional.empty() : Optional.empty();
            }
        };
    }

    @Test void controllerListsNewestRoundsAndReadsOneRecap() {
        UUID laterId = UUID.randomUUID();
        UUID earlierId = UUID.randomUUID();
        var later = new SavedExpedition(laterId, "st-george", Instant.parse("2026-11-21T10:00:00Z"),
                "NORMAL", "daily", LocalDate.parse("2026-11-21"), result(900));
        var earlier = new SavedExpedition(earlierId, "st-george", Instant.parse("2026-11-20T10:00:00Z"),
                "NORMAL", "unlimited", null, result(700));
        ExpeditionResultRepository repository = new ExpeditionResultRepository() {
            @Override public void save(SavedExpedition round) { }
            @Override public Optional<SavedExpedition> find(UUID id) {
                return id.equals(laterId) ? Optional.of(later) : Optional.of(earlier);
            }
            @Override public List<SavedExpedition> all() { return List.of(later, earlier); }
        };
        var presenter = new ExplorePresenter();
        var history = new ReviewExploreHistoryInteractor(catalog(), repository, presenter);
        var controller = new ExploreController(request -> { }, (id, path) -> { }, (id, point) -> { },
                id -> { }, (id, level) -> { }, (id, building) -> { }, presenter).withHistory(history);

        var list = controller.handle("history", Map.of());
        assertEquals(200, list.status());
        assertEquals("history", list.body().get("type"));
        List<?> rounds = (List<?>) list.body().get("rounds");
        assertEquals(laterId.toString(), ((Map<?, ?>) rounds.getFirst()).get("roundId"));

        var recap = controller.handle("recap", Map.of("round", laterId.toString()));
        assertEquals(200, recap.status());
        assertEquals("recap", recap.body().get("type"));
        assertEquals("Robarts Library",
                ((Map<?, ?>) ((Map<?, ?>) recap.body().get("result")).get("target")).get("name"));
    }

    @Test void controllerRejectsMissingRoundAndUnknownCampus() {
        ExpeditionResultRepository repository = new ExpeditionResultRepository() {
            @Override public void save(SavedExpedition round) { }
            @Override public Optional<SavedExpedition> find(UUID id) { return Optional.empty(); }
            @Override public List<SavedExpedition> all() { return List.of(); }
        };
        var presenter = new ExplorePresenter();
        var controller = new ExploreController(request -> { }, (id, path) -> { }, (id, point) -> { },
                id -> { }, (id, level) -> { }, (id, building) -> { }, presenter)
                .withHistory(new ReviewExploreHistoryInteractor(catalog(), repository, presenter));

        assertEquals(400, controller.handle("recap", Map.of()).status());
        assertEquals(400, controller.handle("history", Map.of("campus", "nowhere")).status());
    }
}
