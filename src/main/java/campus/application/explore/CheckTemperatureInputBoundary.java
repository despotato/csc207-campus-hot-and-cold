package campus.application.explore;

import java.util.UUID;

public interface CheckTemperatureInputBoundary {
    /** Spends a check here; with none left, makes the final search. */
    void execute(UUID roundId);
}
