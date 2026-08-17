package com.geocerca.app;

public class GeoPoint {
    public final String name;
    public final double x;
    public final double y;
    public final double z;
    public final boolean latLon;

    public GeoPoint(String name, double x, double y, double z, boolean latLon) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.latLon = latLon;
    }
}
