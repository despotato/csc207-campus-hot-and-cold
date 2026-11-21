package campus.application;

import campus.adapters.StartRoundController;
import campus.adapters.StartRoundPresenter;
import campus.domain.Campus;
import campus.domain.Landmark;
import campus.domain.Round;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StartRoundInteractorTest {
    private static final List<Landmark> LANDMARKS = java.util.stream.IntStream.rangeClosed(1, 8)
            .mapToObj(i -> new Landmark(i, "Building " + i, 43.66 + i * 0.001, -79.4)).toList();
    private GameSession session;
    private StartRoundPresenter presenter;

    @BeforeEach
    void setUp() {
        session = new GameSession();
        presenter = new StartRoundPresenter();
    }

    private StartRoundInteractor interactor(LandmarkGateway gateway) {
        return new StartRoundInteractor(gateway, new Random(13), session, presenter);
    }

    @Test
    void controllerStartsRoundThroughInputPortAndPresentsCandidates() {
        var seen = new ArrayList<Campus>();
        var useCase = interactor(campus -> { seen.add(campus); return LANDMARKS; });
        new StartRoundController(useCase).start(Campus.ST_GEORGE);
        assertEquals(List.of(Campus.ST_GEORGE), seen);
        assertEquals(LANDMARKS, session.currentRound().landmarks());
        assertTrue(presenter.viewModel().successful());
        assertEquals(8, presenter.viewModel().candidates().size());
        assertTrue(presenter.viewModel().status().contains("6 guesses left"));
        assertEquals(6, session.currentRound().guessesRemaining());
        assertTrue(session.currentRound().revealedLandmark().isEmpty());
    }

    @Test
    void startsAgainWithFreshGuessHistory() {
        var useCase = interactor(campus -> LANDMARKS);
        useCase.execute(Campus.ST_GEORGE);
        Round first = session.currentRound();
        first.submitGuess(LANDMARKS.getFirst().id());
        useCase.execute(Campus.ST_GEORGE);
        assertNotSame(first, session.currentRound());
        assertTrue(session.currentRound().guesses().isEmpty());
        assertEquals(6, session.currentRound().guessesRemaining());
    }

    @Test
    void acceptsExactlySixUniqueLandmarks() {
        interactor(campus -> LANDMARKS.subList(0, 6)).execute(Campus.ST_GEORGE);
        assertTrue(presenter.viewModel().successful());
        assertEquals(6, presenter.viewModel().candidates().size());
    }

    @Test
    void tooFewLandmarksPreservesPreviousRoundAndReportsRetry() {
        interactor(campus -> LANDMARKS).execute(Campus.ST_GEORGE);
        Round first = session.currentRound();
        interactor(campus -> LANDMARKS.subList(0, 5)).execute(Campus.ST_GEORGE);
        assertSame(first, session.currentRound());
        assertFalse(presenter.viewModel().successful());
        assertTrue(presenter.viewModel().status().contains("retry"));
        assertTrue(presenter.viewModel().candidates().isEmpty());
    }

    @Test
    void networkFailureDoesNotInstallPartialRound() {
        interactor(campus -> { throw new IOException("internal server details"); })
                .execute(Campus.ST_GEORGE);
        assertFalse(presenter.viewModel().successful());
        assertThrows(IllegalStateException.class, session::currentRound);
        assertFalse(presenter.viewModel().status().contains("internal server details"));
    }

    @Test
    void networkFailureKeepsExistingRoundAndGuesses() {
        interactor(campus -> LANDMARKS).execute(Campus.ST_GEORGE);
        Round first = session.currentRound();
        first.submitGuess(1);
        interactor(campus -> { throw new IOException("offline"); }).execute(Campus.ST_GEORGE);
        assertSame(first, session.currentRound());
        assertEquals(1, first.guesses().size());
    }

    @Test
    void interruptionRestoresThreadInterruptFlag() {
        try {
            interactor(campus -> { throw new InterruptedException(); }).execute(Campus.ST_GEORGE);
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(presenter.viewModel().successful());
            assertThrows(IllegalStateException.class, session::currentRound);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void nullCampusIsRejectedBeforeFetching() {
        AtomicInteger calls = new AtomicInteger();
        interactor(campus -> { calls.incrementAndGet(); return LANDMARKS; }).execute(null);
        assertEquals(0, calls.get());
        assertFalse(presenter.viewModel().successful());
        assertThrows(IllegalStateException.class, session::currentRound);
    }

    @Test
    void duplicateCanonicalIdsAreNotAValidCampusPack() {
        var duplicate = new ArrayList<>(LANDMARKS);
        duplicate.set(7, LANDMARKS.getFirst());
        interactor(campus -> duplicate).execute(Campus.ST_GEORGE);
        assertFalse(presenter.viewModel().successful());
        assertThrows(IllegalStateException.class, session::currentRound);
    }

    @Test
    void failureCanBeRetriedSuccessfully() {
        interactor(campus -> List.of()).execute(Campus.ST_GEORGE);
        assertFalse(presenter.viewModel().successful());
        interactor(campus -> LANDMARKS).execute(Campus.ST_GEORGE);
        assertTrue(presenter.viewModel().successful());
    }

    @Test
    void responseAndViewModelTakeImmutableCopies() {
        var source = new ArrayList<>(List.of(new StartRoundResponse.Candidate(1, "Robarts")));
        var response = new StartRoundResponse("Test campus", source, 6);
        source.clear();
        presenter.present(response);
        assertEquals(1, response.candidates().size());
        assertThrows(UnsupportedOperationException.class, () -> presenter.viewModel().candidates().clear());
        // The start DTO has no target, coordinates, Round, or exact-distance field.
        assertEquals(List.of("campus", "candidates", "guessesRemaining"),
                java.util.Arrays.stream(StartRoundResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void selectedSecretCanBeWonAndRoundCopiesTheLoadedList() {
        var loaded = new ArrayList<>(LANDMARKS);
        var useCase = new StartRoundInteractor(campus -> loaded, new Random(13), session, presenter);
        long expectedTarget = LANDMARKS.get(new Random(13).nextInt(LANDMARKS.size())).id();
        useCase.execute(Campus.ST_GEORGE);
        loaded.clear();
        session.currentRound().submitGuess(expectedTarget);
        assertTrue(session.currentRound().isWon());
        assertEquals(6, session.currentRound().score());
        assertEquals(8, session.currentRound().landmarks().size());
    }
}
