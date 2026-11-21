package campus.application;

import campus.domain.Campus;

/** Input port used by the controller; no dependency on Swing or infrastructure. */
public interface StartRoundInputBoundary {
    void execute(Campus campus);
}
