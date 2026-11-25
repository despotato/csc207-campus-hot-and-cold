package campus.application.explore;

import campus.domain.explore.Expedition;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Holds the expedition in progress. Callers must serialize commands. */
public final class ExpeditionSession {
    public static final String STALE = "This round has ended or was replaced (another tab?). Start a new round.";

    private Expedition current;

    public void start(Expedition next) {
        current = Objects.requireNonNull(next);
    }

    public Optional<Expedition> current() {
        return Optional.ofNullable(current);
    }

    /** The current round only if it is the one the caller is playing, so a stale tab can't act on a new round. */
    public Optional<Expedition> current(UUID roundId) {
        return current().filter(round -> round.id().equals(roundId));
    }
}
