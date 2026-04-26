package primarna_cast;

import sekundarna_cast.Building;
import sekundarna_cast.OsmData;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            OsmData osmData = OsmParser.parse("map.osm");
            List<Building> buildings = osmData.getBuildings();

            System.out.println("Najdene budovy: " + buildings.size());
            if (buildings.isEmpty()) {
                System.out.println("Ziadne budovy neboli najdene!");
                return;
            }

            OMapExporter.exportOnlyBuildings(osmData, "output.omap");
        } catch (Exception e) {
            System.err.println("Chyba: " + e.getMessage());
        }
    }
}
