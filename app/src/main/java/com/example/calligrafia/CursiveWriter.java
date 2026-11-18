package com.example.calligrafia;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CursiveWriter {
    private CursiveWriter() {}

    static Generated generate(String text, String styleId, JSONObject data) throws JSONException {
        JSONObject style = data.getJSONObject("styles").getJSONObject(styleId);
        boolean italicAlphabet = "italica".equals(style.optString("alfabeto"));
        JSONObject alphabet = data.getJSONObject(italicAlphabet ? "italica" : "letters");
        JSONObject capitals = data.getJSONObject("capitals");
        double slant = style.getDouble("slant");
        double ascender = style.getDouble("asc");
        double descender = style.getDouble("disc");
        double width = style.getDouble("larg");
        double spacing = style.getDouble("spazio");
        double thick = style.getDouble("spessore");
        double hairline = style.getDouble("filetto");
        Double broadNib = style.has("penna_larga") ? style.getDouble("penna_larga") : null;

        List<List<double[]>> bodies = new ArrayList<>();
        List<List<double[]>> extras = new ArrayList<>();
        List<double[]> connectedPath = new ArrayList<>();
        List<Character> ignored = new ArrayList<>();
        double cursor = 0;
        String cleaned = text == null ? "" : text;

        for (String word : cleaned.split(" ", -1)) {
            if (word.isEmpty()) {
                cursor += spacing;
                continue;
            }
            for (int index = 0; index < word.length(); index++) {
                char character = word.charAt(index);
                boolean uppercase = Character.isUpperCase(character);
                String key = String.valueOf(character);
                if (italicAlphabet && uppercase) {
                    if (!capitals.has(key)) {
                        ignored.add(character);
                        continue;
                    }
                    if (!connectedPath.isEmpty()) {
                        bodies.add(connectedPath);
                        connectedPath = new ArrayList<>();
                    }
                    JSONArray capital = capitals.getJSONArray(key);
                    List<List<double[]>> leading = readPaths(capital.getJSONArray(0));
                    List<double[]> main = readPath(capital.getJSONArray(1));
                    for (List<double[]> path : leading) bodies.add(transform(path, cursor, 0, width, ascender, descender));
                    bodies.add(transform(main, cursor, 0, width, ascender, descender));
                    for (List<double[]> path : readPaths(capital.getJSONArray(3))) {
                        extras.add(transform(path, cursor, 0, width, ascender, descender));
                    }
                    cursor += capital.getDouble(2) * width;
                    continue;
                }

                if (uppercase) {
                    if (!capitals.has(key)) {
                        ignored.add(character);
                        continue;
                    }
                    if (!connectedPath.isEmpty()) {
                        bodies.add(connectedPath);
                        connectedPath = new ArrayList<>();
                    }
                    JSONArray capital = capitals.getJSONArray(key);
                    for (List<double[]> path : readPaths(capital.getJSONArray(0))) {
                        bodies.add(transform(path, cursor, 0, width, ascender, descender));
                    }
                    List<double[]> main = transform(readPath(capital.getJSONArray(1)), cursor, 0, width, ascender, descender);
                    connectedPath.addAll(main);
                    for (List<double[]> path : readPaths(capital.getJSONArray(3))) {
                        extras.add(transform(path, cursor, 0, width, ascender, descender));
                    }
                    double advance = capital.getDouble(2) * width;
                    if (index < word.length() - 1) connect(connectedPath, cursor + advance, -0.92);
                    cursor += advance;
                    continue;
                }

                key = key.toLowerCase(Locale.ROOT);
                if (!alphabet.has(key)) {
                    ignored.add(character);
                    continue;
                }
                JSONArray letter = alphabet.getJSONArray(key);
                List<double[]> body = transform(readPath(letter.getJSONArray(0)), cursor, 0, width, ascender, descender);
                if (connectedPath.isEmpty()) connectedPath.addAll(body);
                else for (int i = 1; i < body.size(); i++) connectedPath.add(body.get(i));
                for (List<double[]> path : readPaths(letter.getJSONArray(2))) {
                    extras.add(transform(path, cursor, 0, width, ascender, descender));
                }
                double advance = letter.getDouble(1) * width;
                boolean nextIsDetachedCapital = italicAlphabet && index < word.length() - 1
                        && Character.isUpperCase(word.charAt(index + 1));
                if (!nextIsDetachedCapital && index < word.length() - 1) connect(connectedPath, cursor + advance, -0.92);
                else if (index == word.length() - 1 && !connectedPath.isEmpty()) {
                    double[] end = connectedPath.get(connectedPath.size() - 1);
                    double direction = end[1] > -0.5 ? -0.1 : -0.05;
                    connectedPath.add(new double[]{end[0] + 0.2, end[1] + direction});
                    connectedPath.add(new double[]{end[0] + 0.45, end[1] - (end[1] > -0.5 ? 0.45 : 0.05)});
                }
                cursor += advance;
            }
            if (!connectedPath.isEmpty()) {
                bodies.add(connectedPath);
                connectedPath = new ArrayList<>();
            }
            cursor += spacing;
        }

        List<StrokePath> output = new ArrayList<>();
        for (List<double[]> body : bodies) appendStroke(output, smooth(body, 3), slant, thick, hairline, broadNib);
        for (List<double[]> extra : extras) appendStroke(output, smooth(extra, extra.size() > 2 ? 1 : 0),
                slant, thick, hairline, broadNib);
        StringBuilder omitted = new StringBuilder();
        for (Character character : ignored) {
            if (omitted.indexOf(String.valueOf(character)) < 0) omitted.append(character).append(' ');
        }
        return new Generated(output, cursor, omitted.toString().trim());
    }

    private static void connect(List<double[]> path, double advance, double targetY) {
        if (path.isEmpty()) return;
        double[] end = path.get(path.size() - 1);
        if (end[1] < -0.5) {
            path.add(new double[]{(end[0] + advance) / 2, (end[1] + targetY) / 2});
            path.add(new double[]{advance, targetY});
        } else {
            path.add(new double[]{end[0] + 0.15, end[1] + 0.02});
            path.add(new double[]{advance - 0.25, (end[1] + targetY) / 2});
            path.add(new double[]{advance, targetY});
        }
    }

    private static List<double[]> transform(List<double[]> path, double offset, double vertical,
                                            double width, double ascender, double descender) {
        List<double[]> result = new ArrayList<>();
        for (double[] point : path) {
            double y = point[1];
            if (y < -1.2) y = -1 + (y + 1) * ascender;
            if (y > 0.2) y *= descender;
            result.add(new double[]{offset + point[0] * width, y + vertical});
        }
        return result;
    }

    private static List<List<double[]>> readPaths(JSONArray paths) throws JSONException {
        List<List<double[]>> result = new ArrayList<>();
        for (int i = 0; i < paths.length(); i++) result.add(readPath(paths.getJSONArray(i)));
        return result;
    }

    private static List<double[]> readPath(JSONArray points) throws JSONException {
        List<double[]> result = new ArrayList<>();
        for (int i = 0; i < points.length(); i++) {
            JSONArray point = points.getJSONArray(i);
            result.add(new double[]{point.getDouble(0), point.getDouble(1)});
        }
        return result;
    }

    private static List<double[]> smooth(List<double[]> points, int rounds) {
        List<double[]> current = new ArrayList<>(points);
        for (int round = 0; round < rounds; round++) {
            if (current.size() < 3) break;
            List<double[]> next = new ArrayList<>();
            next.add(current.get(0));
            for (int i = 1; i < current.size(); i++) {
                double[] a = current.get(i - 1), b = current.get(i);
                next.add(new double[]{0.75 * a[0] + 0.25 * b[0], 0.75 * a[1] + 0.25 * b[1]});
                next.add(new double[]{0.25 * a[0] + 0.75 * b[0], 0.25 * a[1] + 0.75 * b[1]});
            }
            next.add(current.get(current.size() - 1));
            current = next;
        }
        return current;
    }

    private static void appendStroke(List<StrokePath> output, List<double[]> source, double slant,
                                     double thick, double hairline, Double broadNib) {
        if (source.size() < 2) return;
        List<float[]> points = new ArrayList<>();
        for (double[] point : source) points.add(new float[]{(float) (point[0] - point[1] * slant), (float) point[1]});
        List<Float> widths = new ArrayList<>();
        double previousPressure = 0;
        double bladeX = broadNib == null ? 0 : Math.cos(Math.toRadians(broadNib));
        double bladeY = broadNib == null ? 0 : -Math.sin(Math.toRadians(broadNib));
        for (int i = 1; i < points.size(); i++) {
            float[] a = points.get(i - 1), b = points.get(i);
            double dx = b[0] - a[0], dy = b[1] - a[1], distance = Math.hypot(dx, dy);
            if (distance == 0) {
                widths.add(0f);
                continue;
            }
            double factor;
            if (broadNib != null) {
                factor = Math.pow(Math.abs(dx * bladeY - dy * bladeX) / distance, 1.5);
                factor = 0.35 * previousPressure + 0.65 * factor;
            } else {
                factor = Math.pow(Math.max(0, dy / distance), 1.5);
                factor = 0.6 * previousPressure + 0.4 * factor;
            }
            previousPressure = factor;
            widths.add((float) (hairline + (thick - hairline) * factor));
        }
        output.add(new StrokePath(points, widths));
    }

    static final class Generated {
        final List<StrokePath> strokes;
        final double width;
        final String ignoredCharacters;

        Generated(List<StrokePath> strokes, double width, String ignoredCharacters) {
            this.strokes = strokes;
            this.width = width;
            this.ignoredCharacters = ignoredCharacters;
        }
    }

    static final class StrokePath {
        final List<float[]> points;
        final List<Float> widths;

        StrokePath(List<float[]> points, List<Float> widths) {
            this.points = points;
            this.widths = widths;
        }
    }
}
