package campus.application;

import campus.domain.Campus;
import campus.domain.Landmark;
import campus.domain.Round;

import java.io.IOException;
import java.util.List;
import java.util.random.RandomGenerator;

public record StartRoundInteractor(LandmarkGateway gateway, RandomGenerator random) {
    public Round execute(Campus campus) throws IOException, InterruptedException {
        List<Landmark> landmarks = gateway.load(campus);
        if (landmarks.size() < Round.GUESS_LIMIT) {
            throw new IOException("Wikipedia returned fewer than six landmarks. Please retry.");
        }
        return new Round(landmarks, landmarks.get(random.nextInt(landmarks.size())));
    }
}
