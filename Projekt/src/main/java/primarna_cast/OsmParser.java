package primarna_cast;

import org.w3c.dom.*;
import sekundarna_cast.Building;
import sekundarna_cast.OsmData;
import sekundarna_cast.OsmNode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class OsmParser {
    public static OsmData parse(String filename) throws Exception {
        OsmData data = new OsmData();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(filename));

        parseBounds(doc, data);
        parseNodes(doc, data);
        parseWaysAndBuildings(doc, data);

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

    private static void parseWaysAndBuildings(Document doc, OsmData data) {
        NodeList ways = doc.getElementsByTagName("way");

        for (int i = 0; i < ways.getLength(); i++) {
            Element wayElement = (Element) ways.item(i);

            NodeList tags = wayElement.getElementsByTagName("tag");
            boolean isBuilding = false;

            for (int j = 0; j < tags.getLength(); j++) {
                Element tag = (Element) tags.item(j);
                if ("building".equals(tag.getAttribute("k"))) {
                    isBuilding = true;
                    break;
                }
            }

            if (isBuilding) {
                createBuilding(wayElement, data);
            }
        }
    }

    private static void createBuilding(Element wayElement, OsmData data) {
        Building building = new Building();

        // Tagy
        NodeList tags = wayElement.getElementsByTagName("tag");
        for (int j = 0; j < tags.getLength(); j++) {
            Element tag = (Element) tags.item(j);
            building.addTag(tag.getAttribute("k"), tag.getAttribute("v"));
        }

        // Uzly
        NodeList nds = wayElement.getElementsByTagName("nd");
        for (int j = 0; j < nds.getLength(); j++) {
            Element nd = (Element) nds.item(j);
            long nodeId = Long.parseLong(nd.getAttribute("ref"));
            OsmNode node = data.getNodeById(nodeId);
            if (node != null) {
                building.addNode(node);
            }
        }
        // Bezpecnostna kontrola
        if (building.getNodes().size() >= 3) {
            data.addBuilding(building);
        }
    }
}