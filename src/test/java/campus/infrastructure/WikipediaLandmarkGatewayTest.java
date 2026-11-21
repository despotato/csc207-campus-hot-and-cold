package campus.infrastructure;

import campus.domain.Campus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaLandmarkGatewayTest {
    private HttpServer server;
    private WikipediaLandmarkGateway gateway;
    private String body;
    private int status;
    private String query;
    private String agent;

    @BeforeEach
    void setUp() throws IOException {
        body = "<api><query><pages/></query></api>";
        status = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/w/api.php", exchange -> {
            query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            agent = exchange.getRequestHeaders().getFirst("User-Agent");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var stream = exchange.getResponseBody()) { stream.write(bytes); }
        });
        server.start();
        gateway = new WikipediaLandmarkGateway(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/w/api.php"));
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    private String page(long id, String title, String coordinates) {
        return "<page pageid='" + id + "' title='" + title + "'><coordinates>" + coordinates
                + "</coordinates></page>";
    }

    @Test
    void requestsBatchTitlesRedirectsAndPrimaryCoordinates() throws Exception {
        gateway.load(new Campus("Test", List.of("Hart House (University of Toronto)", "A&amp;B")));
        assertTrue(query.contains("redirects=1"));
        assertTrue(query.contains("coprimary=primary"));
        assertTrue(query.contains("titles=Hart House (University of Toronto)|A&amp;B"));
        assertTrue(agent.startsWith("CampusHotAndCold/"));
    }

    @Test
    void selectsPrimaryEarthCoordinatesDeduplicatesAndSorts() throws Exception {
        String primary = "<co lat='43.66' lon='-79.4' primary='' globe='earth'/>";
        body = "<api><query><pages>" + page(2, "Zulu", primary)
                + page(1, "Alpha", "<co lat='0' lon='0' globe='earth'/>"
                + "<co lat='1' lon='2' primary='' globe='moon'/>" + primary)
                + page(2, "Zulu", primary) + "<page missing='' title='Missing'/>"
                + "</pages></query></api>";
        var landmarks = gateway.load(Campus.ST_GEORGE);
        assertEquals(List.of("Alpha", "Zulu"), landmarks.stream().map(item -> item.name()).toList());
        assertEquals(43.66, landmarks.getFirst().latitude());
    }

    @Test
    void ignoresLocationsWithoutEligibleCoordinates() throws Exception {
        body = "<api><query><pages>" + page(1, "Moon", "<co lat='1' lon='2' primary='' globe='moon'/>")
                + "</pages></query></api>";
        assertTrue(gateway.load(Campus.ST_GEORGE).isEmpty());
    }

    @Test
    void httpErrorIsReportedAsIoFailure() {
        status = 503;
        assertThrows(IOException.class, () -> gateway.load(Campus.ST_GEORGE));
    }

    @Test
    void apiErrorIsReportedAsIoFailure() {
        body = "<api><error code='badvalue'/></api>";
        assertThrows(IOException.class, () -> gateway.load(Campus.ST_GEORGE));
    }

    @Test
    void malformedXmlIsReportedAsIoFailure() {
        body = "<api><query>";
        assertThrows(IOException.class, () -> gateway.load(Campus.ST_GEORGE));
    }

    @Test
    void invalidCoordinatesAreRejected() {
        body = "<api><query><pages>" + page(1, "Invalid", "<co lat='NaN' lon='0' primary='' globe='earth'/>")
                + "</pages></query></api>";
        assertThrows(IOException.class, () -> gateway.load(Campus.ST_GEORGE));
    }

    @Test
    void externalEntityDocumentsAreRejected() {
        body = "<!DOCTYPE api [<!ENTITY test SYSTEM 'file:///nonexistent-campus-test'>]>"
                + "<api><query>&test;</query></api>";
        assertThrows(IOException.class, () -> gateway.load(Campus.ST_GEORGE));
    }
}
