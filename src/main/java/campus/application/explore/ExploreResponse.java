package campus.application.explore;

import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.util.List;
import java.util.Optional;

/** Output data for the explore use cases. Nothing here leaks the target before the round ends. */
public sealed interface ExploreResponse {
    record Started(String campus, GeoPoint start, int from, int to, double fraction,
                   int checksRemaining, int guessLimit, String difficulty) implements ExploreResponse { }

    record Moved(double metresWalked, long elapsedSeconds, boolean zoneReached, ZoneView zone)
            implements ExploreResponse { }

    record Rejected(String message, int from, int to, double fraction, double metresWalked)
            implements ExploreResponse { }

    record Checked(GeoPoint point, Expedition.Heat heat, Expedition.Trend trend, int checksRemaining)
            implements ExploreResponse { }

    record HintChosen(Expedition.HintLevel level, double multiplier, String hint, List<Integer> zoneBuildings)
            implements ExploreResponse { }

    /** {@code saveError} is set only when saving a finished round failed. */
    record Guessed(int buildingId, boolean correct, int guessesRemaining, Result result, String saveError)
            implements ExploreResponse {
        public Optional<Result> finished() {
            return Optional.ofNullable(result);
        }
    }

    record Failed(String message) implements ExploreResponse { }

    record ZoneView(GeoPoint centre, double radiusMetres) { }

    record Result(boolean won, String targetName, String category, int targetBuildingId, GeoPoint targetCentre,
                  int routePoints, double efficiency, int identifyPoints, double multiplier, int score,
                  double metresWalked, double shortestMetres, long secondsToZone, int checksUsed,
                  int guessesUsed, Expedition.HintLevel hintLevel, List<GeoPoint> route,
                  List<GeoPoint> shortestRoute, List<Expedition.Check> checks, LandmarkSummary summary) { }

    /** Optional extra information about the target from an external source. */
    record LandmarkSummary(String title, String extract, String imageUrl, String pageUrl) { }
}
