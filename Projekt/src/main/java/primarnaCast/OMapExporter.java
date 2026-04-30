package primarnaCast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sekundarnaCast.Building;
import sekundarnaCast.OsmData;
import sekundarnaCast.OsmNode;

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

public final class OMapExporter {
    private static final String MAP_NAMESPACE = "http://openorienteering.org/apps/mapper/xml/v2";

    // ID symbolu budovy v referencnom .omap subore.
    // Tento symbol musi existovat v subore "complete map.omap".
    private static final String BUILDING_SYMBOL_ID = "111";

    private OMapExporter() {
    }

    // Hlavna metoda exportu.
    // Zoberie uz vyparsovane OSM data, vyberie z nich budovy a vytvori .omap subor.
    public static void exportOnlyBuildings(OsmData osmData, String filename) throws Exception {
        List<Building> buildings = osmData.getBuildings();
        CoordinateConverter coordinateConverter = CoordinateConverter.from(osmData);

        // Referencny .omap subor obsahuje platne farby, symboly a strukturu OOM.
        // Z neho skopirujeme hlavne colors a symbols.
        Document referenceDocument = loadReferenceDocument();

        // Vytvorime novy prazdny XML dokument, do ktoreho budeme skladat vystupny .omap.
        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        // Korenovy element celeho .omap suboru.
        Element map = document.createElementNS(MAP_NAMESPACE, "map");
        map.setAttribute("version", "9");
        document.appendChild(map);

        // Zakladne casti .omap suboru.
        appendGeoreferencing(document, map);

        // Farby kopirujeme z referencnej mapy, aby sme nemuseli rucne vytvarat vsetky definicie.
        importReferenceElement(referenceDocument, document, map, "colors");

        Element barrier = appendReferenceBarrier(referenceDocument, document, map);
        importReferenceElement(referenceDocument, document, barrier, "symbols");
        appendParts(document, barrier, buildings, coordinateConverter);

        saveToFile(document, filename);
    }

    // Nacita referencny .omap subor, z ktoreho kopirujeme colors, symbols a barrier atributy.
    private static Document loadReferenceDocument() throws Exception {
        Path referencePath = Path.of("complete map.omap");
        if (!Files.exists(referencePath)) {
            throw new IllegalStateException("Reference OMAP template not found: " + referencePath);
        }

        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(referencePath.toFile());
    }

    // Vytvori XML parser.
    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }

    // Prida zakladne georeferencne informacie mapy.
    // V tomto projekte pouzivame hlavne lokalne x/y suradnice objektov.
    private static void appendGeoreferencing(Document document, Element parent) {
        Element georeferencing = appendElement(document, parent, "georeferencing");
        georeferencing.setAttribute("scale", "4000");
        georeferencing.setAttribute("auxiliary_scale_factor", "1");
        georeferencing.setAttribute("declination", "0");
        georeferencing.setAttribute("grivation", "0");

        Element refPoint = appendElement(document, georeferencing, "ref_point");
        refPoint.setAttribute("x", "0");
        refPoint.setAttribute("y", "0");
    }

    // Vytvori element barrier pre vystupny .omap.
    // Ak existuje v referencnej mape, skopiruje jeho atributy,
    // aby bol vystup co najviac kompatibilny so strukturou vytvorenou v OOM.
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

    // Skopiruje celu sekciu z referencneho .omap suboru do vystupu.
    // Pouziva sa pre colors a symbols, pretoze ich rucne vytvaranie
    //by bolo zbytocne zlozite a nachylne na chyby.
    private static void importReferenceElement(Document referenceDocument, Document targetDocument,
                                               Element parent, String localName) {
        Element referenceElement = findFirstElement(referenceDocument, localName);
        if (referenceElement == null) {
            throw new IllegalStateException("Missing element in reference OMAP: " + localName);
        }

        Node importedNode = targetDocument.importNode(referenceElement, true);
        parent.appendChild(importedNode);
    }

    // Najde prvy element podla mena v OOM namespace.
    // Pouzivame to pri hladani colors, symbols a barrier v referencnom subore.
    private static Element findFirstElement(Document document, String localName) {
        NodeList nodeList = document.getElementsByTagNameNS(MAP_NAMESPACE, localName);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return (Element) nodeList.item(0);
    }

    // Skopiruje atributy z referencneho elementu.
    // Pouziva sa hlavne pri barrier, aby sme zachovali jeho verziu
    // a kompatibilitne nastavenia.
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
            // Flag 32 oznacuje bezny bod.
            // Flag 50 pouzivame pre posledny bod uzavreteho polygonu.
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
