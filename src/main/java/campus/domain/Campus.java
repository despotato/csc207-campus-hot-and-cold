package campus.domain;

import java.util.List;

public record Campus(String name, List<String> articleTitles) {
    public Campus {
        articleTitles = List.copyOf(articleTitles);
    }

    public static final Campus ST_GEORGE = new Campus("University of Toronto — St. George",
            List.of("Robarts Library", "Hart House (University of Toronto)",
                    "University College, Toronto", "Bahen Centre for Information Technology",
                    "Convocation Hall (University of Toronto)", "Trinity College, Toronto",
                    "Innis College, Toronto", "Knox College, Toronto", "Massey College, Toronto",
                    "Varsity Stadium", "Victoria University, Toronto", "Wycliffe College, Toronto"));

    @Override
    public String toString() {
        return name;
    }
}
