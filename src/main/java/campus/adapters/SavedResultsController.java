package campus.adapters;

import campus.application.SavedResultsInputBoundary;

import java.util.UUID;

public record SavedResultsController(SavedResultsInputBoundary input) {
    public void saveCurrentRound() {
        input.saveCurrentRound();
    }

    public void showHistory() {
        input.showHistory();
    }

    public void reopen(UUID id) {
        input.reopen(id);
    }

    public void openCurrentMap() {
        input.openCurrentMap();
    }

    public void openSavedMap(UUID id, long landmarkId) {
        input.openSavedMap(id, landmarkId);
    }
}
