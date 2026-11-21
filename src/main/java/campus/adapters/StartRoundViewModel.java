package campus.adapters;

import campus.application.StartRoundResponse.Candidate;
import java.util.List;

public record StartRoundViewModel(boolean successful, String status, List<Candidate> candidates) {
    public StartRoundViewModel {
        candidates = List.copyOf(candidates);
    }
}
