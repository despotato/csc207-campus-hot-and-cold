package campus.application;

import java.util.UUID;

public interface SavedResultsInputBoundary {
    void saveCurrentRound();

    void showHistory();

    void reopen(UUID id);

    void openCurrentMap();

    void openSavedMap(UUID id, long landmarkId);
}
