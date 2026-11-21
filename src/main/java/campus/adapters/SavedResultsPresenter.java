package campus.adapters;

import campus.application.SavedResultsOutputBoundary;
import campus.domain.CompletedRoundResult;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SavedResultsPresenter implements SavedResultsOutputBoundary {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("MMM d, uuuu HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    private SavedResultsViewModel viewModel = new SavedResultsViewModel.History(List.of());

    @Override
    public void presentRecap(CompletedRoundResult result, boolean retrySave) {
        StringBuilder text = new StringBuilder("Answer: ").append(result.answer().name()).append('\n');
        List<SavedResultsViewModel.MapChoice> destinations = new ArrayList<>();
        long answerId = result.answer().id();
        destinations.add(new SavedResultsViewModel.MapChoice(answerId, "Answer: " + result.answer().name()));
        for (int i = 0; i < result.guesses().size(); i++) {
            var guess = result.guesses().get(i);
            text.append(i + 1).append(". ").append(guess.landmark().name()).append(" — ")
                    .append(Math.round(guess.distanceMetres())).append(" m\n");
            if (guess.landmark().id() != answerId) {
                destinations.add(new SavedResultsViewModel.MapChoice(guess.landmark().id(),
                        "Guess " + (i + 1) + ": " + guess.landmark().name()));
            }
        }
        viewModel = new SavedResultsViewModel.Recap(result.id(), outcome(result), text.toString(),
                destinations, retrySave);
    }

    @Override
    public void presentHistory(List<CompletedRoundResult> results) {
        viewModel = new SavedResultsViewModel.History(results.stream()
                .map(result -> new SavedResultsViewModel.Entry(result.id(), TIME.format(result.completedAt())
                        + " — " + result.campus() + " — " + outcome(result)))
                .toList());
    }

    @Override
    public void presentStatus(String message) {
        viewModel = new SavedResultsViewModel.Status(message);
    }

    public SavedResultsViewModel viewModel() {
        return viewModel;
    }

    private static String outcome(CompletedRoundResult result) {
        return (result.won() ? "Solved" : "Round over") + " · Score: " + result.score();
    }
}
