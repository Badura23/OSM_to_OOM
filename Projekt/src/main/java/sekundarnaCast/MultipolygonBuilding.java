package sekundarnaCast;

import java.util.List;

// Budova definovana OSM multipolygon relaciou — ma vonkajsi obrys a vnutorne nadvoria (prikladom takejto budovy su atriove domy)
public class MultipolygonBuilding {

    private final List<OsmNode> outerRing;
    private final List<List<OsmNode>> innerRings;

    public MultipolygonBuilding(List<OsmNode> outerRing, List<List<OsmNode>> innerRings) {
        this.outerRing = outerRing;
        this.innerRings = innerRings;
    }

    public List<OsmNode> getOuterRing() {
        return outerRing;
    }

    // Moze byt prazdny zoznam ak budova nema nadvoria
    public List<List<OsmNode>> getInnerRings() {
        return innerRings;
    }
}
