package sekundarnaCast;

import java.util.*;

public class OsmNode {
    private long id;
    private double lat;
    private double lon;
    private final Map<String, String> tags = new HashMap<>();

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }

    public void addTag(String k, String v) { tags.put(k, v); }
}