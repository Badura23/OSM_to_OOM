package primarna_cast;

import sekundarna_cast.Building;
import sekundarna_cast.OsmData;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Nacitaj OSM
            OsmData osmData = OsmParser.parse("map.osm");

            // 2. Najst budovy
            List<Building> buildings = osmData.getBuildings();
            System.out.println("Najdene budovy: " + buildings.size());
            if (buildings.isEmpty()) {
                System.out.println("Ziadne budovy neboli najdene!");
                return;
            }

            // 3. Export
            String outputFile = "output.osm";
            OSMExporter.exportOnlyBuildings(osmData, outputFile);

        } catch (Exception e) {
            System.err.println("Chyba: " + e.getMessage());
        }
    }
}