package campus.adapters.explore;

import campus.application.explore.CheckTemperatureInputBoundary;
import campus.application.explore.ChooseHintInputBoundary;
import campus.application.explore.GuessBuildingInputBoundary;
import campus.application.explore.MoveInputBoundary;
import campus.application.explore.StartExpeditionInputBoundary;
import campus.domain.explore.Difficulty;
import campus.domain.explore.Expedition.HintLevel;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Converts raw request fields from the browser into use-case calls. Returns the presenter's
 * resulting view model. Calls are synchronized because the game state is shared.
 */
public final class ExploreController {
    private final StartExpeditionInputBoundary start;
    private final MoveInputBoundary move;
    private final CheckTemperatureInputBoundary check;
    private final ChooseHintInputBoundary hint;
    private final GuessBuildingInputBoundary guess;
    private final ExplorePresenter presenter;

    public ExploreController(StartExpeditionInputBoundary start, MoveInputBoundary move,
                             CheckTemperatureInputBoundary check, ChooseHintInputBoundary hint,
                             GuessBuildingInputBoundary guess, ExplorePresenter presenter) {
        this.start = Objects.requireNonNull(start);
        this.move = Objects.requireNonNull(move);
        this.check = Objects.requireNonNull(check);
        this.hint = Objects.requireNonNull(hint);
        this.guess = Objects.requireNonNull(guess);
        this.presenter = Objects.requireNonNull(presenter);
    }

    public synchronized ExploreViewModel handle(String action, Map<String, Object> fields) {
        try {
            switch (action) {
                case "start" -> start.execute(Difficulty.named(
                        fields.get("difficulty") instanceof String name ? name : Difficulty.NORMAL.name()));
                case "move" -> move.execute(integer(fields, "from"), integer(fields, "to"), number(fields, "fraction"));
                case "check" -> check.execute();
                case "hint" -> hint.execute(level(fields.get("level")));
                case "guess" -> guess.execute(integer(fields, "buildingId"));
                default -> {
                    return new ExploreViewModel(404, Map.of("type", "error", "error", "Unknown action."));
                }
            }
        } catch (IllegalArgumentException exception) {
            return new ExploreViewModel(400, Map.of("type", "error", "error", exception.getMessage()));
        }
        return presenter.viewModel();
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
