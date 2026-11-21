package campus.application;

import campus.adapters.SavedResultsController;
import campus.adapters.SavedResultsPresenter;
import campus.adapters.SavedResultsViewModel;
import campus.domain.Campus;
import campus.domain.Landmark;
import campus.domain.Round;
import campus.infrastructure.FileRoundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class SavedResultsInteractorTest {
    @TempDir Path directory;
    private final GameSession session = new GameSession();
    private final SavedResultsPresenter presenter = new SavedResultsPresenter();
    private final List<Landmark> opened = new ArrayList<>();
    private final List<Landmark> landmarks = LongStream.rangeClosed(1, 7)
            .mapToObj(id -> new Landmark(id, "Landmark " + id, 43.66 + id * .001, -79.39)).toList();
    private FileRoundRepository repository;
    private SavedResultsController controller;

    @BeforeEach
    void wireUseCase() {
        repository = new FileRoundRepository(directory.resolve("rounds"));
        controller = new SavedResultsController(new SavedResultsInteractor(
                session, repository, opened::add, presenter));
    }

    @Test
    void activeRoundCannotBeSavedOrRevealed() throws IOException {
        Round round = start();
        round.submitGuess(2);
        controller.saveCurrentRound();

        assertInstanceOf(SavedResultsViewModel.Status.class, presenter.viewModel());
        assertTrue(repository.list().isEmpty());
        assertTrue(round.revealedLandmark().isEmpty());
    }

    @Test
    void savesASixGuessLossAndPresentsItsRecap() throws IOException {
        Round round = start();
        for (long id = 2; id <= 7; id++) {
            round.submitGuess(id);
        }
        controller.saveCurrentRound();

        var result = repository.list().getFirst();
        assertFalse(result.won());
        assertEquals(0, result.score());
        assertEquals("Test campus", result.campus());
        assertEquals(6, result.guesses().size());
        var recap = assertInstanceOf(SavedResultsViewModel.Recap.class, presenter.viewModel());
        assertTrue(recap.text().contains("Landmark 1"));
        assertTrue(recap.text().contains("111 m"));
        assertFalse(recap.retrySave());
    }

    @Test
    void failedSaveKeepsTheRecapAndRetryKeepsTheOriginalIdentityAndTime() throws IOException {
        Path blocked = Files.writeString(directory.resolve("rounds"), "blocked");
        Round round = start();
        round.submitGuess(1);
        var original = round.completedResult().orElseThrow();

        controller.saveCurrentRound();
        var failed = assertInstanceOf(SavedResultsViewModel.Recap.class, presenter.viewModel());
        assertTrue(failed.retrySave());
        assertTrue(failed.text().contains("Landmark 1"));

        Files.delete(blocked);
        controller.saveCurrentRound();
        controller.saveCurrentRound();
        assertEquals(List.of(original), repository.list());
    }

    @Test
    void browsingReopeningAndMappingHistoryPreserveTheCurrentRound() {
        Round previous = start();
        previous.submitGuess(1);
        controller.saveCurrentRound();
        Round current = start();
        current.submitGuess(4);

        controller.showHistory();
        var history = assertInstanceOf(SavedResultsViewModel.History.class, presenter.viewModel());
        var savedId = history.entries().getFirst().id();
        controller.reopen(savedId);
        var recap = assertInstanceOf(SavedResultsViewModel.Recap.class, presenter.viewModel());
        assertEquals(savedId, recap.id());
        controller.openSavedMap(savedId, 1);
        controller.showHistory();

        assertEquals(List.of(landmarks.getFirst()), opened);
        assertSame(current, session.currentRound());
        assertEquals(List.of(4L), current.guesses().stream().map(g -> g.landmark().id()).toList());
        assertEquals(5, current.guessesRemaining());
    }

    @Test
    void mapOnlyReceivesTheLatestGuessOrRevealedAnswerAndRejectsUnknownSavedDestinations() {
        Round round = start();
        controller.openCurrentMap();
        assertTrue(opened.isEmpty());

        round.submitGuess(2);
        controller.openCurrentMap();
        assertEquals(List.of(landmarks.get(1)), opened);

        round.submitGuess(1);
        controller.openCurrentMap();
        controller.saveCurrentRound();
        UUID id = round.completedResult().orElseThrow().id();
        controller.openSavedMap(id, 7);
        assertEquals(List.of(landmarks.get(1), landmarks.getFirst()), opened);
        assertFalse(assertInstanceOf(SavedResultsViewModel.Status.class, presenter.viewModel()).message().isBlank());

        controller.openSavedMap(id, 2);
        assertEquals(landmarks.get(1), opened.getLast());
    }

    @Test
    void missingSavedRoundAndBrowserFailureAreRecoverable() {
        controller.reopen(UUID.randomUUID());
        assertInstanceOf(SavedResultsViewModel.Status.class, presenter.viewModel());

        start().submitGuess(2);
        var failingMap = new SavedResultsController(new SavedResultsInteractor(
                session, repository, landmark -> { throw new IOException("browser unavailable"); }, presenter));
        assertDoesNotThrow(failingMap::openCurrentMap);
        assertTrue(assertInstanceOf(SavedResultsViewModel.Status.class, presenter.viewModel())
                .message().contains("map"));
    }

    private Round start() {
        Round round = new Round(new Campus("Test campus", List.of()), landmarks, landmarks.getFirst());
        session.start(round);
        return round;
    }
}
