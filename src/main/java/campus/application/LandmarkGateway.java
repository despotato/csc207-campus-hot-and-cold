package campus.application;

import campus.domain.Campus;
import campus.domain.Landmark;

import java.io.IOException;
import java.util.List;

public interface LandmarkGateway {
    List<Landmark> load(Campus campus) throws IOException, InterruptedException;
}
