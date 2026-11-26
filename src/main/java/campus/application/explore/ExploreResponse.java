package campus.application.explore;

import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output data for the explore use cases. Nothing here leaks the target or zone before they're found. */
public sealed interface ExploreResponse {
    /** {@code date} is set for the daily puzzle. */
    record Started(UUID roundId, String campusId, String campus, GeoPoint start, int checksRemaining, int guessLimit,
                   String difficulty, String mode, String date) implements ExploreResponse { }

    record Moved(double metresWalked, long elapsedSeconds) implements ExploreResponse { }

    /** Fast travel finished at {@code position}, walking {@code path} (charged as walked distance). */
    record Travelled(GeoPoint position, List<GeoPoint> path, double metresWalked, long elapsedSeconds)
            implements ExploreResponse {
        public Travelled {
            path = List.copyOf(path);
        }
    }

    /** A move was refused; the player stays at {@code position}. */
    record Rejected(String message, GeoPoint position, double metresWalked) implements ExploreResponse { }

    /**
     * A check (or, with none left, the final search). {@code zone} is set when it found the zone;
     * {@code result} is set when a failed final search ended the round.
     */
    record Checked(GeoPoint point, Expedition.Heat heat, Expedition.Trend trend, int checksRemaining,
                   boolean finalSearch, ZoneView zone, Finished finished, int number, Integer temperature,
                   Integer change) implements ExploreResponse { }

    record HintChosen(Expedition.HintLevel level, double multiplier, String hint, List<Integer> zoneBuildings)
            implements ExploreResponse { }

    record Guessed(int buildingId, boolean correct, int guessesRemaining, Finished finished)
            implements ExploreResponse {
        public Optional<Finished> result() {
            return Optional.ofNullable(finished);
        }
    }

    record Failed(String message) implements ExploreResponse { }

    /**
     * Today's daily for a campus: {@code available} (not started), {@code inProgress}, {@code finished} (with the
     * result), {@code used} (started on this device but nothing to resume), or {@code unavailable} (no puzzle today).
     */
    record DailyStatus(String campusId, String date, String status, Result result) implements ExploreResponse { }

    /** A daily round picked up where it was left, with everything the page needs to redraw it. */
    record Resumed(UUID roundId, String campusId, String campus, String date, String stage, GeoPoint position,
                   double metresWalked, long elapsedSeconds, Long zoneElapsedSeconds, int checksRemaining,
                   List<CheckView> checks, List<GeoPoint> trail, ZoneView zone, HintView hint,
                   List<Integer> zoneBuildings, List<Integer> guesses, int guessLimit) implements ExploreResponse {
        public Resumed {
            checks = List.copyOf(checks);
            trail = List.copyOf(trail);
            zoneBuildings = List.copyOf(zoneBuildings);
            guesses = List.copyOf(guesses);
        }
    }

    /** {@code heat} is {@code "zone"} for the check that found it. */
    record CheckView(int number, GeoPoint point, String heat, Integer temperature, Integer change) { }

    record HintView(Expedition.HintLevel level, double multiplier, String text) { }

    record ZoneView(GeoPoint centre, double radiusMetres) { }

    /** The end of a round: the reveal, plus {@code saveError} when saving it failed. */
    record Finished(Result result, String saveError) { }

    /** {@code hintLevel} and {@code secondsToZone} are null when the zone was never found. */
    record Result(boolean won, String targetName, String category, int targetBuildingId, GeoPoint targetCentre,
                  int routePoints, double efficiency, int guessPoints, int hintBonus, int checkBonus,
                  double multiplier, int score,
                  double metresWalked, double shortestMetres, Long secondsToZone, int checksUsed,
                  int guessesUsed, Expedition.HintLevel hintLevel, ZoneView zone, List<GeoPoint> route,
                  List<GeoPoint> shortestRoute, List<Expedition.Check> checks, LandmarkSummary summary) { }

    /** Optional extra information about the target from an external source. */
    record LandmarkSummary(String title, String extract, String imageUrl, String pageUrl) { }
}
