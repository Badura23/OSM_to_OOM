package sekundarna_cast;

import java.util.List;

/**
 * Reprezintuje budovu definovanu OSM multipolygon relacia.
 *
 * <p>OSM multipolygon ma jeden alebo viac vonkajsich kruhov (outer)
 * a nula alebo viac vnutornych kruhov (inner). Pre budovy s nadroriami
 * (napr. Atriove domky) je vonkajsi kruh obrys celeho komplexu a
 * vnutorne kruhy su plochy nadvorii, ktore sa na mape nakreslia
 * ako biele diery v ploche budovy.
 */
public class MultipolygonBuilding {

    private final List<OsmNode> outerRing;
    private final List<List<OsmNode>> innerRings;

    public MultipolygonBuilding(List<OsmNode> outerRing, List<List<OsmNode>> innerRings) {
        this.outerRing = outerRing;
        this.innerRings = innerRings;
    }

    /** Vonkajsi kruh — obrys celej budovy. */
    public List<OsmNode> getOuterRing() {
        return outerRing;
    }

    /** Vnutorne kruhy — nadvoria/otvory v budove (moze byt prazdny zoznam). */
    public List<List<OsmNode>> getInnerRings() {
        return innerRings;
    }
}