package campus.ui;

import campus.adapters.SavedResultsController;
import campus.adapters.SavedResultsPresenter;
import campus.adapters.SavedResultsViewModel;
import campus.adapters.StartRoundController;
import campus.adapters.StartRoundPresenter;
import campus.adapters.StartRoundViewModel;
import campus.adapters.SubmitGuessController;
import campus.adapters.SubmitGuessPresenter;
import campus.adapters.SubmitGuessViewModel;
import campus.application.StartRoundResponse.Candidate;
import campus.domain.Campus;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class CampusGame {
    private final StartRoundController startRound;
    private final StartRoundPresenter presenter;
    private final SubmitGuessController submitGuessController;
    private final SubmitGuessPresenter submitGuessPresenter;
    private final SavedResultsController savedResults;
    private final SavedResultsPresenter resultsPresenter;
    private final SavedRoundsPanel savedRounds;
    private final CardLayout screens = new CardLayout();
    private final JPanel screenPanel = new JPanel(screens);
    private final JComboBox<Campus> campuses = new JComboBox<>(new Campus[]{Campus.ST_GEORGE});
    private final JButton start = new JButton("Start round");
    private final JComboBox<Candidate> candidates = new JComboBox<>();
    private final JButton submit = new JButton("Guess");
    private final JButton browseHistory = new JButton("History");
    private final JButton retrySave = new JButton("Retry save");
    private final JButton map = new JButton("Open landmark on map");
    private final JLabel status = new JLabel("Choose a campus and start a round.");
    private final JLabel mapStatus = new JLabel(" ");
    private final JTextArea history = new JTextArea(11, 48);
    private boolean roundActive;
    private boolean roundComplete;
    private int guessesMade;
    private UUID visibleRecap;
    private boolean savePending;
    private boolean saving;

    public CampusGame(StartRoundController startRound, StartRoundPresenter presenter,
                      SubmitGuessController submitGuessController, SubmitGuessPresenter submitGuessPresenter,
                      SavedResultsController savedResults, SavedResultsPresenter resultsPresenter) {
        this.startRound = startRound;
        this.presenter = presenter;
        this.submitGuessController = submitGuessController;
        this.submitGuessPresenter = submitGuessPresenter;
        this.savedResults = savedResults;
        this.resultsPresenter = resultsPresenter;
        this.savedRounds = new SavedRoundsPanel(() -> screens.show(screenPanel, "game"),
                this::loadSavedHistory, id -> runHistoryAction(() -> savedResults.reopen(id)),
                (id, landmark) -> runHistoryAction(() -> savedResults.openSavedMap(id, landmark)));
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        campus.Main.main(args);
    }

    public void show() {
        JFrame frame = new JFrame("Campus Hot & Cold");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (saving) {
                    JOptionPane.showMessageDialog(frame, "Wait for the save to finish before closing.");
                } else if (!savePending || JOptionPane.showConfirmDialog(frame,
                        "This round is unsaved. Quit and lose the result?", "Unsaved result",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
                    frame.dispose();
                }
            }
        });
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
        mapRow.add(browseHistory);
        mapRow.add(retrySave);
        footer.add(mapStatus, BorderLayout.CENTER);
        footer.add(mapRow, BorderLayout.SOUTH);
        content.add(footer, BorderLayout.SOUTH);

        setBusy(false);
        start.addActionListener(event -> loadRound());
        submit.addActionListener(event -> submitGuess());
        map.addActionListener(event -> openMap());
        browseHistory.addActionListener(event -> {
            screens.show(screenPanel, "history");
            loadSavedHistory();
        });
        retrySave.addActionListener(event -> saveRound());
        screenPanel.add(content, "game");
        screenPanel.add(savedRounds, "history");
        frame.setContentPane(screenPanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void loadRound() {
        Campus campus = (Campus) campuses.getSelectedItem();
        setBusy(true);
        mapStatus.setText(" ");
        status.setText("Loading landmarks from Wikipedia��");

        new SwingWorker<StartRoundViewModel, Void>() {
            @Override
            protected StartRoundViewModel doInBackground() {
                startRound.start(campus);
                return presenter.viewModel();
            }

            @Override
            protected void done() {
                try {
                    StartRoundViewModel model = get();
                    if (!model.successful()) {
                        status.setText(model.status());
                        return;
                    }
                    roundActive = true;
                    roundComplete = false;
                    guessesMade = 0;
                    visibleRecap = null;
                    candidates.setModel(new DefaultComboBoxModel<>(model.candidates().toArray(Candidate[]::new)));
                    history.setText("");
                    status.setText(model.status());
                } catch (ExecutionException exception) {
                    status.setText("Could not start the round. Please retry.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    status.setText("Loading was interrupted. Please retry.");
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void submitGuess() {
        Candidate selected = (Candidate) candidates.getSelectedItem();
        if (selected == null) {
            return;
        }
        submitGuessController.submit(selected.id());
        SubmitGuessViewModel model = submitGuessPresenter.viewModel();
        status.setText(model.status());
        if (!model.guessAccepted()) {
            return;
        }
        guessesMade = 6 - model.guessesRemaining();
        roundComplete = model.completed();
        candidates.setModel(new DefaultComboBoxModel<>(model.candidates().toArray(Candidate[]::new)));
        history.setText(model.feedbackLines().isEmpty() ? ""
                : String.join("\n", model.feedbackLines()) + "\n");
        map.setEnabled(guessesMade > 0);
        mapStatus.setText(" ");
        if (model.completed()) {
            roundCompleted();
        }
    }

    /** Call after the guessing flow displays feedback for the final guess. */
    private void roundCompleted() {
        savePending = true;
        saveRound();
    }

    private void saveRound() {
        saving = true;
        status.setText("Saving result��");
        runResults(savedResults::saveCurrentRound, model -> {
            saving = false;
            if (model instanceof SavedResultsViewModel.Recap recap) {
                if (!recap.id().equals(visibleRecap)) {
                    history.append("\n" + recap.text());
                    visibleRecap = recap.id();
                }
                savePending = recap.retrySave();
                status.setText(savePending
                        ? recap.title() + " �� Save failed. Check storage and retry."
                        : recap.title());
            } else if (model instanceof SavedResultsViewModel.Status message) {
                status.setText(message.message());
            }
        });
    }

    private void openMap() {
        runResults(savedResults::openCurrentMap, model -> {
            if (model instanceof SavedResultsViewModel.Status message) {
                mapStatus.setText(message.message().isBlank() ? " " : message.message());
            }
        });
    }

    private void loadSavedHistory() {
        runHistoryAction(savedResults::showHistory);
    }

    private void runHistoryAction(Runnable action) {
        savedRounds.showStatus("Working��");
        runResults(action, model -> {
            switch (model) {
                case SavedResultsViewModel.History results -> savedRounds.showHistory(results);
                case SavedResultsViewModel.Recap recap -> savedRounds.showRecap(recap);
                case SavedResultsViewModel.Status message -> savedRounds.showStatus(message.message());
            }
        });
    }

    private void runResults(Runnable action, Consumer<SavedResultsViewModel> render) {
        setBusy(true);
        new SwingWorker<SavedResultsViewModel, Void>() {
            @Override
            protected SavedResultsViewModel doInBackground() {
                action.run();
                return resultsPresenter.viewModel();
            }

            @Override
            protected void done() {
                try {
                    render.accept(get());
                } catch (ExecutionException exception) {
                    render.accept(new SavedResultsViewModel.Status("Could not complete the action. Please retry."));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    render.accept(new SavedResultsViewModel.Status("The action was interrupted. Please retry."));
                } finally {
                    setBusy(false);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        boolean canGuess = !busy && roundActive && !roundComplete;
        start.setEnabled(!busy && !savePending);
        campuses.setEnabled(!busy && !savePending);
        candidates.setEnabled(canGuess);
        submit.setEnabled(canGuess);
        map.setEnabled(!busy && roundActive && guessesMade > 0);
        browseHistory.setEnabled(!busy);
        retrySave.setVisible(savePending);
        retrySave.setEnabled(!busy && savePending);
        savedRounds.setBusy(busy);
    }
}



