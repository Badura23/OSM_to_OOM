package primarna_cast;

import sekundarna_cast.OsmData;

public final class OSMExporter {
    private OSMExporter() {
    }

    public static void exportOnlyBuildings(OsmData osmData, String filename) throws Exception {
        OMapExporter.exportOnlyBuildings(osmData, filename);
    }
}
