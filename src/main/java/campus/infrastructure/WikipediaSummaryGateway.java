package campus.infrastructure;

import campus.application.explore.ExploreResponse.LandmarkSummary;
import campus.application.explore.LandmarkInfoGateway;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Fetches a landmark's summary and thumbnail from the Wikipedia REST API for the reveal screen. */
public final class WikipediaSummaryGateway implements LandmarkInfoGateway {
    private final HttpClient client;
    private final URI base;

    public WikipediaSummaryGateway(HttpClient client) {
        this(client, URI.create("https://en.wikipedia.org/api/rest_v1/page/summary/"));
    }

    WikipediaSummaryGateway(HttpClient client, URI base) {
        this.client = client;
        this.base = base;
    }

    @Override
    public Optional<LandmarkSummary> summary(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String path = URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", WikipediaFactsGateway.USER_AGENT)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            return parse(response.body(), title);
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<LandmarkSummary> parse(String body, String title) {
        Map<String, Object> page = Json.object(Json.parse(body));
        if (!(page.get("extract") instanceof String extract) || extract.isBlank()) {
            return Optional.empty();
        }
        String image = page.get("thumbnail") instanceof Map<?, ?> thumbnail ? https(thumbnail.get("source")) : null;
        String url = page.get("content_urls") instanceof Map<?, ?> urls
                && urls.get("desktop") instanceof Map<?, ?> desktop ? https(desktop.get("page")) : null;
        String name = page.get("title") instanceof String text ? text : title;
        return Optional.of(new LandmarkSummary(name, extract, image, url));
    }

    /** Only https links are passed to the page, so a bad response can't inject other URL schemes. */
    private static String https(Object value) {
        return value instanceof String link && link.startsWith("https://") ? link : null;
    }
}
