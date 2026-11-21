package campus.adapters;

import campus.application.StartRoundInputBoundary;
import campus.domain.Campus;

/** Converts the start action into a call through the input port. */
public record StartRoundController(StartRoundInputBoundary startRound) {
    public void start(Campus campus) { startRound.execute(campus); }
}
