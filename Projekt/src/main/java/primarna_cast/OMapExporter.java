package primarna_cast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sekundarna_cast.Building;
import sekundarna_cast.OsmData;
import sekundarna_cast.OsmNode;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class OMapExporter {
    private static final String MAP_NAMESPACE = "http://openorienteering.org/apps/mapper/xml/v2";
    private static final String BUILDING_SYMBOL_ID = "111";
    private static final String REFERENCE_TEMPLATE_PROPERTY = "oom.referenceTemplate";

    private OMapExporter() {
    }

    public static void exportOnlyBuildings(OsmData osmData, String filename) throws Exception {
        List<Building> buildings = osmData.getBuildings();
        CoordinateConverter coordinateConverter = CoordinateConverter.from(osmData);
        Document referenceDocument = loadReferenceDocument();

        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element map = document.createElementNS(MAP_NAMESPACE, "map");
        map.setAttribute("version", "9");
        document.appendChild(map);

        appendEmptyNotes(document, map);
        appendGeoreferencing(document, map, coordinateConverter);
        importReferenceElement(referenceDocument, document, map, "colors");

        Element barrier = appendReferenceBarrier(referenceDocument, document, map);
        importReferenceElement(referenceDocument, document, barrier, "symbols");
        appendParts(document, barrier, buildings, coordinateConverter);

        saveToFile(document, filename);
    }

    private static Document loadReferenceDocument() throws Exception {
        Path referencePath = resolveReferenceTemplate();
        if (!Files.exists(referencePath)) {
            throw new IllegalStateException("Reference OMAP template not found: " + referencePath);
        }

        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(referencePath.toFile());
    }

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }

    private static Path resolveReferenceTemplate() {
        String override = System.getProperty(REFERENCE_TEMPLATE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }

        Path userProfileTemplate = resolveUserProfileTemplate();
        if (userProfileTemplate != null && Files.exists(userProfileTemplate)) {
            return userProfileTemplate;
        }

        Path userHomeTemplate = Path.of(System.getProperty("user.home"), "Downloads", "complete map.omap");
        if (Files.exists(userHomeTemplate)) {
            return userHomeTemplate;
        }

        Path localTemplate = Path.of("complete map.omap");
        if (Files.exists(localTemplate)) {
            return localTemplate;
        }

        return userProfileTemplate != null ? userProfileTemplate : userHomeTemplate;
    }

    private static Path resolveUserProfileTemplate() {
        String userProfile = System.getenv("USERPROFILE");
        if (userProfile == null || userProfile.isBlank()) {
            return null;
        }
        return Path.of(userProfile, "Downloads", "complete map.omap");
    }

    private static void appendEmptyNotes(Document document, Element parent) {
        appendElement(document, parent, "notes");
    }

    private static void appendGeoreferencing(Document document, Element parent,
                                             CoordinateConverter coordinateConverter) {
        Element georeferencing = appendElement(document, parent, "georeferencing");
        georeferencing.setAttribute("scale", "4000");
        georeferencing.setAttribute("auxiliary_scale_factor", "1");
        georeferencing.setAttribute("declination", "0");
        georeferencing.setAttribute("grivation", "0");

        Element refPoint = appendElement(document, georeferencing, "ref_point");
        refPoint.setAttribute("x", String.valueOf(coordinateConverter.getReferenceX()));
        refPoint.setAttribute("y", String.valueOf(coordinateConverter.getReferenceY()));

        Element projectedCrs = appendElement(document, georeferencing, "projected_crs");
        projectedCrs.setAttribute("id", "Local");

        Element projectedSpec = appendElement(document, projectedCrs, "spec");
        projectedSpec.setAttribute("language", "PROJ.4");
        projectedSpec.setTextContent(coordinateConverter.getProjectedCrsSpec());

        Element projectedParameter = appendElement(document, projectedCrs, "parameter");
        projectedParameter.setTextContent("Local equirectangular");

        Element projectedRefPoint = appendElement(document, projectedCrs, "ref_point");
        projectedRefPoint.setAttribute("x", "0");
        projectedRefPoint.setAttribute("y", "0");

        Element geographicCrs = appendElement(document, georeferencing, "geographic_crs");
        geographicCrs.setAttribute("id", "Geographic coordinates");

        Element geographicSpec = appendElement(document, geographicCrs, "spec");
        geographicSpec.setAttribute("language", "PROJ.4");
        geographicSpec.setTextContent("+proj=latlong +datum=WGS84");

        Element geographicRefPoint = appendElement(document, geographicCrs, "ref_point_deg");
        geographicRefPoint.setAttribute("lat", formatDouble(coordinateConverter.getReferenceLat()));
        geographicRefPoint.setAttribute("lon", formatDouble(coordinateConverter.getReferenceLon()));
    }

    private static Element appendReferenceBarrier(Document referenceDocument, Document targetDocument, Element parent) {
        Element referenceBarrier = findFirstElement(referenceDocument, "barrier");
        Element barrier = appendElement(targetDocument, parent, "barrier");

        if (referenceBarrier != null) {
            copyAttributes(referenceBarrier, barrier);
        } else {
            barrier.setAttribute("version", "6");
            barrier.setAttribute("required", "0.6.0");
        }

        return barrier;
    }

    private static void importReferenceElement(Document referenceDocument, Document targetDocument,
                                               Element parent, String localName) {
        Element referenceElement = findFirstElement(referenceDocument, localName);
        if (referenceElement == null) {
            throw new IllegalStateException("Missing element in reference OMAP: " + localName);
        }

        Node importedNode = targetDocument.importNode(referenceElement, true);
        parent.appendChild(importedNode);
    }

    private static Element findFirstElement(Document document, String localName) {
        NodeList nodeList = document.getElementsByTagNameNS(MAP_NAMESPACE, localName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return (Element) nodeList.item(0);
    }

    private static void copyAttributes(Element source, Element target) {
        for (int i = 0; i < source.getAttributes().getLength(); i++) {
            Node attribute = source.getAttributes().item(i);
            target.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
        }
    }

    private static void appendParts(Document document, Element parent, List<Building> buildings,
                                    CoordinateConverter coordinateConverter) {
        Element parts = appendElement(document, parent, "parts");
        parts.setAttribute("count", "1");
        parts.setAttribute("current", "0");

        Element part = appendElement(document, parts, "part");
        part.setAttribute("name", "Map");

        Element objects = appendElement(document, part, "objects");
        objects.setAttribute("count", String.valueOf(buildings.size()));

        for (Building building : buildings) {
            appendBuildingObject(document, objects, building, coordinateConverter);
        }
    }

    private static void appendBuildingObject(Document document, Element parent, Building building,
                                             CoordinateConverter coordinateConverter) {
        Element object = appendElement(document, parent, "object");
        object.setAttribute("type", "1");
        object.setAttribute("symbol", BUILDING_SYMBOL_ID);

        List<OsmNode> nodes = building.getNodes();
        Element coords = appendElement(document, object, "coords");
        coords.setAttribute("count", String.valueOf(nodes.size()));
        coords.setTextContent(buildCoords(nodes, coordinateConverter));

        Element pattern = appendElement(document, object, "pattern");
        pattern.setAttribute("rotation", "0");

        Element coord = appendElement(document, pattern, "coord");
        coord.setAttribute("x", "0");
        coord.setAttribute("y", "0");
    }

    private static String buildCoords(List<OsmNode> nodes, CoordinateConverter coordinateConverter) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            CoordinateConverter.MapPoint point = coordinateConverter.convert(nodes.get(i));
            int flag = (i == nodes.size() - 1) ? 50 : 32;
            builder.append(point.getX())
                    .append(' ')
                    .append(point.getY())
                    .append(' ')
                    .append(flag)
                    .append(';');
        }
        return builder.toString();
    }

    private static Element appendElement(Document document, Element parent, String name) {
        Element element = document.createElementNS(MAP_NAMESPACE, name);
        parent.appendChild(element);
        return element;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.8f", value);
    }

    private static void saveToFile(Document document, String filename) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new File(filename));
        transformer.transform(source, result);
    }
}
