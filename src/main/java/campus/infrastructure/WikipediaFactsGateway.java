package campus.infrastructure;

import campus.application.explore.LandmarkFactsGateway;
import campus.domain.explore.LandmarkNames;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Hint facts from the MediaWiki Action API. Reads the landmark's own article (when it has one) plus a few
 * articles that mention it by name, splits them into sentences, keeps factual ones, and blanks the name.
 * Facts from the start of its own article come first (well known, for students); the rest are ranked by
 * how historical and specific they are (years, architects, namesakes), for campus historians.
 */
public final class WikipediaFactsGateway implements LandmarkFactsGateway {
    private static final int MAX_ARTICLES = 3;
    static final String USER_AGENT =
            "CampusHotAndCold/0.2 (https://github.com/despotato/csc207-campus-hot-and-cold; CSC207 classroom project)";
    private static final int KNOWN_SENTENCES = 4;
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[a-z0-9)\\]\"'%][.!?])\\s+(?=[A-Z\"(])");
    private static final Pattern FACTUAL = Pattern.compile(
            "\\b(1[6-9]\\d\\d|20\\d\\d|built|designed|named|opened|architect|founded|houses|home to|originally"
                    + "|renovat\\w*|construct\\w*|style|storey|floors?|students|collection|donat\\w*|film\\w*"
                    + "|demolish\\w*|expan\\w*|tower|gothic|brutalis\\w*|modernis\\w*|victorian)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ABBREVIATIONS = Pattern.compile("\\b(St|Mt|Dr|Mr|Mrs|Ms|Jr|Sr|No|ca|c)\\.\\s");

    private final HttpClient client;
    private final URI api;
    /** Facts don't change during a run, so each landmark is fetched at most once (Wikipedia asks for this). */
    private final Map<String, List<String>> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public WikipediaFactsGateway(HttpClient client) {
        this(client, URI.create("https://en.wikipedia.org/w/api.php"));
    }

    WikipediaFactsGateway(HttpClient client, URI api) {
        this.client = client;
        this.api = api;
    }

    @Override
    public List<String> facts(String wikipediaTitle, String landmarkName) {
        if (landmarkName == null || landmarkName.isBlank()) {
            return List.of();
        }
        String key = wikipediaTitle + "|" + landmarkName;
        List<String> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        List<String> facts = fetch(wikipediaTitle, landmarkName);
        if (!facts.isEmpty()) {
            cache.put(key, facts);
        }
        return facts;
    }

    /** Requests are made one at a time, as Wikipedia's API etiquette asks. */
    private List<String> fetch(String wikipediaTitle, String landmarkName) {
        try {
            Set<String> titles = new LinkedHashSet<>();
            if (wikipediaTitle != null && !wikipediaTitle.isBlank()) {
                titles.add(wikipediaTitle);
            }
            titles.addAll(search("\"" + landmarkName + "\" Toronto"));
            List<String> pages = titles.stream().limit(MAX_ARTICLES).map(this::extract).toList();
            List<String> known = new ArrayList<>();
            List<String> obscure = new ArrayList<>();
            boolean own = wikipediaTitle != null && !wikipediaTitle.isBlank();
            for (String page : pages) {
                sortFacts(sentences(page), landmarkName, own, known, obscure);
                own = false;
            }
            return merge(known, obscure);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    /** From the landmark's own article, the opening facts are well known; from others, only mentions count. */
    private static void sortFacts(List<String> sentences, String landmarkName, boolean ownArticle,
                                  List<String> known, List<String> obscure) {
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);
            if (!ownArticle && !mentions(sentence, landmarkName)) {
                continue;
            }
            String fact = redact(sentence, landmarkName);
            if (fact != null) {
                (ownArticle && i < KNOWN_SENTENCES ? known : obscure).add(fact);
            }
        }
    }

    /** Well-known facts first, then the most historical and specific; no duplicates, at most twelve. */
    private static List<String> merge(List<String> known, List<String> obscure) {
        obscure.sort(Comparator.comparingInt(WikipediaFactsGateway::specificity).reversed());
        LinkedHashSet<String> result = new LinkedHashSet<>(known);
        result.addAll(obscure);
        return result.stream().limit(12).toList();
    }

    /** Splits plain article text into sentences, keeping initials and "St." together. */
    static List<String> sentences(String text) {
        if (text == null) {
            return List.of();
        }
        // Plain-text extracts put section headings on their own lines without a full stop: drop those.
        String body = text.lines().map(String::strip).filter(line -> line.matches(".*[.!?\"')]$"))
                .reduce("", (a, b) -> a + " " + b);
        String flat = body.replaceAll("\\s+", " ").trim();
        flat = ABBREVIATIONS.matcher(flat).replaceAll("$1§ ");
        flat = flat.replaceAll("\\b([A-Z])\\. ", "$1§ ");
        List<String> result = new ArrayList<>();
        for (String part : SENTENCE_END.split(flat)) {
            result.add(part.replace("§", ".").trim());
        }
        return result;
    }

    /** A hint-sized factual sentence with the landmark's name blanked, or null if it isn't usable. */
    static String redact(String sentence, String landmarkName) {
        if (sentence.length() < 45 || sentence.length() > 260 || !FACTUAL.matcher(sentence).find()
                || sentence.contains("may refer to") || sentence.startsWith("See also")) {
            return null;
        }
        String result = sentence.replaceAll("(?i)" + Pattern.quote(landmarkName), "this building");
        for (String word : LandmarkNames.distinctiveWords(landmarkName)) {
            result = result.replaceAll("(?i)\\b" + Pattern.quote(word) + "('s)?\\b", "____");
        }
        result = result.replaceAll("(____\\s*)+", "____ ");
        return LandmarkNames.givesAway(result, landmarkName) ? null : result.trim();
    }

    /** Other articles only count when they use the building's full name (e.g. not just "graduate"). */
    private static boolean mentions(String sentence, String landmarkName) {
        return sentence.toLowerCase(Locale.ROOT).contains(landmarkName.toLowerCase(Locale.ROOT));
    }

    private static final Pattern HISTORY = Pattern.compile("\\b(built|designed|architect\\w*|named (for|after)|founded"
            + "|originally|former\\w*|opened|construct\\w*|demolish\\w*|renovat\\w*|donat\\w*|century|war"
            + "|heritage|first|oldest|replaced|moved)\\b", Pattern.CASE_INSENSITIVE);

    /** Hard hints want history: years, architects, namesakes, original uses; then other specifics. */
    private static int specificity(String fact) {
        int score = HISTORY.matcher(fact).results().mapToInt(match -> 4).sum();
        var years = Pattern.compile("\\b(1[6-9]\\d\\d|20\\d\\d)\\b").matcher(fact);
        while (years.find()) {
            score += 3;
        }
        score += (int) fact.chars().filter(Character::isDigit).count() / 2;
        score += Math.min(4, (int) Pattern.compile("\\b[A-Z][a-z]+").matcher(fact).results().count() / 2);
        return score;
    }

    private List<String> search(String query) {
        Map<String, Object> body = getJson("action=query&list=search&srlimit=" + MAX_ARTICLES + "&format=json&srsearch="
                + URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (body == null || !(body.get("query") instanceof Map<?, ?> q) || !(q.get("search") instanceof List<?> hits)) {
            return List.of();
        }
        List<String> titles = new ArrayList<>();
        for (Object hit : hits) {
            if (hit instanceof Map<?, ?> page && page.get("title") instanceof String title) {
                titles.add(title);
            }
        }
        return titles;
    }

    private String extract(String title) {
        Map<String, Object> body = getJson("action=query&prop=extracts&explaintext=1&exsectionformat=plain"
                + "&redirects=1&format=json&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8));
        return body == null ? null : pageText(body);
    }

    private static String pageText(Map<String, Object> body) {
        try {
            if (body.get("query") instanceof Map<?, ?> q
                    && q.get("pages") instanceof Map<?, ?> pages) {
                for (Object page : pages.values()) {
                    if (page instanceof Map<?, ?> p && p.get("extract") instanceof String text) {
                        return text;
                    }
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed response: no facts from this page.
        }
        return null;
    }

    private Map<String, Object> getJson(String query) {
        try {
            HttpResponse<String> response = client.send(request(query), HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? Json.object(Json.parse(response.body())) : null;
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private HttpRequest request(String query) {
        return HttpRequest.newBuilder(URI.create(api + "?" + query))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", USER_AGENT)
                .GET().build();
    }
}
