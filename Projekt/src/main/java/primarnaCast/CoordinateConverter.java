package primarnaCast;

import sekundarnaCast.Bounds;
import sekundarnaCast.Building;
import sekundarnaCast.OsmData;
import sekundarnaCast.OsmNode;

public final class CoordinateConverter {

    // Vysledny bod v suradniciach pouzivanych v .omap subore
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

    // Priblizny pocet metrov na jeden stupen zemepisnej sirky
    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

    // interne mapove jednotky, pre OOM
    private static final double MAP_UNITS_PER_METER = 250.0;

    // Referencny bod - lavy horny roh oblasti
    private final double referenceLat;
    private final double referenceLon;

    // Korekcia dlzky jedneho stupna zemepisnej dlzky podla sirky
    private final double latitudeScaleCosine;

    private CoordinateConverter(double minLat, double minLon, double maxLat) {
        /*
         * Ako referencny geograficky bod pouzivame lavy horny roh oblasti:
         * - maxLat = najsevernejsi bod
         * - minLon = najzapadnejsi bod
         *
         * Potom budu suradnice x rast doprava a y rast smerom dole.
         */
        this.referenceLat = maxLat;
        this.referenceLon = minLon;
        double projectionLatitude = (minLat + maxLat) / 2.0;
        this.latitudeScaleCosine = Math.cos(Math.toRadians(projectionLatitude));
    }

    // Vytvori converter podla hranic OSM dat
    public static CoordinateConverter from(OsmData osmData) {
        Bounds bounds = osmData.getBounds();

        /*
         * Export priamo z OpenStreetMap vacsinou obsahuje element <bounds>,
         * ktory udava hranice exportovanej oblasti.
         *
         * Pri rucne vytvorenom .osm subore, alebo pri
         * subore vytvorenom inym nastrojom vsak <bounds> nemusi existovat.
         *
         * Preto najprv pouzijeme bounds, ak su dostupne. Ak nie su dostupne,
         * hranice oblasti si vypocitame z bodov najdenych budov.
         */
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

        // Ak bounds nie su v subore, vypocitaju sa z bodov budov.
        for (Building building : osmData.getBuildings()) {
            for (OsmNode node : building.getNodes()) {
                minLat = Math.min(minLat, node.getLat());
                minLon = Math.min(minLon, node.getLon());
                maxLat = Math.max(maxLat, node.getLat());
            }
        }


        /*
         * Poistka pre pripad, ze sa nenasla ziadna budova.
         *
         * Ak by zoznam budov bol prazdny, hodnoty minLat/minLon/maxLat by zostali
         * nekonecne hodnoty (+Infinity alebo -Infinity). S takymi hodnotami by
         * dalsie vypocty suradnic nedavali zmysel.
         *
         * V beznom behu programu by sa tato vetva nemala pouzit, lebo Main
         * zvycajne kontroluje, ci boli najdene nejake budovy.
         */
        if (!Double.isFinite(minLat)) {
            return new CoordinateConverter(0.0, 0.0, 0.0);
        }

        return new CoordinateConverter(minLat, minLon, maxLat);
    }

    // Prevedie OSM bod z lat/lon na lokalne .omap suradnice x/y
    public MapPoint convert(OsmNode node) {

        /*
         * X suradnica:
         * - zoberieme rozdiel v zemepisnej dlzke oproti referencnemu bodu,
         * - prevedieme stupne na metre,
         * - opravime to kosinom zemepisnej sirky, lebo jeden stupen dlzky
         *   je na Slovensku kratsi ako na rovniku.
         */

        double xMeters = (node.getLon() - referenceLon) * METERS_PER_DEGREE_LATITUDE * latitudeScaleCosine;


        /*
         * Y suradnica:
         * - referenceLat je horny okraj oblasti,
         * - ked je bod juznejsie, ma mensiu latitude,
         * - preto pouzijeme referenceLat - nodeLat, aby y rastlo smerom dole.
         */

        double yMeters = (referenceLat - node.getLat()) * METERS_PER_DEGREE_LATITUDE;

        int x = (int) Math.round(xMeters * MAP_UNITS_PER_METER);
        int y = (int) Math.round(yMeters * MAP_UNITS_PER_METER);
        return new MapPoint(x, y);
    }

}
