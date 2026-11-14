package campus.domain;

public record Guess(Landmark landmark, double distanceMetres, Heat heat, Trend trend) {
    public enum Heat {
        CORRECT, HOT, WARM, COOL, COLD
    }

    public enum Trend {
        FIRST, WARMER, SAME, COLDER
    }

    static Guess evaluate(Landmark landmark, Landmark target, Guess previous) {
        double distance = landmark.distanceTo(target);
        Heat heat;
        if (landmark.id() == target.id()) {
            heat = Heat.CORRECT;
        } else if (distance < 100) {
            heat = Heat.HOT;
        } else if (distance < 250) {
            heat = Heat.WARM;
        } else if (distance < 500) {
            heat = Heat.COOL;
        } else {
            heat = Heat.COLD;
        }

        Trend trend = Trend.FIRST;
        if (previous != null) {
            double change = distance - previous.distanceMetres();
            if (Math.abs(change) < 1) {
                trend = Trend.SAME;
            } else {
                trend = change < 0 ? Trend.WARMER : Trend.COLDER;
            }
        }
        return new Guess(landmark, distance, heat, trend);
    }
}
