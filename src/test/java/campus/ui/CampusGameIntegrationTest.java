package campus.ui;

import campus.adapters.*;
import campus.application.*;
import campus.domain.*;
import campus.infrastructure.FileRoundRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises real Swing controls and asynchronous workers without external services. */
class CampusGameIntegrationTest {
    @TempDir Path directory;
    private final List<Landmark> landmarks = LongStream.rangeClosed(1, 7)
            .mapToObj(id -> new Landmark(id, "Landmark " + id, 43.66 + id * .001, -79.39)).toList();
    private final List<Landmark> mapped = new ArrayList<>();
    private boolean loadFails;

    @BeforeEach void requireDisplay() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Desktop test requires a display or xvfb-run");
    }

    @AfterEach void closeWindows() throws Exception {
        if (!GraphicsEnvironment.isHeadless()) {
            onEdt(() -> { for (Window window : Window.getWindows()) window.dispose(); return null; });
        }
    }

    @Test void winSavesAndHistorySurvivesANewAppSession() throws Exception {
        Path path = directory.resolve("rounds");
        CampusGame game = launch(path);
        start(game);
        // Random returns zero, so Landmark 1 is the deterministic target.
        choose(game, 2);
        click(game, "submit");
        assertTrue(text(game, "history").contains("Landmark 2"));
        assertFalse(text(game, "history").contains("Answer:"));
        click(game, "map");
        await(() -> enabled(game, "submit"));
        assertEquals(2, mapped.getLast().id());
        choose(game, 1);
        click(game, "submit");
        await(() -> enabled(game, "start"));
        assertFalse(enabled(game, "submit"));
        var repository = new FileRoundRepository(path);
        assertEquals(1, repository.list().size());
        assertEquals(5, repository.list().getFirst().score());
        assertTrue(text(game, "history").contains("Answer: Landmark 1"));
        click(game, "map");
        await(() -> enabled(game, "start"));
        assertEquals(1, mapped.getLast().id());

        CampusGame restarted = launch(path);
        click(restarted, "browseHistory");
        Object panel = onEdt(() -> field(restarted, "savedRounds"));
        await(() -> enabled(panel, "reopen"));
        click(panel, "reopen");
        await(() -> enabled(panel, "map"));
        assertTrue(text(panel, "recap").contains("Answer: Landmark 1"));
        click(panel, "map");
        await(() -> enabled(panel, "back"));
        assertEquals(1, mapped.getLast().id());
        click(panel, "back");
        assertTrue(enabled(restarted, "start"));
    }

    @Test void sixIncorrectGuessesEndRoundAndAllowAnotherRound() throws Exception {
        CampusGame game = launch(directory.resolve("rounds"));
        start(game);
        for (long id = 2; id <= 7; id++) {
            choose(game, id);
            click(game, "submit");
        }
        await(() -> enabled(game, "start"));
        assertFalse(enabled(game, "submit"));
        var result = new FileRoundRepository(directory.resolve("rounds")).list().getFirst();
        assertFalse(result.won());
        assertEquals(0, result.score());
        assertEquals(6, result.guesses().size());
        assertTrue(text(game, "history").contains("Answer: Landmark 1"));
        start(game);
        assertEquals("", text(game, "history"));
        assertTrue(enabled(game, "submit"));
        assertFalse(enabled(game, "map"));
    }

    @Test void saveFailurePreservesRecapAndRetrySavesExactlyOnce() throws Exception {
        Path blocked = Files.writeString(directory.resolve("rounds"), "blocked");
        CampusGame game = launch(blocked);
        start(game);
        choose(game, 1);
        click(game, "submit");
        await(() -> enabled(game, "retrySave"));
        assertFalse(enabled(game, "start"));
        assertTrue(text(game, "history").contains("Answer: Landmark 1"));
        String originalRecap = text(game, "history");
        Files.delete(blocked);
        click(game, "retrySave");
        await(() -> enabled(game, "start"));
        assertEquals(1, new FileRoundRepository(blocked).list().size());
        assertEquals(originalRecap, text(game, "history"));
    }

    @Test void failedStartKeepsExistingRoundAndCanBeRetried() throws Exception {
        CampusGame game = launch(directory.resolve("rounds"));
        start(game);
        choose(game, 2);
        click(game, "submit");
        String previous = text(game, "history");
        loadFails = true;
        click(game, "start");
        await(() -> enabled(game, "start"));
        assertTrue(text(game, "status").contains("retry"));
        assertEquals(previous, text(game, "history"));
        assertTrue(enabled(game, "submit"));
        choose(game, 1);
        click(game, "submit");
        await(() -> enabled(game, "start"));
        assertTrue(text(game, "history").contains("Answer: Landmark 1"));
        loadFails = false;
        start(game);
        assertEquals("", text(game, "history"));
    }

    private CampusGame launch(Path path) throws Exception {
        var session = new GameSession();
        var startPresenter = new StartRoundPresenter();
        var random = new Random() {
            @Override public int nextInt(int bound) { return 0; }
        };
        var start = new StartRoundController(new StartRoundInteractor(campus -> {
            if (loadFails) throw new IOException("offline");
            return landmarks;
        }, random, session, startPresenter));
        var guessPresenter = new SubmitGuessPresenter();
        var guess = new SubmitGuessController(new SubmitGuessInteractor(session, guessPresenter));
        var resultsPresenter = new SavedResultsPresenter();
        var results = new SavedResultsController(new SavedResultsInteractor(session,
                new FileRoundRepository(path), mapped::add, resultsPresenter));
        return onEdt(() -> {
            var game = new CampusGame(start, startPresenter, guess, guessPresenter, results, resultsPresenter);
            game.show();
            return game;
        });
    }

    private void start(CampusGame game) throws Exception {
        click(game, "start");
        await(() -> enabled(game, "submit"));
    }

    private void choose(CampusGame game, long id) throws Exception {
        onEdt(() -> {
            JComboBox<?> candidates = (JComboBox<?>) field(game, "candidates");
            for (int i = 0; i < candidates.getItemCount(); i++) {
                var candidate = (StartRoundResponse.Candidate) candidates.getItemAt(i);
                if (candidate.id() == id) { candidates.setSelectedIndex(i); return null; }
            }
            fail("Candidate missing: " + id);
            return null;
        });
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void click(Object owner, String name) throws Exception {
        onEdt(() -> {
            var button = (JButton) field(owner, name);
            assertTrue(button.isEnabled(), name + " should be enabled");
            button.doClick(0);
            return null;
        });
    }

    private static boolean enabled(Object owner, String name) throws Exception {
        return onEdt(() -> ((JComponent) field(owner, name)).isEnabled());
    }

    private static String text(Object owner, String name) throws Exception {
        return onEdt(() -> {
            Object component = field(owner, name);
            return component instanceof JLabel label ? label.getText() : ((JTextArea) component).getText();
        });
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!condition.call()) {
            if (System.nanoTime() > deadline) fail("Timed out waiting for Swing worker");
            Thread.sleep(20);
        }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) return action.call();
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
