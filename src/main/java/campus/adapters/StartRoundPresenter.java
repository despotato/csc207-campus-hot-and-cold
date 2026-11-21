package campus.adapters;

import campus.application.StartRoundOutputBoundary;
import campus.application.StartRoundResponse;
import java.util.List;

/** Formats output without referencing Swing. Read after the worker completes. */
public final class StartRoundPresenter implements StartRoundOutputBoundary {
    private StartRoundViewModel viewModel = new StartRoundViewModel(false,
            "Choose a campus and start a round.", List.of());

    @Override
    public void present(StartRoundResponse response) {
        viewModel = new StartRoundViewModel(true, response.campus() + " — "
                + response.guessesRemaining() + " guesses left.", response.candidates());
    }

    @Override
    public void presentFailure(String message) {
        viewModel = new StartRoundViewModel(false, message, List.of());
    }

    public StartRoundViewModel viewModel() { return viewModel; }
}
