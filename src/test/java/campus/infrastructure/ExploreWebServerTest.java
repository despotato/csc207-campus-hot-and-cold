package campus.infrastructure;

import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExplorePresenter;
import campus.application.explore.CheckTemperatureInteractor;
import campus.application.explore.ChooseHintInteractor;
import campus.application.explore.ExpeditionSession;
import campus.application.explore.GuessBuildingInteractor;
import campus.application.explore.LandmarkInfoGateway;
import campus.application.explore.MoveInteractor;
import campus.application.explore.StartExpeditionInteractor;
import campus.domain.explore.TestCampus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ExploreWebServerTest {
    private ExploreWebServer server;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void start() throws Exception {
        var session = new ExpeditionSession();
        var presenter = new ExplorePresenter();
        var clock = Clock.systemUTC();
        var controller = new ExploreController(
                new StartExpeditionInteractor(TestCampus.map(), new Random(1), clock, session, presenter),
                new MoveInteractor(clock, session, presenter), new CheckTemperatureInteractor(session, presenter),
                new ChooseHintInteractor(session, presenter),
                new GuessBuildingInteractor(session, LandmarkInfoGateway.NONE, presenter), presenter);
        server = new ExploreWebServer(controller, "{\"version\":2}", 0, null);
    }

    @AfterEach void stop() {
        server.close();
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder post(String action, String body) {
        return HttpRequest.newBuilder(server.address().resolve("/api/" + action))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
    }

    @Test void servesThePageAndPackOnLoopback() throws Exception {
        assertEquals("127.0.0.1", server.address().getHost());
        var page = send(HttpRequest.newBuilder(server.address()));
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Campus Hot"));
        var pack = send(HttpRequest.newBuilder(server.address().resolve("/pack.json")));
        assertEquals("{\"version\":2}", pack.body());
        assertEquals(404, send(HttpRequest.newBuilder(server.address().resolve("/secret"))).statusCode());
        var debug = send(HttpRequest.newBuilder(server.address().resolve("/api/debug")));
        assertNotEquals(200, debug.statusCode(), "debug info is off unless explicitly enabled");
        assertFalse(debug.body().contains("zone"));
    }

    @Test void playsThroughTheJsonApi() throws Exception {
        var started = send(post("start", "{}"));
        assertEquals(200, started.statusCode());
        assertEquals("started", Json.object(Json.parse(started.body())).get("type"));
        var checked = send(post("check", ""));
        assertEquals("checked", Json.object(Json.parse(checked.body())).get("type"));
    }

    @Test void rejectsCrossSiteAndMalformedRequests() throws Exception {
        assertEquals(403, send(post("start", "{}").header("Origin", "https://evil.example")).statusCode());
        assertEquals(403, send(HttpRequest.newBuilder(server.address().resolve("/api/start"))
                .header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{}"))).statusCode(),
                "simple cross-site form posts can't reach the API");
        assertEquals(405, send(HttpRequest.newBuilder(server.address().resolve("/api/start"))).statusCode());
        assertEquals(400, send(post("start", "{not json")).statusCode());
        assertEquals(413, send(post("start", "{\"x\":\"" + "a".repeat(20_000) + "\"}")).statusCode());
        var raw = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.address().getPort() + "/"));
        assertEquals(200, send(raw).statusCode());
    }

    @Test void rejectsForeignHostHeaders() throws Exception {
        // DNS rebinding sends a foreign Host header to the local port.
        try (var socket = new java.net.Socket("127.0.0.1", server.address().getPort())) {
            socket.getOutputStream().write(("GET / HTTP/1.1\r\nHost: evil.example\r\nConnection: close\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            String status = new String(socket.getInputStream().readNBytes(12), java.nio.charset.StandardCharsets.US_ASCII);
            assertEquals("HTTP/1.1 403", status);
        }
    }

}
