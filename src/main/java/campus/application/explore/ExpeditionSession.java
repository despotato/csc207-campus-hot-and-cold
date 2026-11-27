package campus.application.explore;

import campus.domain.explore.Expedition;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds the expedition in progress. Callers must serialize commands. A daily round is also checkpointed to a
 * {@link DailyRunStore} after each change, so it can be resumed after the app restarts.
 */
public final class ExpeditionSession {
    public static final String STALE = "This round has ended or was replaced (another tab?). Start a new round.";
    /** Walking sends many small moves; save those at most this often (checks, hints and guesses save at once). */
    static final Duration MOVE_CHECKPOINT_INTERVAL = Duration.ofSeconds(2);

    private final DailyRunStore dailyRuns;
    private final Clock clock;
    private Expedition current;
    private String dailyCampus;
    private LocalDate dailyDate;
    private Instant lastCheckpoint = Instant.MIN;

    public ExpeditionSession() {
        this(DailyRunStore.NONE, Clock.systemUTC());
    }

    public ExpeditionSession(DailyRunStore dailyRuns, Clock clock) {
        this.dailyRuns = Objects.requireNonNull(dailyRuns);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(Expedition next) {
        current = Objects.requireNonNull(next);
        dailyCampus = null;
        dailyDate = null;
    }

    /** Starts (or resumes) a daily round, which is checkpointed from now on. */
    public void startDaily(Expedition next, String campusId, LocalDate date) throws IOException {
        current = Objects.requireNonNull(next);
        dailyCampus = Objects.requireNonNull(campusId);
        dailyDate = Objects.requireNonNull(date);
        lastCheckpoint = Instant.MIN;
        checkpoint(true);
    }

    /** Saves the daily round's progress; walking ({@code important == false}) is throttled. Best effort. */
    public void checkpoint(boolean important) {
        if (current == null || dailyCampus == null) {
            return;
        }
        Instant now = clock.instant();
        if (!important && now.isBefore(lastCheckpoint.plus(MOVE_CHECKPOINT_INTERVAL))) {
            return;
        }
        try {
            dailyRuns.save(dailyCampus, dailyDate, current.snapshot());
            lastCheckpoint = now;
        } catch (IOException exception) {
            // Keep playing; the next checkpoint tries again.
        }
    }

    public Optional<Expedition> current() {
        return Optional.ofNullable(current);
    }

    /** The current round only if it is the one the caller is playing, so a stale tab can't act on a new round. */
    public Optional<Expedition> current(UUID roundId) {
        return current().filter(round -> round.id().equals(roundId));
    }

    /** The daily round held in memory, if it is today's for this campus. */
    public Optional<Expedition> currentDaily(String campusId, LocalDate date) {
        return campusId.equals(dailyCampus) && date.equals(dailyDate) ? current() : Optional.empty();
    }

    /** The local puzzle date for the currently active daily, if this is its round ID. */
    public Optional<LocalDate> dailyDate(UUID roundId) {
        return current != null && current.id().equals(roundId) && dailyCampus != null
                ? Optional.of(dailyDate) : Optional.empty();
    }
}
