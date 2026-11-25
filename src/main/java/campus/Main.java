package campus;

import campus.adapters.SavedResultsController;
import campus.adapters.SavedResultsPresenter;
import campus.adapters.StartRoundController;
import campus.adapters.StartRoundPresenter;
import campus.adapters.SubmitGuessController;
import campus.adapters.SubmitGuessPresenter;
import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExplorePresenter;
import campus.application.GameSession;
import campus.application.SavedResultsInteractor;
import campus.application.StartRoundInteractor;
import campus.application.SubmitGuessInteractor;
import campus.application.explore.CheckTemperatureInteractor;
import campus.application.explore.ChooseHintInteractor;
import campus.application.explore.ExpeditionResultRepository;
import campus.application.explore.ExpeditionSession;
import campus.application.explore.GuessBuildingInteractor;
import campus.application.explore.LandmarkClueGateway;
import campus.application.explore.MoveInteractor;
import campus.application.explore.StartExpeditionInteractor;
import campus.domain.Campus;
import campus.domain.explore.GeoPoint;
import campus.infrastructure.BrowserLandmarkMapGateway;
import campus.infrastructure.CampusPackLoader;
import campus.infrastructure.ExploreWebServer;
import campus.infrastructure.FileRoundRepository;
import campus.infrastructure.WikipediaLandmarkGateway;
import campus.infrastructure.WikipediaSummaryGateway;
import campus.ui.CampusGame;
import campus.ui.Launcher;

import javax.swing.SwingUtilities;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/** Composition root: the only place that wires UI, adapters, use cases, and infrastructure together. */
public final class Main {
    private Main() { }

    public static void main(String[] args) throws IOException, InterruptedException {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        if (args.length == 1 && "--check-api".equals(args[0])) {
            var landmarks = new WikipediaLandmarkGateway(client).load(Campus.ST_GEORGE);
            System.out.println("Loaded " + landmarks.size() + " landmarks from the live Wikipedia API.");
            landmarks.forEach(item -> System.out.printf(Locale.ROOT, "%s: %.6f, %.6f%n",
                    item.name(), item.latitude(), item.longitude()));
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: campus.Main [--check-api]");
            return;
        }
        ExploreWebServer explore = startExplore(client);
        System.out.println("Explore mode: " + explore.address());
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No desktop session: open the address above in a browser. Press Ctrl+C to stop.");
            return;
        }
        SwingUtilities.invokeLater(() -> new Launcher(explore.address(), Main::browse,
                () -> classicGame(client).show()).show());
    }

    /** Explore mode: the 3D browser game. Set -Dcampus.explore.port to fix the port. */
    static ExploreWebServer startExplore(HttpClient client) throws IOException {
        var map = CampusPackLoader.load(CampusPackLoader.ST_GEORGE);
        var session = new ExpeditionSession();
        var presenter = new ExplorePresenter();
        var clock = Clock.systemUTC();
        var controller = new ExploreController(
                new StartExpeditionInteractor(map, RandomGenerator.getDefault(), clock, session, presenter),
                new MoveInteractor(clock, session, presenter),
                new CheckTemperatureInteractor(session, presenter),
                // Team task: replace NONE with a Wikipedia clue gateway.
                new ChooseHintInteractor(session, LandmarkClueGateway.NONE, presenter),
                // Team task: replace DISABLED with a file-backed result repository.
                new GuessBuildingInteractor(session, new WikipediaSummaryGateway(client),
                        ExpeditionResultRepository.DISABLED, clock, presenter),
                presenter);
        int port = Integer.getInteger("campus.explore.port", 0);
        // Debug only: the hidden zone and a shortest route to it, so automated visual tests can walk there.
        Supplier<Map<String, Object>> debug = Boolean.getBoolean("campus.explore.debug") ? () ->
                session.current().map(round -> Map.<String, Object>of(
                        "zone", Map.of("centre", point(round.zoneForDiagnostics().centre()),
                                "radius", round.zoneForDiagnostics().radiusMetres()),
                        "route", map.openSpace().shortestRoute(round.position(), round.zoneForDiagnostics()::contains)
                                .map(route -> route.points().stream().map(Main::point).toList())
                                .orElse(List.of()))).orElse(Map.of()) : null;
        return new ExploreWebServer(controller, CampusPackLoader.raw(CampusPackLoader.ST_GEORGE), port, debug);
    }

    private static List<Double> point(GeoPoint point) {
        return List.of(point.longitude(), point.latitude());
    }

    /** Classic mode: the original six-guess Swing game. */
    static CampusGame classicGame(HttpClient client) {
        var gateway = new WikipediaLandmarkGateway(client);
        var session = new GameSession();
        var presenter = new StartRoundPresenter();
        var interactor = new StartRoundInteractor(gateway, RandomGenerator.getDefault(), session, presenter);
        var controller = new StartRoundController(interactor);
        var guessPresenter = new SubmitGuessPresenter();
        var guessController = new SubmitGuessController(new SubmitGuessInteractor(session, guessPresenter));
        var resultsPresenter = new SavedResultsPresenter();
        var resultsDirectory = Path.of(System.getProperty("user.home"), ".campus-hot-and-cold", "rounds");
        var results = new SavedResultsController(new SavedResultsInteractor(session,
                new FileRoundRepository(resultsDirectory), new BrowserLandmarkMapGateway(), resultsPresenter));
        return new CampusGame(controller, presenter, guessController, guessPresenter, results, resultsPresenter);
    }

    private static void browse(URI address) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IllegalStateException("Browser launch unavailable.");
            }
            Desktop.getDesktop().browse(address);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
