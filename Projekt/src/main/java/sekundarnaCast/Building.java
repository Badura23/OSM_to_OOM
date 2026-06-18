package sekundarnaCast;

import java.util.*;

// Budova z OSM way s tagom building=* — obsahuje zoznam uzlov tvoriaci polygon
public class Building {
    private final List<OsmNode> nodes = new ArrayList<>();
    private final Map<String, String> tags = new HashMap<>();

    public void addNode(OsmNode node) { nodes.add(node); }
    public List<OsmNode> getNodes() { return nodes; }

    public void addTag(String k, String v) { tags.put(k, v); }
    public Map<String, String> getTags() { return tags; }
}
