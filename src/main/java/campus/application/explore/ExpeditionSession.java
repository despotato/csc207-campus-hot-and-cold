package campus.application.explore;

import campus.domain.explore.Expedition;

import java.util.Objects;
import java.util.Optional;

/** Holds the expedition in progress. Callers must serialize commands. */
public final class ExpeditionSession {
    private Expedition current;

    public void start(Expedition next) {
        current = Objects.requireNonNull(next);
    }

    public Optional<Expedition> current() {
        return Optional.ofNullable(current);
    }
}
