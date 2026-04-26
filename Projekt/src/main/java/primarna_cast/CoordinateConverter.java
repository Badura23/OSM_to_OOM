package primarna_cast;

import sekundarna_cast.Bounds;
import sekundarna_cast.Building;
import sekundarna_cast.OsmData;
import sekundarna_cast.OsmNode;

import java.util.Locale;

public final class CoordinateConverter {
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;
    private static final double MAP_UNITS_PER_METER = 250.0;
    private static final int DEFAULT_PADDING = 1000;

    private final double referenceLat;
    private final double referenceLon;
    private final double latitudeScaleCosine;
    private final double projectionLatitude;
    private final int referenceX;
    private final int referenceY;

    private CoordinateConverter(double minLat, double minLon, double maxLat) {
        this.referenceLat = maxLat;
        this.referenceLon = minLon;
        this.projectionLatitude = (minLat + maxLat) / 2.0;
        this.latitudeScaleCosine = Math.cos(Math.toRadians(projectionLatitude));
        this.referenceX = DEFAULT_PADDING;
        this.referenceY = DEFAULT_PADDING;
    }

    public static CoordinateConverter from(OsmData osmData) {
        Bounds bounds = osmData.getBounds();
        if (bounds != null) {
            return new CoordinateConverter(
                    bounds.getMinLat(),
                    bounds.getMinLon(),
                    bounds.getMaxLat()
            );
        }

        double minLat = Double.POSITIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;

        for (Building building : osmData.getBuildings()) {
            for (OsmNode node : building.getNodes()) {
                minLat = Math.min(minLat, node.getLat());
                minLon = Math.min(minLon, node.getLon());
                maxLat = Math.max(maxLat, node.getLat());
            }
        }

        if (!Double.isFinite(minLat)) {
            return new CoordinateConverter(0.0, 0.0, 0.0);
        }

        return new CoordinateConverter(minLat, minLon, maxLat);
    }

    public MapPoint convert(OsmNode node) {
        double xMeters = (node.getLon() - referenceLon) * METERS_PER_DEGREE_LATITUDE * latitudeScaleCosine;
        double yMeters = (referenceLat - node.getLat()) * METERS_PER_DEGREE_LATITUDE;

        int x = referenceX + (int) Math.round(xMeters * MAP_UNITS_PER_METER);
        int y = referenceY + (int) Math.round(yMeters * MAP_UNITS_PER_METER);
        return new MapPoint(x, y);
    }

    public int getReferenceX() {
        return referenceX;
    }

    public int getReferenceY() {
        return referenceY;
    }

    public double getReferenceLat() {
        return referenceLat;
    }

    public double getReferenceLon() {
        return referenceLon;
    }

    public String getProjectedCrsSpec() {
        return String.format(
                Locale.US,
                "+proj=eqc +datum=WGS84 +lat_ts=%.8f +lat_0=%.8f +lon_0=%.8f +units=m",
                projectionLatitude,
                referenceLat,
                referenceLon
        );
    }

    public static final class MapPoint {
        private final int x;
        private final int y;

        public MapPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }
    }
}
