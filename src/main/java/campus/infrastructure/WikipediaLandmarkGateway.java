package campus.infrastructure;

import campus.application.LandmarkGateway;
import campus.domain.Campus;
import campus.domain.Landmark;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WikipediaLandmarkGateway implements LandmarkGateway {
    private static final String ENDPOINT = "https://en.wikipedia.org/w/api.php";
    private final HttpClient client;

    public WikipediaLandmarkGateway(HttpClient client) {
        this.client = client;
    }

    @Override
    public List<Landmark> load(Campus campus) throws IOException, InterruptedException {
        String titles = URLEncoder.encode(String.join("|", campus.articleTitles()),
                StandardCharsets.UTF_8);
        URI uri = URI.create(ENDPOINT + "?action=query&prop=coordinates&redirects=1"
                + "&coprimary=primary&colimit=max&format=xml&titles=" + titles);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", "CampusHotAndCold/0.1 (CSC207 classroom project)")
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Wikipedia returned HTTP " + response.statusCode() + ".");
        }
        return parse(response.body());
    }

    private List<Landmark> parse(byte[] body) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
            if (document.getElementsByTagName("error").getLength() > 0) {
                throw new IOException("Wikipedia could not complete the landmark query.");
            }

            Map<Long, Landmark> landmarks = new HashMap<>();
            NodeList pages = document.getElementsByTagName("page");
            for (int index = 0; index < pages.getLength(); index++) {
                Element page = (Element) pages.item(index);
                NodeList coordinates = page.getElementsByTagName("co");
                for (int pointIndex = 0; pointIndex < coordinates.getLength(); pointIndex++) {
                    Element point = (Element) coordinates.item(pointIndex);
                    if (point.hasAttribute("primary") && "earth".equals(point.getAttribute("globe"))) {
                        long id = Long.parseLong(page.getAttribute("pageid"));
                        landmarks.put(id, new Landmark(id, page.getAttribute("title"),
                                Double.parseDouble(point.getAttribute("lat")),
                                Double.parseDouble(point.getAttribute("lon"))));
                        break;
                    }
                }
            }
            return landmarks.values().stream().sorted(Comparator.comparing(Landmark::name)).toList();
        } catch (ParserConfigurationException | SAXException | NumberFormatException exception) {
            throw new IOException("Wikipedia returned unreadable landmark data.", exception);
        }
    }
}
