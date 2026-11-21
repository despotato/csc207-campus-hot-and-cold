package campus.ui;

import campus.adapters.StartRoundController;
import campus.adapters.StartRoundPresenter;
import campus.adapters.StartRoundViewModel;
import campus.application.GameSession;
import campus.application.StartRoundResponse.Candidate;
import campus.domain.Campus;
import campus.domain.Guess;
import campus.domain.Landmark;
import campus.domain.Round;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public final class CampusGame {
    private final StartRoundController startRound;
    private final StartRoundPresenter presenter;
    private final GameSession session;
    private final JComboBox<Campus> campuses = new JComboBox<>(new Campus[]{Campus.ST_GEORGE});
    private final JButton start = new JButton("Start round");
    private final JComboBox<Candidate> candidates = new JComboBox<>();
    private final JButton submit = new JButton("Guess");
    private final JButton map = new JButton("Open landmark on map");
    private final JLabel status = new JLabel("Choose a campus and start a round.");
    private final JLabel mapStatus = new JLabel(" ");
    private final JTextArea history = new JTextArea(11, 48);
    private Round round;
    private Landmark mapTarget;

    public CampusGame(StartRoundController startRound, StartRoundPresenter presenter, GameSession session) {
        this.startRound = startRound;
        this.presenter = presenter;
        this.session = session;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        campus.Main.main(args);
    }

    public void show() {
        JFrame frame = new JFrame("Campus Hot & Cold");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel("Campus Hot & Cold");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        titleRow.add(heading);
        controls.add(titleRow);

        JPanel campusRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JLabel campusLabel = new JLabel("Campus");
        campusLabel.setLabelFor(campuses);
        campusRow.add(campusLabel);
        campusRow.add(campuses);
        campusRow.add(start);
        controls.add(campusRow);

        JPanel guessRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JLabel guessLabel = new JLabel("Landmark");
        guessLabel.setLabelFor(candidates);
        guessRow.add(guessLabel);
        guessRow.add(candidates);
        guessRow.add(submit);
        controls.add(guessRow);
        content.add(controls, BorderLayout.NORTH);

        history.setEditable(false);
        history.setLineWrap(true);
        history.setWrapStyleWord(true);
        history.getAccessibleContext().setAccessibleName("Guess history");
        content.add(new JScrollPane(history), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(6, 6));
        footer.add(status, BorderLayout.NORTH);
        JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEADING));
        mapRow.add(map);
        mapRow.add(mapStatus);
        footer.add(mapRow, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        candidates.setEnabled(false);
        submit.setEnabled(false);
        map.setEnabled(false);
        start.addActionListener(event -> loadRound());
        submit.addActionListener(event -> submitGuess());
        map.addActionListener(event -> openMap());
        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void loadRound() {
        Campus campus = (Campus) campuses.getSelectedItem();
        start.setEnabled(false);
        campuses.setEnabled(false);
        candidates.setEnabled(false);
        submit.setEnabled(false);
        map.setEnabled(false);
        mapStatus.setText(" ");
        status.setText("Loading landmarks from Wikipedia…");

        new SwingWorker<StartRoundViewModel, Void>() {
            @Override
            protected StartRoundViewModel doInBackground() {
                startRound.start(campus);
                return presenter.viewModel();
            }

            @Override
            protected void done() {
                start.setEnabled(true);
                campuses.setEnabled(true);
                try {
                    StartRoundViewModel model = get();
                    if (!model.successful()) {
                        restoreAfterLoadError(model.status());
                        return;
                    }
                    round = session.currentRound();
                    mapTarget = null;
                    candidates.setModel(new DefaultComboBoxModel<>(model.candidates().toArray(Candidate[]::new)));
                    candidates.setEnabled(true);
                    submit.setEnabled(true);
                    history.setText("");
                    status.setText(model.status());
                } catch (ExecutionException exception) {
                    restoreAfterLoadError("Could not start the round. Please retry.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    restoreAfterLoadError("Loading was interrupted. Please retry.");
                }
            }
        }.execute();
    }

    private void restoreAfterLoadError(String message) {
        boolean canGuess = round != null && round.revealedLandmark().isEmpty();
        candidates.setEnabled(canGuess);
        submit.setEnabled(canGuess);
        map.setEnabled(mapTarget != null);
        status.setText(message);
    }

    private void submitGuess() {
        Candidate selected = (Candidate) candidates.getSelectedItem();
        if (selected == null) {
            return;
        }
        Guess guess = round.submitGuess(selected.id());
        String trend = guess.trend() == Guess.Trend.FIRST ? "" : " · " + label(guess.trend());
        history.append(round.guesses().size() + ". " + selected.name() + " — "
                + label(guess.heat()) + trend + "\n");
        candidates.removeItem(selected);
        mapTarget = guess.landmark();
        map.setEnabled(true);
        mapStatus.setText(" ");
        var revealed = round.revealedLandmark();
        if (revealed.isPresent()) {
            showResults(revealed.get());
        } else {
            status.setText(round.guessesRemaining() + " guesses left.");
        }
    }

    private void showResults(Landmark answer) {
        candidates.setEnabled(false);
        submit.setEnabled(false);
        history.append("\nAnswer: " + answer.name() + "\n");
        for (Guess guess : round.guesses()) {
            history.append(guess.landmark().name() + " — " + Math.round(guess.distanceMetres()) + " m\n");
        }
        status.setText((round.isWon() ? "Solved! " : "Round over. ") + "Score: " + round.score());
        mapTarget = answer;
    }

    private void openMap() {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            mapStatus.setText("Browser launch unavailable.");
            return;
        }
        URI url = URI.create("https://www.google.com/maps/search/?api=1&query="
                + mapTarget.latitude() + "," + mapTarget.longitude());
        try {
            Desktop.getDesktop().browse(url);
        } catch (IOException | SecurityException exception) {
            mapStatus.setText("Could not open map.");
        }
    }

    private static String label(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

