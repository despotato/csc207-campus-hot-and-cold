package campus.application;

import campus.domain.Guess;
import campus.domain.Landmark;

import java.util.List;

/**
 * Output port for the 3D campus view. Implementations are best-effort: a missing or closed
 * viewer must never interrupt the game, so these methods do not throw.
 *
 * <p>The secret landmark is never passed to the viewer until {@link #reveal} is called.
 */
public interface CampusViewerGateway {
    /** A new round started; frame the campus around the given centre. */
    void showRound(String campusName, double centreLatitude, double centreLongitude, int guessLimit);

    /** A valid guess was made; fly to it and show its heat and trend. */
    void showGuess(int number, Landmark landmark, Guess.Heat heat, Guess.Trend trend, int guessesRemaining);

    /** The round ended; fly to the answer and show how far each guess was from it. */
    void reveal(Landmark answer, boolean won, int score, List<RevealedGuess> guesses);

    record RevealedGuess(Landmark landmark, double distanceMetres) { }

    /** A viewer that shows nothing, for tests and headless runs. */
    CampusViewerGateway NONE = new CampusViewerGateway() {
        @Override
        public void showRound(String campusName, double centreLatitude, double centreLongitude, int guessLimit) { }

        @Override
        public void showGuess(int number, Landmark landmark, Guess.Heat heat, Guess.Trend trend,
                              int guessesRemaining) { }

        @Override
        public void reveal(Landmark answer, boolean won, int score, List<RevealedGuess> guesses) { }
    };
}
