package campus.adapters;

import java.util.List;
import java.util.UUID;

public sealed interface SavedResultsViewModel {
    record Recap(UUID id, String title, String text, List<MapChoice> mapChoices, boolean retrySave)
            implements SavedResultsViewModel {
        public Recap {
            mapChoices = List.copyOf(mapChoices);
        }
    }

    record History(List<Entry> entries) implements SavedResultsViewModel {
        public History {
            entries = List.copyOf(entries);
        }
    }

    record Status(String message) implements SavedResultsViewModel { }

    record Entry(UUID id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    record MapChoice(long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
