package campus.application;

import campus.domain.CompletedRoundResult;
import campus.domain.Landmark;
import campus.domain.Round;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public record SavedResultsInteractor(GameSession session, RoundRepository repository,
                                     LandmarkMapGateway map, SavedResultsOutputBoundary output)
        implements SavedResultsInputBoundary {
    @Override
    public void saveCurrentRound() {
        var result = session.findCurrentRound().flatMap(Round::completedResult);
        if (result.isEmpty()) {
            output.presentStatus("Finish the round before saving it.");
            return;
        }
        boolean retrySave = false;
        try {
            repository.save(result.get());
        } catch (IOException exception) {
            retrySave = true;
        }
        output.presentRecap(result.get(), retrySave);
    }

    @Override
    public void showHistory() {
        try {
            output.presentHistory(repository.list());
        } catch (IOException exception) {
            output.presentStatus("Could not load history. " + exception.getMessage());
        }
    }

    @Override
    public void reopen(UUID id) {
        try {
            var result = repository.find(id);
            if (result.isPresent()) {
                output.presentRecap(result.get(), false);
            } else {
                output.presentStatus("This saved round is missing. Reload history and retry.");
            }
        } catch (IOException exception) {
            output.presentStatus("Could not read this saved round. Check the file and retry.");
        }
    }

    @Override
    public void openCurrentMap() {
        var destination = session.findCurrentRound().flatMap(round -> round.revealedLandmark()
                .or(() -> latestGuess(round)));
        openMap(destination);
    }

    @Override
    public void openSavedMap(UUID id, long landmarkId) {
        try {
            openMap(repository.find(id).flatMap(result -> savedLandmark(result, landmarkId)));
        } catch (IOException exception) {
            output.presentStatus("Could not read this saved round. Check the file and retry.");
        }
    }

    private void openMap(Optional<Landmark> destination) {
        if (destination.isEmpty()) {
            output.presentStatus("No guessed or revealed landmark is available.");
            return;
        }
        try {
            map.open(destination.get());
            output.presentStatus("");
        } catch (IOException exception) {
            output.presentStatus("Could not open the map. Check your browser and retry.");
        }
    }

    private static Optional<Landmark> latestGuess(Round round) {
        var guesses = round.guesses();
        return guesses.isEmpty() ? Optional.empty() : Optional.of(guesses.getLast().landmark());
    }

    private static Optional<Landmark> savedLandmark(CompletedRoundResult result, long id) {
        if (result.answer().id() == id) {
            return Optional.of(result.answer());
        }
        return result.guesses().stream().map(CompletedRoundResult.Attempt::landmark)
                .filter(landmark -> landmark.id() == id).findFirst();
    }
}
