package campus.application;

import java.util.List;

/** Public output data deliberately omits the secret target and coordinates. */
public record StartRoundResponse(String campus, List<Candidate> candidates, int guessesRemaining) {
    public StartRoundResponse {
        candidates = List.copyOf(candidates);
    }

    public record Candidate(long id, String name) {
        @Override
        public String toString() { return name; }
    }
}
