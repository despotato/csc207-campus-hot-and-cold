package campus.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikipediaFactsGatewayTest {
    private HttpServer server;
    private WikipediaFactsGateway gateway;
    private final java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();

    private static final String ROBARTS = "The John P. Robarts Research Library, commonly referred to as Robarts Library, "
            + "is the main humanities library of the University of Toronto. It is on St. George Street. "
            + "Opened in 1973 and named for John Robarts, the 17th Premier of Ontario, the library holds 4.5 million items. "
            + "Ok. "
            + "Robarts was used for exterior shots of the prison setting in Resident Evil: Afterlife in 2010. "
            + "The building's construction began in 1968 on land once occupied by houses.";
    private static final String NEW_COLLEGE = "New College is a college of the University of Toronto.\nHistory\n"
            + "Wetmore Hall, built in 1969, was designed by the architects Fairfield and DuBois. "
            + "The college has a library.";

    @BeforeEach void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/w/api.php", exchange -> {
            requests.incrementAndGet();
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String body;
            if (query.contains("list=search")) {
                body = query.contains("Wetmore") ? "{\"query\":{\"search\":[{\"title\":\"New College\"}]}}"
                        : query.contains("Broken") ? "{oops" : "{\"query\":{\"search\":[]}}";
            } else if (query.contains("titles=Robarts Library")) {
                body = page(ROBARTS);
            } else if (query.contains("titles=New College")) {
                body = page(NEW_COLLEGE);
            } else {
                body = "{\"query\":{\"pages\":{\"-1\":{\"missing\":\"\"}}}}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        gateway = new WikipediaFactsGateway(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/w/api.php"));
    }

    private static String page(String text) {
        return "{\"query\":{\"pages\":{\"1\":{\"extract\":" + Json.write(text) + "}}}}";
    }

    @AfterEach void stop() {
        server.stop(0);
    }

    @Test void ownArticleFactsComeFirstWithTheNameBlanked() {
        List<String> facts = gateway.facts("Robarts Library", "Robarts Library");
        assertFalse(facts.isEmpty());
        assertTrue(facts.getFirst().startsWith("Opened in 1973 and named for John ____"), facts.getFirst());
        assertTrue(facts.stream().anyMatch(f -> f.contains("Resident Evil")), "obscure facts follow");
        assertTrue(facts.stream().noneMatch(f -> f.toLowerCase().contains("robarts")), "never names it");
        assertTrue(facts.stream().noneMatch(f -> f.length() < 45), "no fragments like 'Ok.'");
    }

    @Test void buildingsWithoutTheirOwnArticleUseMentionsElsewhere() {
        List<String> facts = gateway.facts(null, "Wetmore Hall");
        assertEquals(List.of("this building, built in 1969, was designed by the architects Fairfield and DuBois."), facts);
    }

    @Test void sentenceSplittingKeepsInitialsAndSaintTogether() {
        List<String> sentences = WikipediaFactsGateway.sentences(ROBARTS);
        assertTrue(sentences.getFirst().startsWith("The John P. Robarts Research Library"));
        assertEquals("It is on St. George Street.", sentences.get(1));
    }

    @Test void eachLandmarkIsFetchedOnceAndHeadingsAreDropped() {
        List<String> first = gateway.facts(null, "Wetmore Hall");
        int used = requests.get();
        assertEquals(first, gateway.facts(null, "Wetmore Hall"));
        assertEquals(used, requests.get(), "second call served from the cache");
        assertTrue(first.stream().noneMatch(f -> f.startsWith("History")));
    }

    @Test void failuresGiveNoFacts() {
        assertEquals(List.of(), gateway.facts(null, "Broken Hall"));
        assertEquals(List.of(), gateway.facts(null, "Nothing Hall"));
        assertEquals(List.of(), gateway.facts(null, " "));
    }
}
