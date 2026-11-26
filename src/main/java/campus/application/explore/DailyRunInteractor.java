package campus.application.explore;

import campus.domain.explore.CampusMap;
import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Today's daily after a reload or restart: report its status (including the finished result) and resume an
 * unfinished run exactly where it was left. The run comes from the session if it's still in memory, otherwise
 * from the {@link DailyRunStore} checkpoint.
 */
public final class DailyRunInteractor implements DailyStatusInputBoundary, ResumeDailyInputBoundary {
    private final CampusCatalog campuses;
    private final DailyAttempts attempts;
    private final DailyRunStore runs;
    private final ExpeditionSession session;
    private final RoundFinisher finisher;
    private final Clock clock;
    private final ExploreOutputBoundary output;

    public DailyRunInteractor(CampusCatalog campuses, DailyAttempts attempts, DailyRunStore runs,
                              ExpeditionSession session, RoundFinisher finisher, Clock clock,
                              ExploreOutputBoundary output) {
        this.campuses = Objects.requireNonNull(campuses);
        this.attempts = Objects.requireNonNull(attempts);
        this.runs = Objects.requireNonNull(runs);
        this.session = Objects.requireNonNull(session);
        this.finisher = Objects.requireNonNull(finisher);
        this.clock = Objects.requireNonNull(clock);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void status(String campusId) {
        CampusMap map = campus(campusId);
        if (map == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(map.timezone()));
        String date = today.toString();
        if (map.dailyPuzzle(today).isEmpty()) {
            output.present(new ExploreResponse.DailyStatus(map.id(), date, "unavailable", null));
            return;
        }
        try {
            Optional<Expedition> run = todaysRun(map, today);
            if (run.isPresent()) {
                boolean finished = run.get().stage() == Expedition.Stage.FINISHED;
                output.present(new ExploreResponse.DailyStatus(map.id(), date, finished ? "finished" : "inProgress",
                        finished ? finisher.describe(run.get()) : null));
            } else {
                output.present(new ExploreResponse.DailyStatus(map.id(), date,
                        attempts.claimed(map.id(), today) ? "used" : "available", null));
            }
        } catch (IOException | IllegalArgumentException exception) {
            output.present(new ExploreResponse.Failed("Couldn't read today's daily. Please retry."));
        }
    }

    @Override
    public void resume(String campusId) {
        CampusMap map = campus(campusId);
        if (map == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(map.timezone()));
        Expedition round;
        try {
            round = todaysRun(map, today).orElse(null);
            if (round == null) {
                output.present(new ExploreResponse.Failed("There's no daily in progress to resume."));
                return;
            }
            if (round.stage() == Expedition.Stage.FINISHED) {
                output.present(new ExploreResponse.DailyStatus(map.id(), today.toString(), "finished",
                        finisher.describe(round)));
                return;
            }
            session.startDaily(round, map.id(), today);
        } catch (IOException | IllegalArgumentException exception) {
            output.present(new ExploreResponse.Failed("Couldn't restore today's daily. Please retry."));
            return;
        }
        output.present(resumed(map, today, round));
    }

    private CampusMap campus(String campusId) {
        String id = campusId == null ? campuses.defaultCampusId() : campusId;
        CampusMap map = campuses.campus(id).orElse(null);
        if (map == null) {
            output.present(new ExploreResponse.Failed("Unknown campus."));
        }
        return map;
    }

    private Optional<Expedition> todaysRun(CampusMap map, LocalDate today) throws IOException {
        Optional<Expedition> inMemory = session.currentDaily(map.id(), today);
        if (inMemory.isPresent()) {
            return inMemory;
        }
        return runs.load(map.id(), today).map(saved -> Expedition.restore(map, saved));
    }

    private ExploreResponse.Resumed resumed(CampusMap map, LocalDate today, Expedition round) {
        List<ExploreResponse.CheckView> checks = new ArrayList<>();
        Integer previous = null;
        for (Expedition.Check check : round.checks()) {
            Integer change = previous == null || check.inZone() ? null : check.temperature() - previous;
            checks.add(new ExploreResponse.CheckView(checks.size() + 1, check.point(),
                    check.inZone() ? "zone" : check.heat().name().toLowerCase(Locale.ROOT),
                    check.inZone() ? null : check.temperature(), change));
            previous = check.temperature();
        }
        String stage = round.stage() == Expedition.Stage.FIND_ZONE ? "walk"
                : round.hintLevel().isEmpty() ? "zone" : round.guesses().isEmpty() ? "hint" : "guess";
        var zone = round.revealedZone().map(z -> new ExploreResponse.ZoneView(z.centre(), z.radiusMetres()))
                .orElse(null);
        var hint = round.hintLevel().map(level -> new ExploreResponse.HintView(level, level.multiplier(),
                round.hint().orElseThrow())).orElse(null);
        List<Integer> zoneBuildings = zone == null ? List.of() : round.revealedZone()
                .map(z -> map.buildingsIn(z).stream().map(CampusMap.Building::id).toList()).orElse(List.of());
        Long zoneElapsed = round.zoneReachedAt()
                .map(at -> Duration.between(at, clock.instant()).toSeconds()).orElse(null);
        List<GeoPoint> trail = round.track();
        return new ExploreResponse.Resumed(round.id(), map.id(), map.name(), today.toString(), stage,
                round.position(), round.metresWalked(), round.secondsElapsed(clock.instant()), zoneElapsed,
                round.checksRemaining(), checks, trail, zone, hint, zoneBuildings, round.guesses(),
                Expedition.GUESS_LIMIT, round.clueBuildings());
    }
}
