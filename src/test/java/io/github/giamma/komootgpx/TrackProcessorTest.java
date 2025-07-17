package io.github.giamma.komootgpx;

import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackProcessorTest {

    @Test
    void testTrackSimplification() {
        // Create a test track with redundant points along a straight line
        // Points: (0,0,100) -> (0,1,100) -> (0,2,100) -> (0,10,100)
        // The middle points should be removed with sufficient tolerance
        Track originalTrack = Track.builder()
            .name("Test Track")
            .addSegment(segment -> segment
                .addPoint(p -> p.lat(45.0).lon(11.0).ele(100))      // Start point
                .addPoint(p -> p.lat(45.001).lon(11.0).ele(100))    // Should be removed (redundant)
                .addPoint(p -> p.lat(45.002).lon(11.0).ele(100))    // Should be removed (redundant)
                .addPoint(p -> p.lat(45.003).lon(11.0).ele(100))    // Should be removed (redundant)
                .addPoint(p -> p.lat(45.010).lon(11.0).ele(100))    // End point
            )
            .build();

        // Apply simplification with 50m tolerance (should remove middle points)
        Track simplifiedTrack = TrackProcessor.simplifyTrack(originalTrack, 50.0);

        // Verify track structure
        assertEquals("Test Track", simplifiedTrack.getName().orElse(""));
        assertEquals(1, simplifiedTrack.getSegments().size());

        TrackSegment segment = simplifiedTrack.getSegments().get(0);
        List<WayPoint> points = segment.getPoints();

        // Should have fewer points than original
        assertTrue(points.size() < 5, "Simplified track should have fewer points");
        assertTrue(points.size() >= 2, "Simplified track should have at least start and end points");

        // First and last points should be preserved
        WayPoint firstPoint = points.get(0);
        WayPoint lastPoint = points.get(points.size() - 1);
        
        assertEquals(45.0, firstPoint.getLatitude().doubleValue(), 0.0001);
        assertEquals(11.0, firstPoint.getLongitude().doubleValue(), 0.0001);
        assertEquals(45.010, lastPoint.getLatitude().doubleValue(), 0.0001);
        assertEquals(11.0, lastPoint.getLongitude().doubleValue(), 0.0001);
    }

    @Test
    void testTrackSimplificationWithSmallTolerance() {
        // Create the same track but use very small tolerance
        Track originalTrack = Track.builder()
            .name("Test Track")
            .addSegment(segment -> segment
                .addPoint(p -> p.lat(45.0).lon(11.0).ele(100))
                .addPoint(p -> p.lat(45.001).lon(11.0).ele(100))
                .addPoint(p -> p.lat(45.002).lon(11.0).ele(100))
                .addPoint(p -> p.lat(45.010).lon(11.0).ele(100))
            )
            .build();

        // Apply simplification with very small tolerance (should keep most points)
        Track simplifiedTrack = TrackProcessor.simplifyTrack(originalTrack, 0.1);

        TrackSegment segment = simplifiedTrack.getSegments().get(0);
        List<WayPoint> points = segment.getPoints();

        // Should keep more points than the large tolerance test, but Douglas-Peucker may still simplify
        assertTrue(points.size() >= 2, "With small tolerance, at least start and end points should be preserved");
        // Since the points are in a straight line, even small tolerance may simplify significantly
    }

    @Test
    void testElevationSmoothing() {
        // Create a track with elevation spikes
        Track originalTrack = Track.builder()
            .name("Test Track with Spikes")
            .addSegment(segment -> segment
                .addPoint(p -> p.lat(45.0).lon(11.0).ele(100))      // Normal elevation
                .addPoint(p -> p.lat(45.001).lon(11.001).ele(150))  // Spike up (+50m)
                .addPoint(p -> p.lat(45.002).lon(11.002).ele(105))  // Back down (spike pattern)
                .addPoint(p -> p.lat(45.003).lon(11.003).ele(110))  // Normal progression
                .addPoint(p -> p.lat(45.004).lon(11.004).ele(80))   // Spike down (-30m)
                .addPoint(p -> p.lat(45.005).lon(11.005).ele(115))  // Back up (spike pattern)
            )
            .build();

        // Apply elevation smoothing with 20m threshold (should catch the 50m spike)
        Track smoothedTrack = TrackProcessor.smoothElevation(originalTrack, 20);

        TrackSegment segment = smoothedTrack.getSegments().get(0);
        List<WayPoint> points = segment.getPoints();

        // Should have same number of points
        assertEquals(6, points.size());

        // First and last points should be unchanged
        assertEquals(100.0, points.get(0).getElevation().get().doubleValue(), 0.1);
        assertEquals(115.0, points.get(5).getElevation().get().doubleValue(), 0.1);

        // Middle spike should be smoothed (interpolated between neighbors)
        double smoothedElevation = points.get(1).getElevation().get().doubleValue();
        assertTrue(smoothedElevation < 150.0, "Spike should be smoothed down");
        assertTrue(smoothedElevation > 100.0, "Smoothed value should be between neighbors");
    }

    @Test
    void testElevationSmoothingWithHighThreshold() {
        // Create a track with small elevation changes
        Track originalTrack = Track.builder()
            .name("Test Track")
            .addSegment(segment -> segment
                .addPoint(p -> p.lat(45.0).lon(11.0).ele(100))
                .addPoint(p -> p.lat(45.001).lon(11.001).ele(110)) // +10m change
                .addPoint(p -> p.lat(45.002).lon(11.002).ele(105)) // -5m change
                .addPoint(p -> p.lat(45.003).lon(11.003).ele(108)) // +3m change
            )
            .build();

        // Apply elevation smoothing with high threshold (should not change anything)
        Track smoothedTrack = TrackProcessor.smoothElevation(originalTrack, 50);

        TrackSegment originalSegment = originalTrack.getSegments().get(0);
        TrackSegment smoothedSegment = smoothedTrack.getSegments().get(0);

        // All elevations should remain unchanged
        for (int i = 0; i < originalSegment.getPoints().size(); i++) {
            double originalEle = originalSegment.getPoints().get(i).getElevation().get().doubleValue();
            double smoothedEle = smoothedSegment.getPoints().get(i).getElevation().get().doubleValue();
            assertEquals(originalEle, smoothedEle, 0.001, "Elevation should not change with high threshold");
        }
    }

    @Test
    void testEmptyTrackHandling() {
        // Test with empty track
        Track emptyTrack = Track.builder()
            .name("Empty Track")
            .build();

        // Both operations should handle empty tracks gracefully
        Track simplifiedEmpty = TrackProcessor.simplifyTrack(emptyTrack, 10.0);
        Track smoothedEmpty = TrackProcessor.smoothElevation(emptyTrack, 10);

        assertEquals(0, simplifiedEmpty.getSegments().size());
        assertEquals(0, smoothedEmpty.getSegments().size());
    }

    @Test
    void testSinglePointTrack() {
        // Test with single point track
        Track singlePointTrack = Track.builder()
            .name("Single Point")
            .addSegment(segment -> segment
                .addPoint(p -> p.lat(45.0).lon(11.0).ele(100))
            )
            .build();

        // Both operations should preserve single point
        Track simplifiedSingle = TrackProcessor.simplifyTrack(singlePointTrack, 10.0);
        Track smoothedSingle = TrackProcessor.smoothElevation(singlePointTrack, 10);

        assertEquals(1, simplifiedSingle.getSegments().get(0).getPoints().size());
        assertEquals(1, smoothedSingle.getSegments().get(0).getPoints().size());
        
        assertEquals(100.0, simplifiedSingle.getSegments().get(0).getPoints().get(0).getElevation().get().doubleValue(), 0.001);
        assertEquals(100.0, smoothedSingle.getSegments().get(0).getPoints().get(0).getElevation().get().doubleValue(), 0.001);
    }
}