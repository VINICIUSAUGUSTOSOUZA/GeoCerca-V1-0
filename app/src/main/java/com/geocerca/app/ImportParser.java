package com.geocerca.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ImportParser {
    private ImportParser() {}

    public static List<GeoPoint> parseTxt(InputStream in) throws IOException {
        List<GeoPoint> out = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        int auto = 1;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] raw;
            if (line.contains(";")) raw = line.split(";");
            else if (line.contains("\t")) raw = line.split("\t");
            else if (line.contains(",")) raw = line.split(",");
            else raw = line.split("\\s+");
            List<String> cols = new ArrayList<>();
            for (String s : raw) if (!s.trim().isEmpty()) cols.add(s.trim());
            if (cols.size() < 3) continue;

            String name = cols.get(0);
            Double n1 = toNumber(cols.get(1));
            Double n2 = toNumber(cols.get(2));
            Double z = cols.size() >= 4 ? toNumber(cols.get(3)) : 0.0;
            if (n1 == null || n2 == null) continue;
            if (name.matches("[-+]?\\d+(?:[.,]\\d+)?")) name = String.format(Locale.US, "P%02d", auto);

            double east;
            double north;
            if (Math.abs(n1) > 1000000 && Math.abs(n2) < 1000000) {
                north = n1;
                east = n2;
            } else {
                east = n1;
                north = n2;
            }
            out.add(new GeoPoint(name, east, north, z == null ? 0.0 : z, false, 0));
            auto++;
        }
        return out;
    }

    public static List<GeoPoint> parseKml(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');

        String xml = sb.toString();
        List<String> coordinateBlocks = new ArrayList<>();

        // Primeiro procura anéis de polígonos, evitando pontos e linhas soltas do KML.
        Pattern ringPattern = Pattern.compile(
                "<LinearRing[^>]*>.*?<coordinates[^>]*>(.*?)</coordinates>.*?</LinearRing>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher ringMatcher = ringPattern.matcher(xml);
        while (ringMatcher.find()) coordinateBlocks.add(ringMatcher.group(1));

        // Compatibilidade com KMLs simples que trazem apenas <coordinates>.
        if (coordinateBlocks.isEmpty()) {
            Pattern coordinatesPattern = Pattern.compile(
                    "<coordinates[^>]*>(.*?)</coordinates>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher coordinatesMatcher = coordinatesPattern.matcher(xml);
            while (coordinatesMatcher.find()) coordinateBlocks.add(coordinatesMatcher.group(1));
        }

        List<GeoPoint> out = new ArrayList<>();
        int polygonId = 0;
        for (String block : coordinateBlocks) {
            String[] tokens = block.trim().split("\\s+");
            List<GeoPoint> candidate = new ArrayList<>();
            int vertex = 1;
            GeoPoint first = null;

            for (String token : tokens) {
                String[] c = token.trim().split(",");
                if (c.length < 2) continue;
                Double lon = toNumber(c[0]);
                Double lat = toNumber(c[1]);
                Double alt = c.length >= 3 ? toNumber(c[2]) : 0.0;
                if (lon == null || lat == null) continue;

                GeoPoint gp = new GeoPoint(
                        String.format(Locale.US, "P%d-V%02d", polygonId + 1, vertex++),
                        lon,
                        lat,
                        alt == null ? 0.0 : alt,
                        true,
                        polygonId);

                if (first == null) first = gp;
                if (first != null && candidate.size() >= 3 && nearlySame(first, gp)) continue;
                candidate.add(gp);
            }

            if (candidate.size() >= 3) {
                out.addAll(candidate);
                polygonId++;
            }
        }

        if (polygonId == 0) {
            throw new IOException("Nenhum polígono com pelo menos 3 vértices foi encontrado no KML.");
        }
        return out;
    }

    private static boolean nearlySame(GeoPoint a, GeoPoint b) {
        return Math.abs(a.x - b.x) < 1e-10 && Math.abs(a.y - b.y) < 1e-10;
    }

    private static Double toNumber(String s) {
        try {
            String v = s.trim().replace(" ", "");
            if (v.contains(",") && !v.contains(".")) v = v.replace(',', '.');
            return Double.parseDouble(v);
        } catch (Exception e) {
            return null;
        }
    }
}
