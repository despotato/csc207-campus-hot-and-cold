package campus.infrastructure;

import campus.application.explore.CampusCatalog;
import campus.domain.explore.CampusMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Campuses listed in {@code /campus/packs/index.json}; each pack is loaded the first time it is played.
 * To add a university, build its pack with tools/build_pack.py and add an entry to the index.
 */
public final class PackCatalog implements CampusCatalog {
    public static final String INDEX = "/campus/packs/index.json";

    private final Map<String, String> files = new LinkedHashMap<>();
    private final Map<String, CampusMap> loaded = new ConcurrentHashMap<>();

    public PackCatalog() throws IOException {
        Map<String, Object> index = Json.object(Json.parse(CampusPackLoader.raw(INDEX)));
        for (Object item : Json.array(index.get("campuses"))) {
            Map<String, Object> campus = Json.object(item);
            String file = (String) campus.get("file");
            if (file == null || !file.matches("[a-z0-9-]+\\.json")) {
                throw new IOException("Invalid campus pack file name: " + file);
            }
            files.put((String) campus.get("id"), file);
        }
        if (files.isEmpty()) {
            throw new IOException("No campuses in " + INDEX);
        }
    }

    @Override
    public List<String> campusIds() {
        return List.copyOf(files.keySet());
    }

    @Override
    public Optional<CampusMap> campus(String id) {
        String file = files.get(id);
        if (file == null) {
            return Optional.empty();
        }
        return Optional.of(loaded.computeIfAbsent(id, key -> {
            try {
                return CampusPackLoader.load("/campus/packs/" + file);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }));
    }

    /** The raw JSON of a listed pack file (served to the browser), or empty if it isn't in the index. */
    public Optional<String> rawPack(String file) throws IOException {
        if (!files.containsValue(file)) {
            return Optional.empty();
        }
        return Optional.of(CampusPackLoader.raw("/campus/packs/" + file));
    }

    public String rawIndex() throws IOException {
        return CampusPackLoader.raw(INDEX);
    }
}
