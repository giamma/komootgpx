# komootgpx

Extract GPX tracks from Komoot tours without requiring region unlock. Uses the map data to generate standard GPX files with automatic elevation gain calculation.

## Build

```bash
mvn clean package
```

This creates an executable jar: `target/komootgpx-<VERSION>.jar`

## Usage

### Run with jar
```bash
java -jar target/komootgpx-<VERSION>.jar [OPTIONS] <URL>
```

### Options
- `-o, --output <FILE>` - Output GPX file (default: uses tour name as filename)
- `-s, --simplify` - Simplify track using Douglas-Peucker algorithm (default: 5.0m)
- `--simplify-tolerance <METERS>` - Custom tolerance for track simplification
- `-e, --smooth-elevation` - Smooth elevation data by removing GPS spikes (default: 10m)  
- `--elevation-threshold <METERS>` - Custom threshold for elevation smoothing
- `-h, --help` - Show help

*See [Parameter Details](#parameter-details) below for guidance on choosing values.*

### Auto-Generated Filenames

When no output file is specified (`-o` option), filenames are automatically generated using this pattern:
```
{TourName}_D{ElevationGain}m[_simplified][_smoothed].gpx
```

- **TourName**: Sanitized tour name from Komoot (spaces become underscores)
- **D{ElevationGain}m**: Total vertical ascent in meters (e.g., D1568m)
- **_simplified**: Added when track simplification is applied
- **_smoothed**: Added when elevation smoothing is applied

This allows easy comparison of file sizes and processing effects across multiple runs.

### Examples
```bash
# Basic usage - auto-generate filename with elevation gain
# Creates: "Tour_Name_D1234m.gpx"
java -jar target/komootgpx-<VERSION>.jar 'https://www.komoot.com/tour/123456'

# Simplify track with default tolerance (5.0m)
# Creates: "Tour_Name_D1234m_simplified.gpx"
java -jar target/komootgpx-<VERSION>.jar --simplify 'https://www.komoot.com/tour/123456'

# Smooth elevation with default threshold (10m)
# Creates: "Tour_Name_D1234m_smoothed.gpx"
java -jar target/komootgpx-<VERSION>.jar --smooth-elevation 'https://www.komoot.com/tour/123456'

# Both processing options with custom values
# Creates: "Tour_Name_D1234m_simplified_smoothed.gpx"
java -jar target/komootgpx-<VERSION>.jar --simplify --simplify-tolerance 10.0 --smooth-elevation 'https://www.komoot.com/tour/123456'

# Output to stdout (no logging)
java -jar target/komootgpx-<VERSION>.jar -o - 'https://www.komoot.com/tour/123456'

# Save to specific file with processing
java -jar target/komootgpx-<VERSION>.jar -o route.gpx --simplify --smooth-elevation 'https://www.komoot.com/tour/123456'
```

## Requirements

- Java 11+
- Maven 3.6+

## Parameter Details

### Track Simplification (`--simplify`)

**What it does:** Uses the Douglas-Peucker algorithm to remove redundant GPS points while preserving the overall track shape.

**How it works:** Removes points that are within the specified tolerance distance (in meters) from a straight line between neighboring "important" points.

**Recommended values:**
- **1-3m**: Very conservative, removes only truly redundant points, minimal visual change
- **5-10m**: Good balance, removes unnecessary detail while preserving track shape *(default: 5.0m)*
- **15-20m**: More aggressive, significant file size reduction, slight smoothing of curves  
- **30-50m**: Very aggressive, major simplification, may lose some detail on tight turns

**Perfect for:** Reducing file sizes, removing GPS jitter, preparing tracks for GPS devices with limited memory.

### Elevation Smoothing (`--smooth-elevation`)

**What it does:** Detects and removes unrealistic elevation spikes caused by GPS errors while preserving legitimate terrain features.

**How it works:** Identifies points where elevation changes dramatically up/down and then immediately down/up between consecutive GPS points, replacing spikes with interpolated values.

**Recommended values:**
- **10-15m**: Conservative, removes only obvious GPS errors *(default: 10m)*
- **20-30m**: Moderate, handles typical GPS elevation noise
- **40-50m**: Aggressive, may remove some legitimate steep terrain
- **100m+**: Very aggressive, only for very noisy GPS data

**Context:** Consumer GPS elevation accuracy is typically ±10-30m, making 10m a safe threshold for removing obvious errors while preserving real terrain features.

**Perfect for:** Mountain biking, hiking, and activities where elevation accuracy matters for analysis and route planning.

## Credits

*Inspired by the original [komootgpx](https://github.com/cdown/komootgpx) by Chris Down.*

This Java implementation extends the original concept with track processing, elevation analysis, and enhanced user experience while maintaining the core functionality of extracting GPX data from Komoot tours.

## License

MIT