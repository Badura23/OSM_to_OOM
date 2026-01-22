package sekundarna_cast;

import java.util.*;

public class OsmData {
    private final Map<Long, OsmNode> nodes = new HashMap<>();
    private final List<Building> buildings = new ArrayList<>();
    private Bounds bounds;

    public void addNode(OsmNode node) { nodes.put(node.getId(), node); }
    public OsmNode getNodeById(long id) { return nodes.get(id); }
    public List<Building> getBuildings() { return buildings; }
    public void addBuilding(Building building) { buildings.add(building); }

    public Bounds getBounds() { return bounds; }
    public void setBounds(double minLat, double minLon, double maxLat, double maxLon) {
        this.bounds = new Bounds(minLat, minLon, maxLat, maxLon);
    }
}