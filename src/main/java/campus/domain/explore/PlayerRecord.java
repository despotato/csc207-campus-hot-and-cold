package campus.domain.explore;

import campus.application.explore.SavedExpedition;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure calculations for a player's saved Explore rounds. */
public record PlayerRecord(int roundsPlayed, double winRate, double averageScore, int bestScore,
                           double averageRouteEfficiency, String favouriteHintLevel,
                           Map<String, Integer> bestScorePerCampus, int currentDailyStreak,
                           int longestDailyStreak, Map<Integer, Long> scoreHistogram) {
    public PlayerRecord {
        bestScorePerCampus = Map.copyOf(bestScorePerCampus);
        scoreHistogram = Map.copyOf(scoreHistogram);
    }

    public static PlayerRecord from(List<SavedExpedition> rounds, String homeCampusId,
                                    ZoneId homeTimezone, LocalDate today) {
        int played = rounds.size();
        double wins = rounds.stream().filter(round -> round.result().won()).count();
        double average = average(rounds.stream().mapToDouble(round -> round.result().score()).toArray());
        int best = rounds.stream().mapToInt(round -> round.result().score()).max().orElse(0);
        double efficiency = average(rounds.stream()
                .mapToDouble(round -> round.result().efficiency()).toArray());
        String favourite = favouriteHint(rounds);
        Map<String, Integer> campusBests = campusBests(rounds);
        Map<LocalDate, Boolean> daily = dailyDates(rounds, homeCampusId);
        int longest = longest(daily.keySet().stream().sorted().toList());
        int current = current(daily, today);
        Map<Integer, Long> histogram = histogram(rounds);
        return new PlayerRecord(played, played == 0 ? 0 : wins / played, average, best,
                efficiency, favourite, campusBests, current, longest, histogram);
    }

    private static double average(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        return java.util.Arrays.stream(values).average().orElse(0);
    }

    private static String favouriteHint(List<SavedExpedition> rounds) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SavedExpedition round : rounds) {
            if (round.result().hintLevel() != null) {
                String level = round.result().hintLevel().name().toLowerCase(java.util.Locale.ROOT);
                counts.merge(level, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).findFirst().orElse("none");
    }

    private static Map<String, Integer> campusBests(List<SavedExpedition> rounds) {
        Map<String, Integer> best = new TreeMap<>();
        for (SavedExpedition round : rounds) {
            best.merge(round.campusId(), round.result().score(), Math::max);
        }
        return best;
    }

    private static Map<LocalDate, Boolean> dailyDates(List<SavedExpedition> rounds, String homeCampusId) {
        Map<LocalDate, Boolean> dates = new java.util.HashMap<>();
        for (SavedExpedition round : rounds) {
            if ("daily".equals(round.mode()) && round.campusId().equals(homeCampusId)
                    && round.dailyDate() != null) {
                dates.put(round.dailyDate(), true);
            }
        }
        return dates;
    }

    private static int longest(List<LocalDate> dates) {
        if (dates.isEmpty()) {
            return 0;
        }
        int longest = 1;
        int run = 1;
        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i).equals(dates.get(i - 1).plusDays(1))) {
                run++;
            } else {
                run = 1;
            }
            longest = Math.max(longest, run);
        }
        return longest;
    }

    private static int current(Map<LocalDate, Boolean> dates, LocalDate today) {
        LocalDate anchor = dates.containsKey(today) ? today : today.minusDays(1);
        int run = 0;
        while (dates.containsKey(anchor)) {
            run++;
            anchor = anchor.minusDays(1);
        }
        return run;
    }

    private static Map<Integer, Long> histogram(List<SavedExpedition> rounds) {
        Map<Integer, Long> histogram = new TreeMap<>();
        for (SavedExpedition round : rounds) {
            int bucket = Math.min(1000, round.result().score() / 100 * 100);
            histogram.merge(bucket, 1L, Long::sum);
        }
        return histogram;
    }
}
