package campus.application;

import campus.domain.Landmark;

import java.io.IOException;

@FunctionalInterface
public interface LandmarkMapGateway {
    void open(Landmark landmark) throws IOException;
}
