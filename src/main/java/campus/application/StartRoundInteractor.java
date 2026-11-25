package campus.application;

import campus.domain.Campus;
import campus.domain.Landmark;
import campus.domain.Round;

import java.io.IOException;
import java.util.List;
import java.util.random.RandomGenerator;

public record StartRoundInteractor(LandmarkGateway gateway, RandomGenerator random,
                                   GameSession session, StartRoundOutputBoundary output)
        implements StartRoundInputBoundary {
    @Override
    public void execute(Campus campus) {
        if (campus == null) {
            output.presentFailure("Choose a campus before starting a round.");
            return;
        }
        try {
            List<Landmark> landmarks = gateway.load(campus);
            if (landmarks.size() < Round.GUESS_LIMIT) {
                output.presentFailure("Fewer than six landmarks loaded. Please retry.");
                return;
            }
            Round next = new Round(campus, landmarks, landmarks.get(random.nextInt(landmarks.size())));
            // Replace the old round only after the new round is fully valid.
            session.start(next);
            output.present(new StartRoundResponse(campus.name(), landmarks.stream()
                    .map(item -> new StartRoundResponse.Candidate(item.id(), item.name())).toList(),
                    next.guessesRemaining()));
        } catch (IOException exception) {
            output.presentFailure("Could not load campus landmarks. Check your connection and retry.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            output.presentFailure("Loading was interrupted. Please retry.");
        } catch (IllegalArgumentException exception) {
            output.presentFailure("The landmark data was invalid. Please retry.");
        }
    }
}

