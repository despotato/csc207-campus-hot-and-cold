package campus.adapters;

import campus.application.SubmitGuessOutputBoundary;
import campus.application.SubmitGuessResponse;
import campus.domain.Guess;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Converts use-case output into display-ready strings without depending on Swing. */
public final class SubmitGuessPresenter implements SubmitGuessOutputBoundary {
    private SubmitGuessViewModel viewModel = new SubmitGuessViewModel(false, false, false, 0, 0,
            "Start a round first.", List.of(), List.of(), null);

    @Override
    public void present(SubmitGuessResponse response) {
        List<String> lines = IntStream.range(0, response.feedback().size()).mapToObj(index -> {
            var feedback = response.feedback().get(index);
            String trend = feedback.trend() == Guess.Trend.FIRST
                    ? "" : " �� " + title(feedback.trend().name());
            return (index + 1) + ". " + feedback.landmarkName()
                    + " �� " + title(feedback.heat().name()) + trend;
        }).toList();
        viewModel = new SubmitGuessViewModel(response.guessAccepted(), response.completed(), response.won(),
                response.guessesRemaining(), response.score(), response.status(), response.candidates(), lines,
                response.answer() == null ? null : response.answer().name());
    }

    @Override
    public void presentFailure(String message) {
        viewModel = new SubmitGuessViewModel(false, false, false, 0, 0, message,
                List.of(), List.of(), null);
    }

    public SubmitGuessViewModel viewModel() {
        return viewModel;
    }

    private static String title(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}

