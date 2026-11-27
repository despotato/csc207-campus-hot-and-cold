package campus.application.explore;

import campus.domain.explore.Expedition.HintLevel;

import java.util.UUID;

public interface ChooseHintInputBoundary {
    void execute(UUID roundId, HintLevel level);
}
