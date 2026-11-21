package campus.ui;

import campus.adapters.SavedResultsViewModel.Entry;
import campus.adapters.SavedResultsViewModel.History;
import campus.adapters.SavedResultsViewModel.MapChoice;
import campus.adapters.SavedResultsViewModel.Recap;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

final class SavedRoundsPanel extends JPanel {
    private final JList<Entry> rounds = new JList<>();
    private final JButton reopen = new JButton("Open recap");
    private final JButton reload = new JButton("Reload");
    private final JButton back = new JButton("Back to game");
    private final JLabel status = new JLabel(" ");
    private final JTextArea recap = new JTextArea(9, 48);
    private final JComboBox<MapChoice> destinations = new JComboBox<>();
    private final JButton map = new JButton("Open on map");
    private UUID openRound;
    private boolean busy;

    SavedRoundsPanel(Runnable goBack, Runnable load, Consumer<UUID> open, BiConsumer<UUID, Long> openMap) {
        super(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(18, 18, 18, 18));
        rounds.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rounds.setVisibleRowCount(4);
        rounds.getAccessibleContext().setAccessibleName("Saved rounds");
        rounds.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                clearRecap();
                updateControls();
            }
        });
        recap.setEditable(false);
        recap.setLineWrap(true);
        recap.setWrapStyleWord(true);
        recap.getAccessibleContext().setAccessibleName("Saved round recap");

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Saved rounds"), BorderLayout.NORTH);
        top.add(new JScrollPane(rounds), BorderLayout.CENTER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEADING));
        navigation.add(back);
        navigation.add(reload);
        navigation.add(reopen);
        top.add(navigation, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(recap), BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        JPanel maps = new JPanel(new FlowLayout(FlowLayout.LEADING));
        JLabel destinationLabel = new JLabel("Landmark");
        destinationLabel.setLabelFor(destinations);
        maps.add(destinationLabel);
        maps.add(destinations);
        maps.add(map);
        footer.add(maps, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        back.addActionListener(event -> goBack.run());
        reload.addActionListener(event -> load.run());
        reopen.addActionListener(event -> {
            Entry selected = rounds.getSelectedValue();
            if (selected != null) {
                open.accept(selected.id());
            }
        });
        map.addActionListener(event -> {
            MapChoice destination = (MapChoice) destinations.getSelectedItem();
            if (openRound != null && destination != null) {
                openMap.accept(openRound, destination.id());
            }
        });
        updateControls();
    }

    void setBusy(boolean busy) {
        this.busy = busy;
        updateControls();
    }

    void showHistory(History model) {
        clearRecap();
        rounds.setListData(model.entries().toArray(Entry[]::new));
        if (!model.entries().isEmpty()) {
            rounds.setSelectedIndex(0);
        }
        showStatus(model.entries().isEmpty() ? "No saved rounds yet." : "");
        updateControls();
    }

    void showRecap(Recap model) {
        openRound = model.id();
        recap.setText(model.title() + "\n\n" + model.text());
        recap.setCaretPosition(0);
        destinations.setModel(new DefaultComboBoxModel<>(model.mapChoices().toArray(MapChoice[]::new)));
        showStatus("");
        updateControls();
    }

    void showStatus(String message) {
        status.setText(message.isBlank() ? " " : message);
    }

    private void clearRecap() {
        openRound = null;
        recap.setText("");
        destinations.removeAllItems();
    }

    private void updateControls() {
        boolean canMap = !busy && openRound != null;
        back.setEnabled(!busy);
        reload.setEnabled(!busy);
        rounds.setEnabled(!busy);
        reopen.setEnabled(!busy && rounds.getSelectedValue() != null);
        destinations.setEnabled(canMap);
        map.setEnabled(canMap);
    }
}
