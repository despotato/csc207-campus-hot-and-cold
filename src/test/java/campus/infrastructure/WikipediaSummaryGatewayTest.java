package campus.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaSummaryGatewayTest {
    private HttpServer server;
    private final List<String> paths = new ArrayList<>();
    private WikipediaSummaryGateway gateway;

    @BeforeEach void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/summary/", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            paths.add(path);
            String body = switch (path) {
                case "/summary/Robarts_Library" -> "{\"title\":\"Robarts Library\",\"extract\":\"A library.\","
                        + "\"thumbnail\":{\"source\":\"https://upload.example/r.jpg\"},"
                        + "\"content_urls\":{\"desktop\":{\"page\":\"https://en.wikipedia.org/wiki/Robarts_Library\"}}}";
                case "/summary/Bad" -> "{not json";
                case "/summary/Sneaky" -> "{\"extract\":\"x\",\"thumbnail\":{\"source\":\"javascript:alert(1)\"}}";
                default -> null;
            };
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : bytes.length);
            if (body != null) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        gateway = new WikipediaSummaryGateway(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/summary/"));
    }

    @AfterEach void stop() {
        server.stop(0);
    }

    @Test void readsTitleExtractImageAndLink() {
        var summary = gateway.summary("Robarts Library").orElseThrow();
        assertEquals("Robarts Library", summary.title());
        assertEquals("A library.", summary.extract());
        assertEquals("https://upload.example/r.jpg", summary.imageUrl());
        assertEquals("https://en.wikipedia.org/wiki/Robarts_Library", summary.pageUrl());
        assertEquals(List.of("/summary/Robarts_Library"), paths);
    }

    @Test void failuresAndUnsafeLinksDegradeQuietly() {
        assertTrue(gateway.summary("Missing").isEmpty());
        assertTrue(gateway.summary("Bad").isEmpty());
        assertTrue(gateway.summary(null).isEmpty());
        assertNull(gateway.summary("Sneaky").orElseThrow().imageUrl());
    }
}
