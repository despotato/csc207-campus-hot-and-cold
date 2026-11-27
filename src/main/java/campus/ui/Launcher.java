package campus.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.util.function.Consumer;

/** Small start window: Explore mode plays in the browser; Classic mode opens the original Swing game. */
public final class Launcher {
    private final URI exploreAddress;
    private final Consumer<URI> openBrowser;
    private final Runnable openClassic;

    public Launcher(URI exploreAddress, Consumer<URI> openBrowser, Runnable openClassic) {
        this.exploreAddress = exploreAddress;
        this.openBrowser = openBrowser;
        this.openClassic = openClassic;
    }

    public void show() {
        JFrame frame = new JFrame("Campus Hot & Cold");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel heading = new JLabel("Campus Hot & Cold");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        content.add(heading, BorderLayout.NORTH);

        JPanel choices = new JPanel(new GridLayout(0, 1, 8, 8));
        JButton explore = new JButton("Play Explore (3D)");
        JButton classic = new JButton("Classic mode");
        JLabel status = new JLabel("Explore runs in your browser at " + exploreAddress);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        explore.addActionListener(event -> {
            try {
                openBrowser.accept(exploreAddress);
                status.setText("Opened " + exploreAddress + " in your browser.");
            } catch (RuntimeException exception) {
                status.setText("Could not open a browser. Visit " + exploreAddress);
            }
        });
        classic.addActionListener(event -> openClassic.run());
        choices.add(explore);
        choices.add(classic);
        content.add(choices, BorderLayout.CENTER);
        content.add(status, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        explore.requestFocusInWindow();
    }
}
