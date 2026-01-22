package primarna_cast;

import org.w3c.dom.*;
import sekundarna_cast.Building;
import sekundarna_cast.OsmData;
import sekundarna_cast.OsmNode;
import sekundarna_cast.Bounds;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.*;

public class OSMExporter {
    public static void exportOnlyBuildings(OsmData osmData, String filename) throws Exception {
        List<Building> buildings = osmData.getBuildings();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element osm = doc.createElement("osm");
        osm.setAttribute("version", "0.6");
        osm.setAttribute("generator", "OSM-Budovy-Extractor");
        doc.appendChild(osm);

        Bounds bounds = osmData.getBounds();
        if (bounds != null) {
            Element boundsElem = doc.createElement("bounds");
            boundsElem.setAttribute("minlat", String.valueOf(bounds.getMinLat()));
            boundsElem.setAttribute("minlon", String.valueOf(bounds.getMinLon()));
            boundsElem.setAttribute("maxlat", String.valueOf(bounds.getMaxLat()));
            boundsElem.setAttribute("maxlon", String.valueOf(bounds.getMaxLon()));
            osm.appendChild(boundsElem);
        }

        // Mapovanie starych ID na nove ID
        Map<Long, Integer> idMapping = new HashMap<>();
        int newNodeId = 1;
        int newWayId = 1;

        for (Building building : buildings) {
            List<OsmNode> nodes = building.getNodes();

            for (OsmNode node : nodes) {
                long oldId = node.getId();

                if (!idMapping.containsKey(oldId)) {
                    addNode(doc, osm, node, newNodeId);
                    idMapping.put(oldId, newNodeId);
                    newNodeId++;
                }
            }
        }

        // Pridanie vsetkych ways (budovy)
        for (Building building : buildings) {
            addWay(doc, osm, building, idMapping, newWayId);
            newWayId++;
        }

        saveToFile(doc, filename);

        System.out.println("   Exportované: " + buildings.size() + " budov");
        System.out.println("   Použitých uzlov: " + idMapping.size());
    }

    private static void addNode(Document doc, Element osm, OsmNode node, int newId) {
        Element nodeElem = doc.createElement("node");
        nodeElem.setAttribute("id", String.valueOf(newId));
        nodeElem.setAttribute("lat", String.valueOf(node.getLat()));
        nodeElem.setAttribute("lon", String.valueOf(node.getLon()));
        nodeElem.setAttribute("visible", "true");
        nodeElem.setAttribute("version", "1");
        osm.appendChild(nodeElem);
    }

    private static void addWay(Document doc, Element osm, Building building,
                               Map<Long, Integer> idMapping, int wayId) {
        Element way = doc.createElement("way");
        way.setAttribute("id", String.valueOf(wayId));
        way.setAttribute("visible", "true");
        way.setAttribute("version", "1");

        // Pridanie referencie na uzly
        List<OsmNode> nodes = building.getNodes();
        for (OsmNode node : nodes) {
            int newNodeId = idMapping.get(node.getId());
            Element nd = doc.createElement("nd");
            nd.setAttribute("ref", String.valueOf(newNodeId));
            way.appendChild(nd);
        }

        // Pridanie vsetky tagy budovy
        for (Map.Entry<String, String> entry : building.getTags().entrySet()) {
            Element tag = doc.createElement("tag");
            tag.setAttribute("k", entry.getKey());
            tag.setAttribute("v", entry.getValue());
            way.appendChild(tag);
        }

        osm.appendChild(way);
    }

    private static void saveToFile(Document doc, String filename) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
    }
}