package primarnaCast;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Kartograficke spracovanie budov v mapovych suradniciach (250 mu = 1m pri 1:4000)
// Krok 1: minimalna sirka budovy (0.5mm na mape = 500 mu)
// Krok 2: zlucenie budov blizsich ako MIN_GAP_MU (napr. radove garaze)
public final class BuildingProcessor {

    private static final double MIN_BUILDING_WIDTH_MU = 500.0;
    private static final double MIN_GAP_MU = 250.0;
    private static final double PATH_FLATNESS = 10.0;

    private BuildingProcessor() {}

    // Vypocita plochu budovy s dierami pomocou Area.subtract().
    // Java Area automaticky nastavi spravny winding pre kazdu dieru,
    // co eliminuje renderingove artefakty v OOM.
    // Vracia: index 0 = vonkajsi kruh, index 1..N = hranice nadvorii
    public static List<List<int[]>> computeHoleSubtraction(List<int[]> outerRing,
                                                           List<List<int[]>> innerRings) {
        Area area = toArea(outerRing);
        for (List<int[]> inner : innerRings) {
            area.subtract(toArea(inner));
        }
        return extractAllRings(area);
    }

    public static List<List<int[]>> process(List<List<int[]>> polygons) {
        List<List<int[]>> krok1 = enforceMinimumWidth(polygons);
        return mergeNearbyBuildings(krok1);
    }

    // --- Krok 1: minimalna sirka ---

    private static List<List<int[]>> enforceMinimumWidth(List<List<int[]>> polygons) {
        List<List<int[]>> result = new ArrayList<>(polygons.size());
        for (List<int[]> polygon : polygons) {
            result.add(ensureMinimumWidth(polygon));
        }
        return result;
    }

