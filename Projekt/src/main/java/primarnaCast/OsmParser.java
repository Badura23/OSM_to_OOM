package primarnaCast;

import org.w3c.dom.*;
import sekundarnaCast.Building;
import sekundarnaCast.MultipolygonBuilding;
import sekundarnaCast.OsmData;
import sekundarnaCast.OsmNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Parsuje .osm XML subor a vracia OsmData s budovami a multipolygonmi
public class OsmParser {

    public static OsmData parse(String filename) throws Exception {
        OsmData data = new OsmData();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filename));

        parseBounds(doc, data);
        parseNodes(doc, data);

        // wayNodes: docasna mapa way_id -> uzly, potrebna pri skladani relacii
        Map<Long, List<OsmNode>> wayNodes = new HashMap<>();
        parseWaysAndBuildings(doc, data, wayNodes);
        parseRelations(doc, data, wayNodes);

        return data;
    }

    private static void parseBounds(Document doc, OsmData data) {
        Element bounds = (Element) doc.getElementsByTagName("bounds").item(0);
        if (bounds != null) {
            data.setBounds(
                    Double.parseDouble(bounds.getAttribute("minlat")),
                    Double.parseDouble(bounds.getAttribute("minlon")),
                    Double.parseDouble(bounds.getAttribute("maxlat")),
                    Double.parseDouble(bounds.getAttribute("maxlon"))
            );
        }
    }

    private static void parseNodes(Document doc, OsmData data) {
        NodeList nodeList = doc.getElementsByTagName("node");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element e = (Element) nodeList.item(i);
            OsmNode node = new OsmNode();
            node.setId(Long.parseLong(e.getAttribute("id")));
            node.setLat(Double.parseDouble(e.getAttribute("lat")));
            node.setLon(Double.parseDouble(e.getAttribute("lon")));

            NodeList tags = e.getElementsByTagName("tag");
            for (int j = 0; j < tags.getLength(); j++) {
                Element tag = (Element) tags.item(j);
                node.addTag(tag.getAttribute("k"), tag.getAttribute("v"));
            }
            data.addNode(node);
        }
    }

    // Prechadzame VSETKY ways (nielen building=*), lebo ich uzly su potrebne
    // aj pri skladani multipolygon relacii kde samotny way nema building tag
    private static void parseWaysAndBuildings(Document doc, OsmData data,
                                              Map<Long, List<OsmNode>> wayNodes) {
        NodeList ways = doc.getElementsByTagName("way");

        for (int i = 0; i < ways.getLength(); i++) {
            Element wayElement = (Element) ways.item(i);
            long wayId = Long.parseLong(wayElement.getAttribute("id"));

            NodeList nds = wayElement.getElementsByTagName("nd");
            List<OsmNode> nodes = new ArrayList<>(nds.getLength());
            for (int j = 0; j < nds.getLength(); j++) {
                long nodeId = Long.parseLong(((Element) nds.item(j)).getAttribute("ref"));
                OsmNode node = data.getNodeById(nodeId);
                if (node != null) nodes.add(node);
            }

            if (!nodes.isEmpty()) wayNodes.put(wayId, nodes);

            NodeList tags = wayElement.getElementsByTagName("tag");
            for (int j = 0; j < tags.getLength(); j++) {
                Element tag = (Element) tags.item(j);
                if ("building".equals(tag.getAttribute("k"))) {
                    createBuilding(wayElement, nodes, data);
                    break;
                }
            }
        }
    }

    private static void createBuilding(Element wayElement, List<OsmNode> nodes, OsmData data) {
        if (nodes.size() < 3) return;

        Building building = new Building();
        NodeList tags = wayElement.getElementsByTagName("tag");
        for (int j = 0; j < tags.getLength(); j++) {
            Element tag = (Element) tags.item(j);
            building.addTag(tag.getAttribute("k"), tag.getAttribute("v"));
        }
        for (OsmNode node : nodes) building.addNode(node);
        data.addBuilding(building);
    }

    // Hlada relacie s building=* a type=multipolygon
    // Outer way = obrys budovy, inner ways = nadvoria
    private static void parseRelations(Document doc, OsmData data,
                                       Map<Long, List<OsmNode>> wayNodes) {
        NodeList relations = doc.getElementsByTagName("relation");

        for (int i = 0; i < relations.getLength(); i++) {
            Element relElement = (Element) relations.item(i);

            Map<String, String> tags = collectTags(relElement);
            if (!tags.containsKey("building")) continue;
            if (!"multipolygon".equals(tags.get("type"))) continue;

            List<Long> outerWayIds = new ArrayList<>();
            List<Long> innerWayIds = new ArrayList<>();

            NodeList members = relElement.getElementsByTagName("member");
            for (int j = 0; j < members.getLength(); j++) {
                Element m = (Element) members.item(j);
                if (!"way".equals(m.getAttribute("type"))) continue;
                long ref = Long.parseLong(m.getAttribute("ref"));
                String role = m.getAttribute("role");
                if ("outer".equals(role)) outerWayIds.add(ref);
                else if ("inner".equals(role)) innerWayIds.add(ref);
            }

            if (outerWayIds.isEmpty()) continue;

            List<OsmNode> outerRing = wayNodes.get(outerWayIds.getFirst());
            if (outerRing == null || outerRing.size() < 3) continue;

            List<List<OsmNode>> innerRings = new ArrayList<>();
            for (long innerWayId : innerWayIds) {
                List<OsmNode> innerRing = wayNodes.get(innerWayId);
                if (innerRing != null && innerRing.size() >= 3) innerRings.add(innerRing);
            }

            data.addMultipolygonBuilding(new MultipolygonBuilding(outerRing, innerRings));

            System.out.printf("Multipolygon relacia %s (%s): outer=%d uzlov, %d vnutornych kruhov%n",
                    relElement.getAttribute("id"),
                    tags.getOrDefault("name", "?"),
                    outerRing.size(),
                    innerRings.size());
        }
    }

    private static Map<String, String> collectTags(Element element) {
        Map<String, String> result = new HashMap<>();
        NodeList tagList = element.getElementsByTagName("tag");
        for (int i = 0; i < tagList.getLength(); i++) {
            Element tag = (Element) tagList.item(i);
            result.put(tag.getAttribute("k"), tag.getAttribute("v"));
        }
        return result;
    }
}
