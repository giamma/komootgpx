package io.github.giamma.komootgpx;

import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;
import org.locationtech.jts.algorithm.distance.DistanceToPoint;
import org.locationtech.jts.algorithm.distance.PointPairDistance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class TrackProcessor {
    private static final Logger logger = Logger.getLogger(TrackProcessor.class.getName());
    private static final GeometryFactory geometryFactory = new GeometryFactory();
    
    public static Track simplifyTrack(Track track, double toleranceMeters) {
        logger.info("Simplifying track with tolerance " + toleranceMeters + "m");
        
        Track.Builder trackBuilder = track.toBuilder();
        List<TrackSegment> simplifiedSegments = new ArrayList<>();
        
        int originalPoints = 0;
        int simplifiedPoints = 0;
        
        for (TrackSegment segment : track.getSegments()) {
            List<WayPoint> points = segment.getPoints();
            originalPoints += points.size();
            
            if (points.size() < 3) {
                simplifiedSegments.add(segment);
                simplifiedPoints += points.size();
                continue;
            }
            
            // Convert to JTS coordinates
            Coordinate[] coords = points.stream()
                .map(wp -> new Coordinate(wp.getLongitude().doubleValue(), wp.getLatitude().doubleValue()))
                .toArray(Coordinate[]::new);
            
            LineString lineString = geometryFactory.createLineString(coords);
            
            // Convert tolerance from meters to degrees (rough approximation)
            double toleranceDegrees = toleranceMeters / 111000.0; // ~111km per degree
            
            // Simplify using Douglas-Peucker
            LineString simplified = (LineString) DouglasPeuckerSimplifier.simplify(lineString, toleranceDegrees);
            
            // Convert back to WayPoints
            List<WayPoint> segmentPoints = new ArrayList<>();
            for (Coordinate coord : simplified.getCoordinates()) {
                // Find the original waypoint that matches this coordinate
                WayPoint original = findClosestWayPoint(points, coord);
                segmentPoints.add(original);
            }
            
            simplifiedPoints += segmentPoints.size();
            simplifiedSegments.add(TrackSegment.of(segmentPoints));
        }
        
        logger.info("Track simplified: " + originalPoints + " → " + simplifiedPoints + " points (" + 
                   String.format("%.1f", (1.0 - (double)simplifiedPoints/originalPoints) * 100) + "% reduction)");
        
        return trackBuilder.segments(simplifiedSegments).build();
    }
    
    public static Track smoothElevation(Track track, int thresholdMeters) {
        logger.info("Smoothing elevation with threshold " + thresholdMeters + "m");
        
        Track.Builder trackBuilder = track.toBuilder();
        List<TrackSegment> smoothedSegments = new ArrayList<>();
        
        int totalSpikesRemoved = 0;
        
        for (TrackSegment segment : track.getSegments()) {
            List<WayPoint> points = segment.getPoints();
            
            if (points.size() < 3) {
                smoothedSegments.add(segment);
                continue;
            }
            
            List<WayPoint> smoothedPoints = new ArrayList<>();
            smoothedPoints.add(points.get(0)); // Always keep first point
            
            for (int i = 1; i < points.size() - 1; i++) {
                WayPoint prev = points.get(i - 1);
                WayPoint curr = points.get(i);
                WayPoint next = points.get(i + 1);
                
                if (prev.getElevation().isPresent() && curr.getElevation().isPresent() && next.getElevation().isPresent()) {
                    double prevElev = prev.getElevation().get().doubleValue();
                    double currElev = curr.getElevation().get().doubleValue();
                    double nextElev = next.getElevation().get().doubleValue();
                    
                    // Check if current point is a spike
                    boolean isSpike = false;
                    double spikeUp = Math.abs(currElev - prevElev);
                    double spikeDown = Math.abs(nextElev - currElev);
                    
                    // Spike detection: large elevation change up and then down (or vice versa)
                    if (spikeUp > thresholdMeters && spikeDown > thresholdMeters) {
                        // Check if it goes up then down or down then up
                        boolean upThenDown = (currElev > prevElev && currElev > nextElev);
                        boolean downThenUp = (currElev < prevElev && currElev < nextElev);
                        
                        if (upThenDown || downThenUp) {
                            isSpike = true;
                            totalSpikesRemoved++;
                        }
                    }
                    
                    if (!isSpike) {
                        smoothedPoints.add(curr);
                    } else {
                        // Replace spike with interpolated elevation
                        double interpolatedElev = (prevElev + nextElev) / 2.0;
                        WayPoint smoothed = curr.toBuilder()
                            .ele(interpolatedElev)
                            .build();
                        smoothedPoints.add(smoothed);
                    }
                } else {
                    smoothedPoints.add(curr);
                }
            }
            
            smoothedPoints.add(points.get(points.size() - 1)); // Always keep last point
            smoothedSegments.add(TrackSegment.of(smoothedPoints));
        }
        
        logger.info("Elevation smoothed: " + totalSpikesRemoved + " spikes removed/smoothed");
        
        return trackBuilder.segments(smoothedSegments).build();
    }
    
    private static WayPoint findClosestWayPoint(List<WayPoint> wayPoints, Coordinate coord) {
        WayPoint closest = wayPoints.get(0);
        double minDistance = Double.MAX_VALUE;
        
        for (WayPoint wp : wayPoints) {
            double distance = Math.sqrt(
                Math.pow(wp.getLongitude().doubleValue() - coord.x, 2) +
                Math.pow(wp.getLatitude().doubleValue() - coord.y, 2)
            );
            
            if (distance < minDistance) {
                minDistance = distance;
                closest = wp;
            }
        }
        
        return closest;
    }
}