package campus.infrastructure;

import campus.adapters.explore.ExploreController;
import campus.adapters.explore.ExploreViewModel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Serves the 3D explore page, the campus pack, and a JSON API to the explore controller.
 * Listens on 127.0.0.1 only, and accepts game actions only as same-origin JSON POSTs.
 */
public final class ExploreWebServer implements AutoCloseable {
    static final String PAGE = "/campus/explore/index.html";
    private static final int MAX_BODY = 16 * 1024;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "explore-web");
        thread.setDaemon(true);
        return thread;
    });
    private final ExploreController controller;
    private final byte[] pack;
    private final Supplier<Map<String, Object>> debug;

    /**
     * @param debug optional supplier of round internals for automated visual tests; pass null in play.
     */
    public ExploreWebServer(ExploreController controller, String packJson, int port,
                            Supplier<Map<String, Object>> debug) throws IOException {
        this.controller = Objects.requireNonNull(controller);
        this.pack = packJson.getBytes(StandardCharsets.UTF_8);
        this.debug = debug;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::route);
        server.start();
    }

    public URI address() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private void route(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String expectedHost = "127.0.0.1:" + server.getAddress().getPort();
            String localhost = "localhost:" + server.getAddress().getPort();
            if (!expectedHost.equals(host) && !localhost.equals(host)) {
                send(exchange, 403, "text/plain", "Forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                send(exchange, 200, "text/html; charset=utf-8", page());
            } else if ("GET".equals(method) && "/pack.json".equals(path)) {
                send(exchange, 200, "application/json; charset=utf-8", pack);
            } else if ("GET".equals(method) && "/api/debug".equals(path) && debug != null) {
                json(exchange, 200, debug.get());
            } else if (path.startsWith("/api/")) {
                api(exchange, path.substring("/api/".length()), "http://" + host);
            } else {
                send(exchange, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private void api(HttpExchange exchange, String action, String ownOrigin) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (!"POST".equals(exchange.getRequestMethod())) {
            json(exchange, 405, Map.of("type", "error", "error", "Use POST."));
            return;
        }
        if ((origin != null && !origin.equals(ownOrigin))
                || type == null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
            json(exchange, 403, Map.of("type", "error", "error", "Forbidden."));
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
            json(exchange, 413, Map.of("type", "error", "error", "Request too large."));
            return;
        }
        Map<String, Object> fields;
        try {
            String text = new String(body, StandardCharsets.UTF_8).strip();
            fields = text.isEmpty() ? Map.of() : Json.object(Json.parse(text));
        } catch (IllegalArgumentException exception) {
            json(exchange, 400, Map.of("type", "error", "error", "Invalid JSON."));
            return;
        }
        ExploreViewModel model = controller.handle(action, fields);
        json(exchange, model.status(), model.body());
    }

    private static byte[] page() throws IOException {
        // Development only: -Dcampus.explore.pageFile=src/main/resources/campus/explore/index.html serves edits live.
        String file = System.getProperty("campus.explore.pageFile");
        if (file != null) {
            return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(file));
        }
        try (InputStream in = ExploreWebServer.class.getResourceAsStream(PAGE)) {
            if (in == null) {
                throw new IOException("Explore page missing from the build.");
            }
            return in.readAllBytes();
        }
    }

    private static void json(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
