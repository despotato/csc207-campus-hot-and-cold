package campus.application;

import campus.domain.CompletedRoundResult;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoundRepository {
    /** Saving the same result again is a no-op. */
    void save(CompletedRoundResult result) throws IOException;

    Optional<CompletedRoundResult> find(UUID id) throws IOException;

    List<CompletedRoundResult> list() throws IOException;
}
