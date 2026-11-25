package campus.adapters.explore;

import campus.application.explore.ExploreOutputBoundary;
import campus.application.explore.ExploreResponse;
import campus.domain.explore.Expedition;
import campus.domain.explore.GeoPoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Turns explore responses into view models; holds the latest one for the controller's caller. */
public final class ExplorePresenter implements ExploreOutputBoundary {
    private ExploreViewModel viewModel = new ExploreViewModel(400, Map.of("error", "No action yet."));

    public ExploreViewModel viewModel() {
        return viewModel;
    }

    @Override
    public void present(ExploreResponse response) {
        Map<String, Object> body = new LinkedHashMap<>();
        int status = 200;
        switch (response) {
            case ExploreResponse.Started started -> {
                body.put("type", "started");
                body.put("roundId", started.roundId().toString());
                body.put("campus", started.campus());
                body.put("point", point(started.start()));
                body.put("checksRemaining", started.checksRemaining());
                body.put("guessLimit", started.guessLimit());
                body.put("difficulty", started.difficulty());
                body.put("message", "Find the zone. Check inside it to know you're there. "
                        + started.checksRemaining() + " checks.");
            }
            case ExploreResponse.Moved moved -> {
                body.put("type", "moved");
                body.put("metresWalked", round(moved.metresWalked()));
                body.put("elapsedSeconds", moved.elapsedSeconds());
            }
            case ExploreResponse.Travelled travelled -> {
                body.put("type", "travelled");
                body.put("point", point(travelled.position()));
                body.put("path", travelled.path().stream().map(ExplorePresenter::point).toList());
                body.put("metresWalked", round(travelled.metresWalked()));
                body.put("elapsedSeconds", travelled.elapsedSeconds());
            }
            case ExploreResponse.Rejected rejected -> {
                status = 409;
                body.put("type", "rejected");
                body.put("error", rejected.message());
                body.put("point", point(rejected.position()));
                body.put("metresWalked", round(rejected.metresWalked()));
            }
            case ExploreResponse.Checked checked -> checked(checked, body);
            case ExploreResponse.HintChosen hint -> {
                body.put("type", "hint");
                body.put("level", name(hint.level()));
                body.put("multiplier", hint.multiplier());
                body.put("hint", hint.hint());
                body.put("zoneBuildings", hint.zoneBuildings());
            }
            case ExploreResponse.Guessed guessed -> {
                body.put("type", "guessed");
                body.put("buildingId", guessed.buildingId());
                body.put("correct", guessed.correct());
                body.put("guessesRemaining", guessed.guessesRemaining());
                body.put("message", guessed.correct() ? "That's it!"
                        : guessed.result().isPresent() ? "Out of guesses."
                        : "Not this one. " + guessed.guessesRemaining() + " guesses left.");
                guessed.result().ifPresent(finished -> putFinished(body, finished));
            }
            case ExploreResponse.Failed failed -> {
                status = 400;
                body.put("type", "error");
                body.put("error", failed.message());
            }
        }
        viewModel = new ExploreViewModel(status, body);
    }

    private static void checked(ExploreResponse.Checked checked, Map<String, Object> body) {
        body.put("type", "checked");
        body.put("point", point(checked.point()));
        body.put("checksRemaining", checked.checksRemaining());
        body.put("finalSearch", checked.finalSearch());
        body.put("zoneFound", checked.zone() != null);
        if (checked.zone() != null) {
            body.put("zone", Map.of("centre", point(checked.zone().centre()), "radius", checked.zone().radiusMetres()));
            body.put("message", "You're in the zone! Pick a hint to identify the building.");
        } else if (checked.finalSearch()) {
            body.put("message", "The zone isn't here. The round is over.");
        } else {
            body.put("heat", name(checked.heat()));
            body.put("trend", name(checked.trend()));
            body.put("message", label(checked.heat()) + trendText(checked.trend())
                    + (checked.checksRemaining() == 0 ? " No checks left: your next search is final." : ""));
        }
        if (checked.finished() != null) {
            putFinished(body, checked.finished());
        }
    }

    private static void putFinished(Map<String, Object> body, ExploreResponse.Finished finished) {
        body.put("result", result(finished.result()));
        if (finished.saveError() != null) {
            body.put("saveError", finished.saveError());
        }
    }

    private static Map<String, Object> result(ExploreResponse.Result result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("won", result.won());
        body.put("target", Map.of("name", result.targetName(), "category", result.category(),
                "buildingId", result.targetBuildingId(), "centre", point(result.targetCentre())));
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("route", result.routePoints());
        score.put("efficiency", Math.round(result.efficiency() * 100));
        score.put("guess", result.guessPoints());
        score.put("hintBonus", result.hintBonus());
        score.put("checkBonus", result.checkBonus());
        score.put("multiplier", result.multiplier());
        score.put("total", result.score());
        score.put("max", Expedition.MAX_SCORE);
        body.put("score", score);
        body.put("metresWalked", round(result.metresWalked()));
        body.put("shortestMetres", round(result.shortestMetres()));
        body.put("secondsToZone", result.secondsToZone());
        body.put("zone", Map.of("centre", point(result.zone().centre()), "radius", result.zone().radiusMetres()));
        body.put("checksUsed", result.checksUsed());
        body.put("guessesUsed", result.guessesUsed());
        body.put("hintLevel", result.hintLevel() == null ? null : name(result.hintLevel()));
        body.put("route", result.route().stream().map(ExplorePresenter::point).toList());
        body.put("shortestRoute", result.shortestRoute().stream().map(ExplorePresenter::point).toList());
        body.put("checks", result.checks().stream().map(check -> Map.of("point", point(check.point()),
                "heat", check.inZone() ? "zone" : name(check.heat()), "trend", name(check.trend()),
                "metres", round(check.metresToZone()))).toList());
        if (result.summary() != null) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("title", result.summary().title());
            summary.put("extract", result.summary().extract());
            summary.put("image", result.summary().imageUrl());
            summary.put("url", result.summary().pageUrl());
            body.put("summary", summary);
        }
        return body;
    }

    /** GeoJSON order: [longitude, latitude]. */
    private static List<Double> point(GeoPoint point) {
        return List.of(point.longitude(), point.latitude());
    }

    private static long round(double metres) {
        return Math.round(metres);
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static String label(Expedition.Heat heat) {
        return switch (heat) {
            case HOT -> "Hot!";
            case WARM -> "Warm.";
            case COOL -> "Cool.";
            case COLD -> "Cold.";
        };
    }

    private static String trendText(Expedition.Trend trend) {
        return switch (trend) {
            case FIRST -> "";
            case WARMER -> " Warmer than your last check.";
            case SAME -> " About the same as your last check.";
            case COLDER -> " Colder than your last check.";
        };
    }
}
