package campus.application;

import campus.adapters.SubmitGuessController;
import campus.adapters.SubmitGuessPresenter;
import campus.domain.Campus;
import campus.domain.Guess;
import campus.domain.Landmark;
import campus.domain.Round;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SubmitGuessInteractorTest {
    private static final Landmark TARGET = atDistance(99, "Target", 0);

    @Test
    void validGuessUsesControllerAndPresentsOnlyPublicFeedback() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 100));

        harness.controller.submit(1);

        var model = harness.presenter.viewModel();
        assertTrue(model.guessAccepted());
        assertFalse(model.completed());
        assertEquals(5, model.guessesRemaining());
        assertEquals("1. Landmark 1 �� Cold", model.feedbackLines().getFirst());
        assertEquals(7, model.candidates().size());
        assertNull(model.revealedAnswer());
        var response = harness.capture.response;
        assertNull(response.answer());
        assertTrue(response.feedback().stream().allMatch(item -> item.distanceMetres() == null));
        assertEquals(1, harness.round.guesses().size());
    }

    @Test
    void duplicateGuessIsRejectedWithoutConsumingATurn() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 100));
        harness.controller.submit(1);
        harness.controller.submit(1);

        assertFalse(harness.presenter.viewModel().guessAccepted());
        assertEquals("You already guessed that landmark.", harness.presenter.viewModel().status());
        assertEquals(5, harness.round.guessesRemaining());
        assertEquals(1, harness.round.guesses().size());
    }

    @Test
    void unknownIdIsRejectedWithoutConsumingATurn() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 100));
        harness.controller.submit(12345);

        assertFalse(harness.presenter.viewModel().guessAccepted());
        assertEquals("Unknown landmark.", harness.presenter.viewModel().status());
        assertEquals(6, harness.round.guessesRemaining());
        assertTrue(harness.round.guesses().isEmpty());
    }

    @Test
    void exactHeatBandBoundariesAreApplied() {
        assertHeat(99.99, Guess.Heat.HOT);
        assertHeat(100, Guess.Heat.WARM);
        assertHeat(249.99, Guess.Heat.WARM);
        assertHeat(250, Guess.Heat.COOL);
        assertHeat(499.99, Guess.Heat.COOL);
        assertHeat(500, Guess.Heat.COLD);
    }

    @Test
    void warmerColderAndSameUseOneMetreToleranceAcrossBandChanges() {
        var harness = harness(roundWithDistances(500.5, 500.0, 498.9, 500.0, 900, 800, 0));
        harness.controller.submit(1);
        assertEquals(Guess.Trend.FIRST, harness.round.guesses().getFirst().trend());

        harness.controller.submit(2);
        assertEquals(Guess.Trend.SAME, harness.round.guesses().get(1).trend());

        harness.controller.submit(3);
        assertEquals(Guess.Trend.WARMER, harness.round.guesses().get(2).trend());

        harness.controller.submit(4);
        assertEquals(Guess.Trend.COLDER, harness.round.guesses().get(3).trend());
    }

    @Test
    void correctGuessWinsAndScoresSevenMinusAttempts() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 0));
        harness.controller.submit(1);
        harness.controller.submit(TARGET.id());

        var model = harness.presenter.viewModel();
        assertTrue(model.completed());
        assertTrue(model.won());
        assertEquals(5, model.score());
        assertEquals(TARGET.name(), model.revealedAnswer());
        assertTrue(harness.capture.response.feedback().stream()
                .allMatch(item -> item.distanceMetres() != null));
        assertEquals(0.0, harness.capture.response.feedback().getLast().distanceMetres(), 1e-9);
    }

    @Test
    void sixthIncorrectGuessEndsRoundWithZeroScoreAndRevealsRecap() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 100));
        for (long id = 1; id <= 6; id++) {
            harness.controller.submit(id);
        }

        var model = harness.presenter.viewModel();
        assertTrue(model.completed());
        assertFalse(model.won());
        assertEquals(0, model.guessesRemaining());
        assertEquals(0, model.score());
        assertEquals(TARGET.name(), model.revealedAnswer());
        assertEquals(6, harness.capture.response.feedback().size());
        assertTrue(harness.capture.response.feedback().stream()
                .allMatch(item -> item.distanceMetres() != null));
    }

    @Test
    void guessesAfterCompletionAreRejectedWithoutChangingRound() {
        var harness = harness(roundWithDistances(700, 600, 500, 400, 300, 200, 0));
        harness.controller.submit(TARGET.id());
        harness.controller.submit(1);

        assertFalse(harness.presenter.viewModel().guessAccepted());
        assertTrue(harness.presenter.viewModel().completed());
        assertEquals("This round is already complete.", harness.presenter.viewModel().status());
        assertEquals(1, harness.round.guesses().size());
    }

    @Test
    void noRoundProducesARecoverableFailure() {
        var presenter = new SubmitGuessPresenter();
        var controller = new SubmitGuessController(new SubmitGuessInteractor(new GameSession(), presenter));

        controller.submit(1);

        assertFalse(presenter.viewModel().guessAccepted());
        assertEquals("Start a round first.", presenter.viewModel().status());
    }

    private static void assertHeat(double distance, Guess.Heat expected) {
        var harness = harness(roundWithDistances(distance, 800, 700, 600, 500, 400, 0));
        harness.controller.submit(1);
        assertEquals(expected, harness.round.guesses().getFirst().heat(), "distance=" + distance);
        assertEquals(distance, harness.round.guesses().getFirst().distanceMetres(), 1e-6);
    }

    private static List<Landmark> roundWithDistances(double... distances) {
        List<Landmark> landmarks = new ArrayList<>();
        for (int index = 0; index < distances.length; index++) {
            long id = index + 1;
            landmarks.add(atDistance(id, "Landmark " + id, distances[index]));
        }
        landmarks.add(TARGET);
        return landmarks;
    }

    private static Landmark atDistance(long id, String name, double metres) {
        double angle = 2 * Math.asin(metres / (2 * 6_371_000));
        return new Landmark(id, name, 0, Math.toDegrees(angle));
    }

    private static Harness harness(List<Landmark> landmarks) {
        Landmark target = landmarks.getLast();
        var round = new Round(Campus.ST_GEORGE, landmarks, target);
        var session = new GameSession();
        session.start(round);
        var presenter = new SubmitGuessPresenter();
        var capture = new Capture();
        var interactor = new SubmitGuessInteractor(session, new SubmitGuessOutputBoundary() {
            @Override
            public void present(SubmitGuessResponse response) {
                capture.response = response;
                presenter.present(response);
            }

            @Override
            public void presentFailure(String message) {
                capture.response = null;
                presenter.presentFailure(message);
            }
        });
        return new Harness(round, new SubmitGuessController(interactor), presenter, capture);
    }

    private static final class Capture {
        private SubmitGuessResponse response;
    }

    private record Harness(Round round, SubmitGuessController controller, SubmitGuessPresenter presenter,
                           Capture capture) { }
}

