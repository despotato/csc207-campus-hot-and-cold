package campus.application;

import campus.domain.CompletedRoundResult;

import java.util.List;

public interface SavedResultsOutputBoundary {
    void presentRecap(CompletedRoundResult result, boolean retrySave);

    void presentHistory(List<CompletedRoundResult> results);

    void presentStatus(String message);
}
