package primarna_cast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import sekundarna_cast.Building;
import sekundarna_cast.MultipolygonBuilding;
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
import java.util.ArrayList;
import java.util.List;

public final class OMapExporter {
    private static final String MAP_NAMESPACE = "http://openorienteering.org/apps/mapper/xml/v2";
    private static final String BUILDING_SYMBOL_ID = "111";

    private OMapExporter() {
    }

    public static void exportOnlyBuildings(OsmData osmData, String filename) throws Exception {
        List<Building> buildings = osmData.getBuildings();
        List<MultipolygonBuilding> mpBuildings = osmData.getMultipolygonBuildings();
        CoordinateConverter coordinateConverter = CoordinateConverter.from(osmData);
        Document referenceDocument = loadReferenceDocument();

        // 1. Jednoduche budovy (OSM way s tagom building=*)
        //    → minimalna sirka + zlucovanie blizko stoiacich budov
        List<List<int[]>> rawPolygons = convertToMapCoordinates(buildings, coordinateConverter);
        List<List<int[]>> processedPolygons = BuildingProcessor.process(rawPolygons);

        // 2. Multipolygon budovy (OSM relacie s tagom building=*)
        //    → outer kruh ako telo budovy, inner kruhy ako biele diery (nadvoria)
        //    → neprechadzaju cez BuildingProcessor (su to uz komplexne tvary)
        List<List<List<int[]>>> mpCoords = convertMultipolygonsToMapCoords(mpBuildings, coordinateConverter);

        System.out.printf("Budovy: %d jednoduche + %d multipolygony  ->  %d OOM objektov%n",
                buildings.size(), mpBuildings.size(),
                processedPolygons.size() + mpCoords.size());

        // 3. Zostavenie XML
        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element map = document.createElementNS(MAP_NAMESPACE, "map");
        map.setAttribute("version", "9");
        document.appendChild(map);

        appendGeoreferencing(document, map);
        importReferenceElement(referenceDocument, document, map, "colors");

        Element barrier = appendReferenceBarrier(referenceDocument, document, map);
        importReferenceElement(referenceDocument, document, barrier, "symbols");
        appendParts(document, barrier, processedPolygons, mpCoords);

        saveToFile(document, filename);
    }

    // -------------------------------------------------------------------------
    // Konverzia do mapovych suradnic
    // -------------------------------------------------------------------------

    private static List<List<int[]>> convertToMapCoordinates(List<Building> buildings,
                                                             CoordinateConverter converter) {
        List<List<int[]>> result = new ArrayList<>(buildings.size());
        for (Building building : buildings) {
            List<int[]> polygon = new ArrayList<>(building.getNodes().size());
            for (OsmNode node : building.getNodes()) {
                CoordinateConverter.MapPoint p = converter.convert(node);
                polygon.add(new int[]{p.getX(), p.getY()});
            }
            result.add(polygon);
        }
        return result;
    }

    /**
     * Konvertuje multipolygon budovy do mapovych suradnic s pouzitim Area.subtract().
     *
     * Java Area automaticky opravuje smer obehu (winding) pre kazdu dieru.
     * Vysledok je zoznam kruhov kde:
     *   index 0    = vonkajsi kruh (outer ring) - spravny winding
     *   index 1..N = hranice nadvorii (hole boundaries) - opacny winding
     *
     * Toto riesenie eliminuje artefakty ktore vznikali pri pouziti HolePoint
     * flagu=8 s nekorektnym smerom obehu vnutornych kruhov.
     */
    private static List<List<List<int[]>>> convertMultipolygonsToMapCoords(
            List<MultipolygonBuilding> mpBuildings, CoordinateConverter converter) {

        List<List<List<int[]>>> result = new ArrayList<>(mpBuildings.size());

        for (MultipolygonBuilding mp : mpBuildings) {
            // Konverzia suradnic
            List<int[]> outer = convertRing(mp.getOuterRing(), converter);
            List<List<int[]>> inners = new ArrayList<>();
            for (List<OsmNode> innerNodes : mp.getInnerRings()) {
                inners.add(convertRing(innerNodes, converter));
            }

            // Area.subtract() vypocita diery so spravnym windingom
            // (Java Area automaticky nastavi opacny smer obehu pre diery)
            List<List<int[]>> rings = BuildingProcessor.computeHoleSubtraction(outer, inners);
            result.add(rings);
        }

        return result;
    }

