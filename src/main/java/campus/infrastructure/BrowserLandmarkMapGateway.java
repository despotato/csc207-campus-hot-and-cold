package campus.infrastructure;

import campus.application.LandmarkMapGateway;
import campus.domain.Landmark;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;

public final class BrowserLandmarkMapGateway implements LandmarkMapGateway {
    @Override
    public void open(Landmark landmark) throws IOException {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Browser launch unavailable.");
            }
            URI url = URI.create("https://www.google.com/maps/search/?api=1&query="
                    + landmark.latitude() + "," + landmark.longitude());
            Desktop.getDesktop().browse(url);
        } catch (UnsupportedOperationException | SecurityException exception) {
            throw new IOException("Browser launch unavailable.", exception);
        }
    }
}
