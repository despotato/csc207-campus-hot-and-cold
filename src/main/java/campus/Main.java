package campus;

import campus.adapters.SavedResultsController;
import campus.adapters.SavedResultsPresenter;
import campus.adapters.StartRoundController;
import campus.adapters.StartRoundPresenter;
import campus.application.GameSession;
import campus.application.SavedResultsInteractor;
import campus.application.StartRoundInteractor;
import campus.domain.Campus;
import campus.infrastructure.BrowserLandmarkMapGateway;
import campus.infrastructure.FileRoundRepository;
import campus.infrastructure.WikipediaLandmarkGateway;
import campus.ui.CampusGame;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.random.RandomGenerator;

/** Composition root: the only place that wires UI, adapters, use cases, and infrastructure together. */
public final class Main {
    private Main() { }

    public static void main(String[] args) throws IOException, InterruptedException {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        var gateway = new WikipediaLandmarkGateway(client);
        if (args.length == 1 && "--check-api".equals(args[0])) {
            var landmarks = gateway.load(Campus.ST_GEORGE);
            System.out.println("Loaded " + landmarks.size() + " landmarks from the live Wikipedia API.");
            landmarks.forEach(item -> System.out.printf(Locale.ROOT, "%s: %.6f, %.6f%n",
                    item.name(), item.latitude(), item.longitude()));
            return;
        }
        if (args.length != 0) {
            System.err.println("Usage: campus.Main [--check-api]");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("A desktop session is required. Use --check-api to verify the API.");
            return;
        }
        var session = new GameSession();
        var presenter = new StartRoundPresenter();
        var interactor = new StartRoundInteractor(gateway, RandomGenerator.getDefault(), session, presenter);
        var controller = new StartRoundController(interactor);
        var resultsPresenter = new SavedResultsPresenter();
        var resultsDirectory = Path.of(System.getProperty("user.home"), ".campus-hot-and-cold", "rounds");
        var results = new SavedResultsController(new SavedResultsInteractor(session,
                new FileRoundRepository(resultsDirectory), new BrowserLandmarkMapGateway(), resultsPresenter));
        SwingUtilities.invokeLater(() ->
                new CampusGame(controller, presenter, session, results, resultsPresenter).show());
    }
}