    private static List<int[]> convertRing(List<OsmNode> nodes, CoordinateConverter converter) {
        List<int[]> ring = new ArrayList<>(nodes.size());
        for (OsmNode node : nodes) {
            CoordinateConverter.MapPoint p = converter.convert(node);
            ring.add(new int[]{p.getX(), p.getY()});
        }
        return ring;
    }

    // -------------------------------------------------------------------------
    // Nacitanie referencneho suboru
    // -------------------------------------------------------------------------

    private static Document loadReferenceDocument() throws Exception {
        Path projectRoot = resolveProjectRoot();
        if (projectRoot == null) {
            throw new IllegalStateException(
                    "Nepodarilo sa najst projektovy koren (adresar obsahujuci 'src'). " +
                            "Skontroluj, ze 'complete map.omap' je na rovnakej urovni ako priecinok 'src'.");
        }

        Path referencePath = projectRoot.resolve("src").resolve("main").resolve("java")
                .resolve("podklady").resolve("complete map.omap");
        if (!Files.exists(referencePath)) {
            throw new IllegalStateException(
                    "Referencny subor nebol najdeny: " + referencePath.toAbsolutePath());
        }

        DocumentBuilderFactory factory = createDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(referencePath.toFile());
    }

    private static Path resolveProjectRoot() {
        try {
            Path start = Path.of(OMapExporter.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).toAbsolutePath();

            Path candidate = Files.isDirectory(start) ? start : start.getParent();

            for (int depth = 0; depth < 8 && candidate != null; depth++) {
                if (Files.isDirectory(candidate.resolve("src"))) {
                    return candidate;
                }
                candidate = candidate.getParent();
            }
        } catch (Exception ignored) {
            // ignorovat — chyba sa odchyti v loadReferenceDocument
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Zostavenie XML dokumentu
    // -------------------------------------------------------------------------

    private static DocumentBuilderFactory createDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory;
    }

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

    private static Element appendReferenceBarrier(Document referenceDocument,
                                                  Document targetDocument, Element parent) {
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
        if (nodeList.getLength() == 0) return null;
        return (Element) nodeList.item(0);
    }

    private static void copyAttributes(Element source, Element target) {
        for (int i = 0; i < source.getAttributes().getLength(); i++) {
            Node attribute = source.getAttributes().item(i);
            target.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
        }
    }

    // -------------------------------------------------------------------------
    // Generovanie XML objektov budov
    // -------------------------------------------------------------------------

    /**
     * Vygeneruje XML cast {@code <parts>} obsahujucu vsetky budovy:
     *   - jednoduche polygony zo spracovanych OSM ways
     *   - multipolygon budovy z OSM relacii (s dierami pre nadvoria)
     */
    private static void appendParts(Document document, Element parent,
                                    List<List<int[]>> simplePolygons,
                                    List<List<List<int[]>>> multipolygons) {
        Element parts = appendElement(document, parent, "parts");
        parts.setAttribute("count", "1");
        parts.setAttribute("current", "0");

        Element part = appendElement(document, parts, "part");
        part.setAttribute("name", "Map");

        int totalCount = simplePolygons.size() + multipolygons.size();
        Element objects = appendElement(document, part, "objects");
        objects.setAttribute("count", String.valueOf(totalCount));

        for (List<int[]> polygon : simplePolygons) {
            appendSimpleBuildingObject(document, objects, polygon);
        }

        for (List<List<int[]>> mp : multipolygons) {
            // mp.get(0) = outer ring, mp.subList(1, ...) = inner rings
            appendMultipolygonBuildingObject(document, objects, mp.get(0), mp.subList(1, mp.size()));
        }
    }

    /**
     * Jednoduchy polygon budovy (bez dier).
     * OOM flagy: 32 = bezny bod, 50 = posledny uzatvoriaci bod.
     */
    private static void appendSimpleBuildingObject(Document document, Element parent,
                                                   List<int[]> polygon) {
        Element object = appendElement(document, parent, "object");
        object.setAttribute("type", "1");
        object.setAttribute("symbol", BUILDING_SYMBOL_ID);

        Element coords = appendElement(document, object, "coords");
        coords.setAttribute("count", String.valueOf(polygon.size()));
        coords.setTextContent(buildCoordsSimple(polygon));

        appendPatternElement(document, object);
    }

    /**
     * Multipolygon budova s dierami (nadroriami).
     *
     * <p>OOM format pre plochu s dierami:
     * <ul>
     *   <li>Vonkajsi kruh: vsetky body s flagom 32 (vrátane opakujuceho prveho bodu)</li>
     *   <li>Kazdy vnutorny kruh: prvy bod s flagom {@code 8} (HolePoint = OOM signal
     *       pre zaciatok diery), ostatne body s flagom 32</li>
     *   <li>Uplne posledny bod celeho objektu ma flag 50</li>
     * </ul>
     *
     * @param outerRing  vonkajsi kruh budovy
     * @param innerRings vnutorne kruhy (nadvoria); moze byt prazdny
     */
    private static void appendMultipolygonBuildingObject(Document document, Element parent,
                                                         List<int[]> outerRing,
                                                         List<List<int[]>> innerRings) {
        int totalPoints = outerRing.size();
        for (List<int[]> inner : innerRings) {
            totalPoints += inner.size();
        }

        Element object = appendElement(document, parent, "object");
        object.setAttribute("type", "1");
        object.setAttribute("symbol", BUILDING_SYMBOL_ID);

        Element coords = appendElement(document, object, "coords");
        coords.setAttribute("count", String.valueOf(totalPoints));
        coords.setTextContent(buildCoordsMultipolygon(outerRing, innerRings));

        appendPatternElement(document, object);
    }

    // -------------------------------------------------------------------------
    // Serializacia suradnic do OOM textoveho formatu
    // -------------------------------------------------------------------------

    /** Serializuje jednoduchy uzatvoriaci polygon: flag 32 pre kazdy bod, 50 pre posledny. */
    private static String buildCoordsSimple(List<int[]> polygon) {
        StringBuilder sb = new StringBuilder();
        int last = polygon.size() - 1;
        for (int i = 0; i <= last; i++) {
            int flag = (i == last) ? 50 : 32;
            sb.append(polygon.get(i)[0]).append(' ')
                    .append(polygon.get(i)[1]).append(' ')
                    .append(flag).append(';');
        }
        return sb.toString();
    }

    /**
     * Serializuje multipolygon s dierami do OOM textoveho formatu.
     *
     * Kazdy kruh (vonkajsi aj vnutorny) konci flagom 50. Tento pristup:
     *   - vonkajsi kruh: flag 32 pre kazdy bod, flag 50 na uzatvoriacom bode
     *   - vnutorny kruh: flag 8 na prvom bode (HolePoint), flag 32 pre ostatok,
     *                    flag 50 na uzatvoriacom bode
     *
     * Flag 50 na konci kazdeho kruhu zabranuje OOM nakreslit spojovaciu ciaru
     * medzi kruhmi (cize nie su diagonalne artefakty).
     * Flag 8 informuje OOM ze ide o dieru, nie o dalsiu vonkajsiu plochu.
     * Spravny winding (smer obehu) je zaisteny cez BuildingProcessor.computeHoleSubtraction().
     */
    private static String buildCoordsMultipolygon(List<int[]> outerRing,
                                                  List<List<int[]>> innerRings) {
        StringBuilder sb = new StringBuilder();

        // Vonkajsi kruh — flag 32 pre kazdy bod, flag 50 pre uzatvoriaci
        for (int i = 0; i < outerRing.size(); i++) {
            int flag = (i == outerRing.size() - 1) ? 50 : 32;
            sb.append(outerRing.get(i)[0]).append(' ')
                    .append(outerRing.get(i)[1]).append(' ')
                    .append(flag).append(';');
        }

        // Vnutorne kruhy (diery)
        for (List<int[]> ring : innerRings) {
            for (int i = 0; i < ring.size(); i++) {
                int flag;
                if (i == 0) {
                    flag = 8;  // HolePoint — zaciatok diery
                } else if (i == ring.size() - 1) {
                    flag = 50; // Uzatvoriaci bod tohto kruhu
                } else {
                    flag = 32; // Bezny bod
                }
                sb.append(ring.get(i)[0]).append(' ')
                        .append(ring.get(i)[1]).append(' ')
                        .append(flag).append(';');
            }
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Pomocne metody
    // -------------------------------------------------------------------------

    private static void appendPatternElement(Document document, Element object) {
        Element pattern = appendElement(document, object, "pattern");
        pattern.setAttribute("rotation", "0");

        Element coord = appendElement(document, pattern, "coord");
        coord.setAttribute("x", "0");
        coord.setAttribute("y", "0");
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