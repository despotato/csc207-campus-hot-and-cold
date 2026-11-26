package campus.application.explore;

import campus.domain.explore.Difficulty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** A completed Explore round and the metadata needed to review and aggregate it later. */
public record SavedExpedition(UUID id, String campusId, Instant finishedAt, String difficulty, String mode,
                              LocalDate dailyDate, ExploreResponse.Result result) {
    public SavedExpedition {
        Objects.requireNonNull(id);
        if (campusId == null || !campusId.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid campus ID.");
        }
        Objects.requireNonNull(finishedAt);
        difficulty = Difficulty.named(difficulty).name();
        if (!"daily".equals(mode) && !"unlimited".equals(mode)) {
            throw new IllegalArgumentException("Invalid round mode.");
        }
        if (("daily".equals(mode)) != (dailyDate != null)) {
            throw new IllegalArgumentException("A daily round must have a date.");
        }
        Objects.requireNonNull(result);
    }
}
