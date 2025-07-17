package io.github.giamma.komootgpx;

import java.io.IOException;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

public class KomootGpx {
    private static final String GPX_CREATOR = "komootgpx";
    private static final Logger logger = Logger.getLogger(KomootGpx.class.getName());

    public static void main(String[] args) {
        try {
            Args parsedArgs = parseArgs(args);

            // Setup logging (disable for stdout output)
            setupLogging(!"-".equals(parsedArgs.output));

            logger.info("Downloading tour from Komoot...");
            KomootDownloader downloader = new KomootDownloader();
            Track track = downloader.downloadTrack(parsedArgs.url);

            // Apply processing if requested
            boolean wasSimplified = false;
            boolean wasSmoothed = false;

            if (parsedArgs.simplifyTolerance != null) {
                track = TrackProcessor.simplifyTrack(track, parsedArgs.simplifyTolerance);
                wasSimplified = true;
            }

            if (parsedArgs.elevationThreshold != null) {
                track = TrackProcessor.smoothElevation(track, parsedArgs.elevationThreshold);
                wasSmoothed = true;
            }

            writeGpx(track, parsedArgs.output, track.getName().orElse("track"), wasSimplified, wasSmoothed);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void setupLogging(boolean enableLogging) {
        Logger rootLogger = Logger.getLogger("");

        // Remove default handlers
        rootLogger.getHandlers()[0].setLevel(Level.OFF);

        if (enableLogging) {
            ConsoleHandler handler = new ConsoleHandler();
            handler.setLevel(Level.INFO);
            handler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return record.getMessage() + "\n";
                }
            });

            rootLogger.addHandler(handler);
            rootLogger.setLevel(Level.INFO);
        } else {
            rootLogger.setLevel(Level.OFF);
        }
    }

    private static Args parseArgs(String[] args) throws ParseException {
        Options options = new Options();

        options.addOption(Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("FILE")
                .desc("The GPX file to create. By default, uses tour name as filename")
                .build());

        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Show this help message")
                .build());

        options.addOption(Option.builder("s")
                .longOpt("simplify")
                .hasArg(false)
                .desc("Simplify track using Douglas-Peucker algorithm (default: 5.0m)")
                .build());

        options.addOption(Option.builder()
                .longOpt("simplify-tolerance")
                .hasArg()
                .argName("METERS")
                .desc("Custom tolerance for track simplification in meters")
                .build());

        options.addOption(Option.builder("e")
                .longOpt("smooth-elevation")
                .hasArg(false)
                .desc("Smooth elevation data by removing GPS spikes (default: 10m)")
                .build());

        options.addOption(Option.builder()
                .longOpt("elevation-threshold")
                .hasArg()
                .argName("METERS")
                .desc("Custom threshold for elevation smoothing in meters")
                .build());

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        if (cmd.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("komootgpx [OPTIONS] <URL>",
                "Get GPX files from Komoot", options, "");
            System.exit(0);
        }

        String[] remainingArgs = cmd.getArgs();
        if (remainingArgs.length != 1) {
            throw new ParseException("Exactly one URL argument is required");
        }

        Double simplifyTolerance = null;
        if (cmd.hasOption("simplify")) {
            simplifyTolerance = cmd.hasOption("simplify-tolerance")
                ? parseDoubleValue(cmd.getOptionValue("simplify-tolerance"), "simplify-tolerance")
                : 5.0;
        }

        Integer elevationThreshold = null;
        if (cmd.hasOption("smooth-elevation")) {
            elevationThreshold = cmd.hasOption("elevation-threshold")
                ? parseIntValue(cmd.getOptionValue("elevation-threshold"), "elevation-threshold")
                : 10;
        }

        return new Args(
            remainingArgs[0],
            cmd.getOptionValue("output"),
            simplifyTolerance,
            elevationThreshold
        );
    }

    private static Double parseDoubleValue(String value, String optionName) throws ParseException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid number format for --" + optionName + ": " + value);
        }
    }

    private static Integer parseIntValue(String value, String optionName) throws ParseException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ParseException("Invalid integer format for --" + optionName + ": " + value);
        }
    }

    private static void writeGpx(Track track, String outputFile, String tourName, boolean wasSimplified, boolean wasSmoothed) throws IOException {
        GPX gpx = GPX.builder()
                .creator(GPX_CREATOR)
                .version(GPX.Version.V11)
                .addTrack(track)
                .build();

        if (outputFile == null) {
            int elevationGain = calculateElevationGain(track);
            StringBuilder filename = new StringBuilder();
            filename.append(sanitizeFilename(tourName));
            filename.append("_D").append(elevationGain).append("m");

            if (wasSimplified) {
                filename.append("_simplified");
            }
            if (wasSmoothed) {
                filename.append("_smoothed");
            }

            filename.append(".gpx");
            GPX.write(gpx, java.nio.file.Paths.get(filename.toString()));
        } else if ("-".equals(outputFile)) {
            GPX.Writer.DEFAULT.write(gpx, System.out);
        } else {
            GPX.write(gpx, java.nio.file.Paths.get(outputFile));
        }
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static int calculateElevationGain(Track track) {
        double totalGain = 0.0;

        for (TrackSegment segment : track.getSegments()) {
            List<WayPoint> points = segment.getPoints();
            for (int i = 1; i < points.size(); i++) {
                WayPoint prev = points.get(i - 1);
                WayPoint curr = points.get(i);

                if (prev.getElevation().isPresent() && curr.getElevation().isPresent()) {
                    double prevElev = prev.getElevation().get().doubleValue();
                    double currElev = curr.getElevation().get().doubleValue();
                    double diff = currElev - prevElev;

                    if (diff > 0) {
                        totalGain += diff;
                    }
                }
            }
        }

        return (int) Math.round(totalGain);
    }

    private static class Args {
        final String url;
        final String output;
        final Double simplifyTolerance;
        final Integer elevationThreshold;

        Args(String url, String output, Double simplifyTolerance, Integer elevationThreshold) {
            this.url = url;
            this.output = output;
            this.simplifyTolerance = simplifyTolerance;
            this.elevationThreshold = elevationThreshold;
        }
    }
}