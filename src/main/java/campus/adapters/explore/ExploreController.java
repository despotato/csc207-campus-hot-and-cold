package campus.adapters.explore;

import campus.application.explore.CheckTemperatureInputBoundary;
import campus.application.explore.ChooseHintInputBoundary;
import campus.application.explore.GuessBuildingInputBoundary;
import campus.application.explore.MoveInputBoundary;
import campus.application.explore.StartExpeditionInputBoundary;
import campus.application.explore.StartRequest;
import campus.application.explore.TravelInputBoundary;
import campus.domain.explore.Difficulty;
import campus.domain.explore.GeoPoint;
import campus.domain.explore.Expedition.HintLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Converts raw request fields from the browser into use-case calls. Returns the presenter's
 * resulting view model. Calls are synchronized because the game state is shared.
 */
public final class ExploreController {
    private static final int MAX_PATH_POINTS = 64;

    private final StartExpeditionInputBoundary start;
    private final MoveInputBoundary move;
    private final TravelInputBoundary travel;
    private final CheckTemperatureInputBoundary check;
    private final ChooseHintInputBoundary hint;
    private final GuessBuildingInputBoundary guess;
    private final ExplorePresenter presenter;

    public ExploreController(StartExpeditionInputBoundary start, MoveInputBoundary move, TravelInputBoundary travel,
                             CheckTemperatureInputBoundary check, ChooseHintInputBoundary hint,
                             GuessBuildingInputBoundary guess, ExplorePresenter presenter) {
        this.start = Objects.requireNonNull(start);
        this.move = Objects.requireNonNull(move);
        this.travel = Objects.requireNonNull(travel);
        this.check = Objects.requireNonNull(check);
        this.hint = Objects.requireNonNull(hint);
        this.guess = Objects.requireNonNull(guess);
        this.presenter = Objects.requireNonNull(presenter);
    }

    public synchronized ExploreViewModel handle(String action, Map<String, Object> fields) {
        try {
            switch (action) {
                case "start" -> start.execute(startRequest(fields));
                case "move" -> move.execute(round(fields), path(fields.get("path")));
                case "travel" -> travel.execute(round(fields), path(List.of(fields.get("to"))).getFirst());
                case "check" -> check.execute(round(fields));
                case "hint" -> hint.execute(round(fields), level(fields.get("level")));
                case "guess" -> guess.execute(round(fields), integer(fields, "buildingId"));
                default -> {
                    return new ExploreViewModel(404, Map.of("type", "error", "error", "Unknown action."));
                }
            }
        } catch (IllegalArgumentException exception) {
            return new ExploreViewModel(400, Map.of("type", "error", "error", exception.getMessage()));
        }
        return presenter.viewModel();
    }

    private static StartRequest startRequest(Map<String, Object> fields) {
        String mode = fields.get("mode") instanceof String text ? text.toUpperCase(Locale.ROOT) : "UNLIMITED";
        StartRequest.Mode chosen;
        try {
            chosen = StartRequest.Mode.valueOf(mode);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Choose daily or unlimited.");
        }
        return new StartRequest(fields.get("campus") instanceof String campus ? campus : null, chosen,
                Difficulty.named(fields.get("difficulty") instanceof String name ? name : Difficulty.NORMAL.name()));
    }

    /** A path is a list of [longitude, latitude] pairs, as GeoJSON writes them. */
    private static List<GeoPoint> path(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty() || items.size() > MAX_PATH_POINTS) {
            throw new IllegalArgumentException("Missing or invalid path.");
        }
        List<GeoPoint> points = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof List<?> pair) || pair.size() != 2
                    || !(pair.get(0) instanceof Number lon) || !(pair.get(1) instanceof Number lat)) {
                throw new IllegalArgumentException("Path points must be [longitude, latitude].");
            }
            points.add(new GeoPoint(lat.doubleValue(), lon.doubleValue()));
        }
        return points;
    }

    private static UUID round(Map<String, Object> fields) {
        if (fields.get("round") instanceof String id) {
            try {
                return UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                // Fall through.
            }
        }
        throw new IllegalArgumentException("Missing or invalid round.");
    }

    private static HintLevel level(Object value) {
        if (value instanceof String text) {
            try {
                return HintLevel.valueOf(text.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through.
            }
        }
        throw new IllegalArgumentException("Choose easy, normal, or hard.");
    }

    private static double number(Map<String, Object> fields, String name) {
        if (fields.get(name) instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Missing or invalid " + name + ".");
    }

    private static int integer(Map<String, Object> fields, String name) {
        double value = number(fields, name);
        if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid " + name + ".");
        }
        return (int) value;
    }
}
