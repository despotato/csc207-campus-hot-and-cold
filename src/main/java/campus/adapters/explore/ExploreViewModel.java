package campus.adapters.explore;

import java.util.Map;

/** HTTP-style status plus a JSON-ready body for the browser view. */
public record ExploreViewModel(int status, Map<String, Object> body) {
    public ExploreViewModel {
        body = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(body));
    }
}
