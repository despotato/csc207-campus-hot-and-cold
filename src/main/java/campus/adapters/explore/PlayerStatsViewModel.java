package campus.adapters.explore;

import java.util.Map;

public record PlayerStatsViewModel(int status, Map<String, Object> body) {
    public PlayerStatsViewModel {
        body = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(body));
    }
}
