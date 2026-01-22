package sekundarna_cast;

import java.util.*;

public class Building {
    private List<OsmNode> nodes = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();

    public void addNode(OsmNode node) { nodes.add(node); }
    public List<OsmNode> getNodes() { return nodes; }

    public void addTag(String k, String v) { tags.put(k, v); }
    public Map<String, String> getTags() { return tags; }
}