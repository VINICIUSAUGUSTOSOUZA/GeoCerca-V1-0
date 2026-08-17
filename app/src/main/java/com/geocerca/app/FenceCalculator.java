package com.geocerca.app;

import java.util.List;

public final class FenceCalculator {
    private FenceCalculator() {}

    public static class Result {
        public double perimeterM;
        public int intermediatePosts;
        public int cornerPosts;
        public int bracePosts;
        public int struts;
        public double wireM;
        public int wireRolls;
        public int staples;
        public double stapleKg;
        public int staplePackages;
    }

    public static double distanceM(GeoPoint a, GeoPoint b) {
        if (a.latLon || b.latLon) {
            double r = 6371008.8;
            double lat1 = Math.toRadians(a.y);
            double lat2 = Math.toRadians(b.y);
            double dLat = lat2 - lat1;
            double dLon = Math.toRadians(b.x - a.x);
            double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(lat1) * Math.cos(lat2)
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(h)));
        }
        return Math.hypot(b.x - a.x, b.y - a.y);
    }

    public static Result calculate(List<GeoPoint> polygon, FenceConfig cfg) {
        Result r = new Result();
        if (polygon == null || polygon.size() < 3) return r;

        r.cornerPosts = polygon.size();
        r.bracePosts = r.cornerPosts * Math.max(0, cfg.bracesPerCorner);
        r.struts = r.cornerPosts * Math.max(0, cfg.strutsPerCorner);

        for (int i = 0; i < polygon.size(); i++) {
            GeoPoint a = polygon.get(i);
            GeoPoint b = polygon.get((i + 1) % polygon.size());
            double d = distanceM(a, b);
            r.perimeterM += d;

            int intervals = Math.max(1, (int) Math.ceil(d / Math.max(0.1, cfg.postSpacingM)));
            r.intermediatePosts += Math.max(0, intervals - 1);
        }

        double baseWire = r.perimeterM * Math.max(1, cfg.strands);
        r.wireM = baseWire * (1.0 + Math.max(0, cfg.wireReservePct) / 100.0);
        r.wireRolls = (int) Math.ceil(r.wireM / Math.max(1.0, cfg.wireRollM));

        int fixationPosts = r.cornerPosts + r.intermediatePosts;
        double baseStaples = fixationPosts * Math.max(1, cfg.strands);
        r.staples = (int) Math.ceil(baseStaples * (1.0 + Math.max(0, cfg.stapleReservePct) / 100.0));
        r.stapleKg = r.staples / Math.max(1.0, cfg.staplesPerKg);
        double staplesPerPackage = Math.max(1.0, cfg.staplesPerKg * Math.max(0.1, cfg.staplePackageKg));
        r.staplePackages = (int) Math.ceil(r.staples / staplesPerPackage);
        return r;
    }
}
