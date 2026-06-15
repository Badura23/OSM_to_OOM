package sekundarnaCast;

import java.util.*;

public class OsmData {
    private final Map<Long, OsmNode> nodes = new HashMap<>();
    private final List<Building> buildings = new ArrayList<>();
    private final List<MultipolygonBuilding> multipolygonBuildings = new ArrayList<>();
    private Bounds bounds;

    public void addNode(OsmNode node) { nodes.put(node.getId(), node); }
    public OsmNode getNodeById(long id) { return nodes.get(id); }

    public List<Building> getBuildings() { return buildings; }
    public void addBuilding(Building building) { buildings.add(building); }

    /** Multipolygon budovy z OSM relacii (napr. budovy s nadroriami). */
    public List<MultipolygonBuilding> getMultipolygonBuildings() { return multipolygonBuildings; }
    public void addMultipolygonBuilding(MultipolygonBuilding b) { multipolygonBuildings.add(b); }

    public Bounds getBounds() { return bounds; }
    public void setBounds(double minLat, double minLon, double maxLat, double maxLon) {
        this.bounds = new Bounds(minLat, minLon, maxLat, maxLon);
    }
}