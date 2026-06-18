package primarnaCast;

import sekundarnaCast.Bounds;
import sekundarnaCast.Building;
import sekundarnaCast.OsmData;
import sekundarnaCast.OsmNode;

// Prevadza zemepisne suradnice (lat/lon) na OOM mapove jednotky
// 1 meter = 250 mapovych jednotiek pri mierke 1:4000
public final class CoordinateConverter {

    public static final class MapPoint {
        private final int x;
        private final int y;

        public MapPoint(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int getX() { return x; }
        public int getY() { return y; }
    }

    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;
    private static final double MAP_UNITS_PER_METER = 250.0;

    // Referencny bod — lavy horny roh oblasti (maxLat, minLon)
    private final double referenceLat;
    private final double referenceLon;

    // Korekcia dlzky stupna zemepisnej dlzky podla sirky (kosinus)
    private final double latitudeScaleCosine;

    private CoordinateConverter(double minLat, double minLon, double maxLat) {
        this.referenceLat = maxLat;
        this.referenceLon = minLon;
        double projectionLatitude = (minLat + maxLat) / 2.0;
        this.latitudeScaleCosine = Math.cos(Math.toRadians(projectionLatitude));
    }

    public static CoordinateConverter from(OsmData osmData) {
        Bounds bounds = osmData.getBounds();

        if (bounds != null) {
            return new CoordinateConverter(bounds.getMinLat(), bounds.getMinLon(), bounds.getMaxLat());
        }

        // Ak subor neobsahuje <bounds>, vypocitame hranice z uzlov budov
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

        // Poistka pre pripad prazdneho zoznamu budov
        if (!Double.isFinite(minLat)) return new CoordinateConverter(0.0, 0.0, 0.0);

        return new CoordinateConverter(minLat, minLon, maxLat);
    }

    public MapPoint convert(OsmNode node) {
        // x: rozdiel dlzky * kosinus sirky prevadza stupne na metre
        double xMeters = (node.getLon() - referenceLon) * METERS_PER_DEGREE_LATITUDE * latitudeScaleCosine;

        // y: referenceLat - lat zabezpeci ze y rastie smerom dole (juh)
        double yMeters = (referenceLat - node.getLat()) * METERS_PER_DEGREE_LATITUDE;

        int x = (int) Math.round(xMeters * MAP_UNITS_PER_METER);
        int y = (int) Math.round(yMeters * MAP_UNITS_PER_METER);
        return new MapPoint(x, y);
    }
}
