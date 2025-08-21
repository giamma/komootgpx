package io.github.giamma.komootgpx;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

/**
 * Handles downloading and parsing Komoot tour data to create GPX tracks.
 */
public class KomootDownloader {
    private static final String USER_AGENT = "komootgpx";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    public KomootDownloader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)).followRedirects(Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Downloads a Komoot tour and converts it to a GPX Track.
     * 
     * @param url The Komoot tour URL
     * @return A Track containing the tour data
     * @throws IOException If download or parsing fails
     * @throws InterruptedException If the HTTP request is interrupted
     */
    public Track downloadTrack(String url) throws IOException, InterruptedException {
        String htmlResponse = makeHttpRequest(url);
        JsonNode tourData = extractJsonFromHtml(htmlResponse);
        return createTrackFromJson(tourData);
    }
    
    private String makeHttpRequest(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(30))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("HTTP request to " + url + " failed with status: " + response.statusCode());
        }
        
        return response.body();
    }
    
    private JsonNode extractJsonFromHtml(String html) throws IOException {
        String startMarker = "kmtBoot.setProps(\"";
        String endMarker = "\");";
        
        int start = html.indexOf(startMarker);
        if (start == -1) {
            throw new IOException("Start marker not found - this may not be a valid Komoot tour page");
        }
        start += startMarker.length();
        
        int end = html.indexOf(endMarker, start);
        if (end == -1) {
            throw new IOException("End marker not found - failed to extract tour data");
        }
        
        String jsonStr = html.substring(start, end);
        jsonStr = StringEscapeUtils.unescapeJava(jsonStr);
        
        return objectMapper.readTree(jsonStr);
    }
    
    private Track createTrackFromJson(JsonNode json) throws IOException {
        JsonNode tourNode = json.path("page").path("_embedded").path("tour");
        String tourName = tourNode.path("name").asText();
        
        if (tourName.isEmpty()) {
            throw new IOException("Tour name not found - invalid tour data");
        }
        
        JsonNode coordsNode = tourNode.path("_embedded").path("coordinates").path("items");
        
        if (!coordsNode.isArray()) {
            throw new IOException("Coordinates are not an array - invalid tour data structure");
        }
        
        List<WayPoint> waypoints = new ArrayList<>();
        
        for (JsonNode coord : coordsNode) {
            double lat = getCoordinateValue(coord, "lat");
            double lng = getCoordinateValue(coord, "lng");
            double alt = getCoordinateValue(coord, "alt");
            
            WayPoint waypoint = WayPoint.builder()
                .lat(lat)
                .lon(lng)
                .ele(alt)
                .build();
            waypoints.add(waypoint);
        }
        
        if (waypoints.isEmpty()) {
            throw new IOException("No valid coordinates found in tour data");
        }
        
        TrackSegment segment = TrackSegment.of(waypoints);
        return Track.builder().name(tourName).addSegment(segment).build();
    }
    
    private double getCoordinateValue(JsonNode coord, String key) throws IOException {
        JsonNode node = coord.path(key);
        if (!node.isNumber()) {
            throw new IOException(key + " coordinate is not a valid number");
        }
        return node.asDouble();
    }
}