    // Ak je bounding box uzsi ako minimum, nahradi polygon obdlznikom rovnakeho stredu.
    // Dlhsia os sa zachova, kratsia sa rozsiri — takze uzky dlhy objekt zostane dlhy.
    private static List<int[]> ensureMinimumWidth(List<int[]> polygon) {
        if (polygon.isEmpty()) return polygon;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] pt : polygon) {
            if (pt[0] < minX) minX = pt[0];
            if (pt[0] > maxX) maxX = pt[0];
            if (pt[1] < minY) minY = pt[1];
            if (pt[1] > maxY) maxY = pt[1];
        }

        double w = maxX - minX;
        double h = maxY - minY;
        if (w >= MIN_BUILDING_WIDTH_MU && h >= MIN_BUILDING_WIDTH_MU) return polygon;

        double cx = (minX + maxX) / 2.0;
        double cy = (minY + maxY) / 2.0;
        double hw = Math.max(w / 2.0, MIN_BUILDING_WIDTH_MU / 2.0);
        double hh = Math.max(h / 2.0, MIN_BUILDING_WIDTH_MU / 2.0);

        int l = iRound(cx - hw), r = iRound(cx + hw);
        int t = iRound(cy - hh), b = iRound(cy + hh);
        List<int[]> rect = new ArrayList<>(5);
        rect.add(new int[]{l, t});
        rect.add(new int[]{r, t});
        rect.add(new int[]{r, b});
        rect.add(new int[]{l, b});
        rect.add(new int[]{l, t}); // uzatvoriaci bod = prvy bod
        return rect;
    }

    // --- Krok 2: zlucenie blizko stoiacich budov ---

    private static List<List<int[]>> mergeNearbyBuildings(List<List<int[]>> polygons) {
        int n = polygons.size();
        if (n <= 1) return polygons;

        double radius = MIN_GAP_MU / 2.0;
        List<Area> originalAreas = new ArrayList<>(n);
        List<Area> bufferedAreas = new ArrayList<>(n);
        for (List<int[]> polygon : polygons) {
            Area a = toArea(polygon);
            originalAreas.add(a);
            bufferedAreas.add(buffer(a, radius));
        }

        // Union-Find: budovy ktore sa dotykaju v nafuknutej verzii patria do jednej skupiny
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Area test = new Area(bufferedAreas.get(i));
                test.intersect(bufferedAreas.get(j));
                if (!test.isEmpty()) ufUnion(parent, i, j);
            }
        }

        Map<Integer, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(ufFind(parent, i), k -> new ArrayList<>()).add(i);
        }

        List<List<int[]>> result = new ArrayList<>();
        for (List<Integer> group : groups.values()) {
            if (group.size() == 1) {
                result.add(polygons.get(group.getFirst()));
            } else {
                Area merged = new Area();
                for (int idx : group) merged.add(bufferedAreas.get(idx));
                result.addAll(extractOuterPolygons(merged));
            }
        }
        return result;
    }

    // --- Geometricke pomocne metody ---

    private static Area toArea(List<int[]> polygon) {
        if (polygon.isEmpty()) return new Area();
        Path2D.Double path = new Path2D.Double();
        path.moveTo(polygon.getFirst()[0], polygon.getFirst()[1]);
        for (int i = 1; i < polygon.size(); i++)
            path.lineTo(polygon.get(i)[0], polygon.get(i)[1]);
        path.closePath();
        return new Area(path);
    }

    private static Area buffer(Area area, double radius) {
        PathIterator pi = area.getPathIterator(null);
        Path2D.Double path = new Path2D.Double();
        path.append(pi, false);
        BasicStroke stroke = new BasicStroke(
                (float)(radius * 2.0), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        Shape outline = stroke.createStrokedShape(path);
        Area result = new Area(area);
        result.add(new Area(outline));
        return result;
    }

    // Z Area extrahuje iba vonkajsie prstence (zahodí nadvoria/diery).
    // Vonkajsi prstenec ma vzdy najvacsiu absolutnu plochu — jeho znamienko
    // shoelace formuly pouzijeme ako referenciu pre ostatne vonkajsie prstence.
    private static List<List<int[]>> extractOuterPolygons(Area area) {
        List<List<int[]>> allRings = extractAllRings(area);
        if (allRings.size() <= 1) return allRings;

        double maxAbsArea = 0;
        double dominantSign = 0;
        for (List<int[]> ring : allRings) {
            double sa = computeSignedArea(ring);
            if (Math.abs(sa) > maxAbsArea) {
                maxAbsArea = Math.abs(sa);
                dominantSign = Math.signum(sa);
            }
        }

        List<List<int[]>> outer = new ArrayList<>();
        for (List<int[]> ring : allRings) {
            if (Math.signum(computeSignedArea(ring)) == dominantSign) outer.add(ring);
        }
        return outer.isEmpty() ? allRings : outer;
    }

    // Shoelace formula — vonkajsi a vnutorny prstenec maju opacne znamienka
    private static double computeSignedArea(List<int[]> ring) {
        double area = 0;
        int n = ring.size();
        for (int i = 0; i < n - 1; i++) {
            area += (double) ring.get(i)[0] * ring.get(i + 1)[1]
                    - (double) ring.get(i + 1)[0] * ring.get(i)[1];
        }
        return area / 2.0;
    }

    private static List<List<int[]>> extractAllRings(Area area) {
        List<List<int[]>> rings = new ArrayList<>();
        List<int[]> current = new ArrayList<>();
        int[] ringStart = null;

        PathIterator pi = area.getPathIterator(null, PATH_FLATNESS);
        double[] c = new double[6];

        while (!pi.isDone()) {
            int type = pi.currentSegment(c);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    if (!current.isEmpty() && ringStart != null)
                        closeAndAdd(current, ringStart, rings);
                    current = new ArrayList<>();
                    ringStart = new int[]{iRound(c[0]), iRound(c[1])};
                    current.add(new int[]{ringStart[0], ringStart[1]});
                    break;
                case PathIterator.SEG_LINETO:
                    current.add(new int[]{iRound(c[0]), iRound(c[1])});
                    break;
                case PathIterator.SEG_CLOSE:
                    if (!current.isEmpty() && ringStart != null)
                        closeAndAdd(current, ringStart, rings);
                    current = new ArrayList<>();
                    ringStart = null;
                    break;
                default:
                    break;
            }
            pi.next();
        }
        if (!current.isEmpty() && ringStart != null && current.size() >= 3)
            closeAndAdd(current, ringStart, rings);

        return rings;
    }

    private static void closeAndAdd(List<int[]> ring, int[] start, List<List<int[]>> out) {
        ring.add(new int[]{start[0], start[1]}); // uzatvoriaci bod = kopie prveho bodu
        if (ring.size() >= 4) out.add(ring);
    }

    private static int iRound(double v) { return (int) Math.round(v); }

    private static int ufFind(int[] parent, int i) {
        if (parent[i] != i) parent[i] = ufFind(parent, parent[i]);
        return parent[i];
    }

    private static void ufUnion(int[] parent, int i, int j) {
        parent[ufFind(parent, i)] = ufFind(parent, j);
    }
}
