package com.example.calligrafia;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class StyleRecognizer {
    private static final String[] FAMILIES = {"latino", "arabo", "cjk"};

    private StyleRecognizer() {}

    static Result recognize(List<List<float[]>> strokes, JSONObject profiles, JSONArray catalog, String forcedFamily)
            throws JSONException {
        Features features = extract(strokes);
        if (features == null) return new Result("Disegna una parola di 4-5 lettere o alcuni caratteri prima di avviare il riconoscimento.");

        Map<String, Double> familyProbability = familyProbability(features, forcedFamily);
        Map<String, String> styleNames = new HashMap<>();
        for (int i = 0; i < catalog.length(); i++) {
            JSONObject style = catalog.getJSONObject(i);
            styleNames.put(style.getString("id"), style.getString("nome"));
        }

        List<Match> matches = new ArrayList<>();
        JSONArray ids = profiles.names();
        if (ids == null) return new Result("Profili di riconoscimento non disponibili.");
        for (int i = 0; i < ids.length(); i++) {
            String id = ids.getString(i);
            JSONObject profile = profiles.getJSONObject(id);
            String family = profile.optString("f");
            double score = profileScore(features.values, profile);
            score *= 0.25 + 0.75 * familyProbability.getOrDefault(family, 0.0);
            matches.add(new Match(id, styleNames.getOrDefault(id, id), family, score));
        }
        matches.sort((left, right) -> Double.compare(right.score, left.score));
        normalizeConfidence(matches);

        int evidence = 0;
        if (features.values.getOrDefault("percorso_in_H", 0.0) >= 12) evidence++;
        if (features.values.getOrDefault("n_tratti", 0.0) >= 4) evidence++;
        if (features.values.getOrDefault("n_gruppi", 0.0) >= 3) evidence++;
        String reliability = new String[]{"molto bassa", "bassa", "media", "buona"}[evidence];
        String topFamily = Collections.max(familyProbability.entrySet(), Map.Entry.comparingByValue()).getKey();
        return new Result(matches, familyProbability, reliability, topFamily);
    }

    private static Features extract(List<List<float[]>> input) {
        List<Trace> traces = new ArrayList<>();
        for (List<float[]> source : input) {
            List<float[]> points = clean(source);
            if (!points.isEmpty()) traces.add(new Trace(points));
        }
        if (traces.isEmpty()) return null;

        List<Group> initialGroups = group(traces);
        List<Double> heights = new ArrayList<>(), widths = new ArrayList<>();
        for (Group group : initialGroups) {
            if (group.height() > 4) heights.add(group.height());
            if (group.width() > 4) widths.add(group.width());
        }
        if (heights.isEmpty() && widths.isEmpty()) return null;
        double height = Math.max(12, median(heights.isEmpty() ? widths : heights));
        List<Trace> body = new ArrayList<>();
        int dots = 0;
        for (Trace trace : traces) {
            if (trace.length < 0.22 * height) dots++;
            else body.add(trace);
        }
        if (body.isEmpty()) return null;
        List<Group> groups = group(body);
        double totalLength = 0, cornerCount = 0, curveCount = 0, straightCount = 0;
        double orthogonalLength = 0, horizontalLength = 0;
        int closed = 0, longTraces = 0, leftward = 0, horizontalTraces = 0;
        List<Double> slantValues = new ArrayList<>(), slantWeights = new ArrayList<>(), normalizedLengths = new ArrayList<>();
        List<Double> aspects = new ArrayList<>(), traceCounts = new ArrayList<>();
        for (Group group : groups) traceCounts.add((double) group.indices.size());

        for (Trace trace : body) {
            normalizedLengths.add(trace.length / height);
            if (trace.length >= 0.9 * height && distance(trace.points.get(0), trace.points.get(trace.points.size() - 1)) < 0.28 * height) closed++;
            if (trace.length >= 0.6 * height) longTraces++;
            float[] start = trace.points.get(0), end = trace.points.get(trace.points.size() - 1);
            if (Math.abs(end[0] - start[0]) > 0.8 * height) {
                horizontalTraces++;
                if (end[0] < start[0]) leftward++;
            }

            List<float[]> sampled = resample(trace.points, Math.max(2.5, height * 0.06));
            if (sampled.size() < 3) {
                totalLength += trace.length;
                continue;
            }
            double localLength = 0;
            for (int i = 1; i < sampled.size(); i++) {
                float[] a = sampled.get(i - 1), b = sampled.get(i);
                double dx = b[0] - a[0], dy = b[1] - a[1], length = Math.hypot(dx, dy);
                localLength += length;
                if (length < 1e-6) continue;
                double angle = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));
                angle = Math.min(angle, 180 - angle);
                if (angle < 12 || angle > 78) orthogonalLength += length;
                if (angle < 20) horizontalLength += length;
                if (angle > 30) {
                    if (dy > 0) { dx = -dx; dy = -dy; }
                    double slant = Math.toDegrees(Math.atan2(dx, -dy));
                    if (Math.abs(slant) <= 60) {
                        slantValues.add(slant);
                        slantWeights.add(length);
                    }
                }
            }
            totalLength += trace.length;
            int previousCorner = -10;
            for (int i = 1; i < sampled.size() - 1; i++) {
                double turn = turn(sampled.get(i - 1), sampled.get(i), sampled.get(i + 1));
                if (turn > 55 && i - previousCorner >= 3) {
                    cornerCount++;
                    previousCorner = i;
                } else if (turn >= 7 && turn <= 45) curveCount++;
                else if (turn < 7) straightCount++;
            }
        }
        if (totalLength <= 0) return null;
        Double slant = weightedMedian(slantValues, slantWeights);
        for (Group group : groups) {
            if (group.height() > 0.35 * height && group.width() > 0 && group.width() < 2.2 * height) {
                aspects.add(group.width() / group.height());
            }
        }

        List<Double> centers = new ArrayList<>();
        for (Trace trace : body) centers.add((trace.minX + trace.maxX) / 2);
        List<Double> movement = new ArrayList<>();
        for (int i = 1; i < centers.size(); i++) {
            double delta = centers.get(i) - centers.get(i - 1);
            if (Math.abs(delta) > 0.15 * height) movement.add(delta < 0 ? 1.0 : 0.0);
        }
        List<Double> rtlEvidence = new ArrayList<>();
        if (movement.size() >= 2) rtlEvidence.add(mean(movement));
        if (horizontalTraces >= 2) rtlEvidence.add((double) leftward / horizontalTraces);
        Double rtl = rtlEvidence.isEmpty() ? null : mean(rtlEvidence);

        Double drift = regressionSlope(groups);
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        for (Group group : groups) {
            minX = Math.min(minX, group.minX);
            maxX = Math.max(maxX, group.maxX);
        }
        double extent = Math.max(0.6, (maxX - minX) / height);
        int samples = Math.max(1, (int) (cornerCount + curveCount + straightCount));
        Map<String, Double> values = new HashMap<>();
        put(values, "slant", slant);
        put(values, "aspect", aspects.isEmpty() ? null : median(aspects));
        values.put("ang", cornerCount / (totalLength / height));
        values.put("curve", curveCount / samples);
        values.put("dritto", straightCount / samples);
        values.put("connect", median(normalizedLengths));
        values.put("ortho", orthogonalLength / totalLength);
        values.put("horiz", horizontalLength / totalLength);
        values.put("closed", (double) closed / Math.max(1, longTraces));
        values.put("nstroke", median(traceCounts));
        put(values, "rtl", rtl);
        put(values, "drift", drift);
        values.put("largo_max", groups.stream().mapToDouble(g -> g.width() / height).max().orElse(0));
        values.put("densita", body.size() / extent);
        values.put("percorso_in_H", totalLength / height);
        values.put("n_tratti", (double) body.size());
        values.put("n_gruppi", (double) groups.size());
        values.put("puntini", (double) dots);
        return new Features(values);
    }

    private static List<float[]> clean(List<float[]> source) {
        List<float[]> result = new ArrayList<>();
        for (float[] point : source) {
            if (point == null || point.length < 2) continue;
            if (result.isEmpty() || distance(result.get(result.size() - 1), point) > 0.01) {
                result.add(new float[]{point[0], point[1]});
            }
        }
        return result;
    }

    private static List<Group> group(List<Trace> traces) {
        List<Trace> sorted = new ArrayList<>(traces);
        sorted.sort(Comparator.comparingDouble(trace -> trace.minX));
        List<Group> ordered = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Trace trace = sorted.get(i);
            Group next = new Group(trace.minX, trace.minY, trace.maxX, trace.maxY, i);
            if (!ordered.isEmpty()) {
                Group current = ordered.get(ordered.size() - 1);
                double tolerance = 0.12 * Math.max(Math.max(current.width(), next.width()), 1);
                if (next.minX <= current.maxX + tolerance) {
                    current.include(next);
                    continue;
                }
            }
            ordered.add(next);
        }
        ordered.sort(Comparator.comparingDouble(group -> group.minX));
        return ordered;
    }

    private static List<float[]> resample(List<float[]> points, double step) {
        if (points.size() < 2 || step <= 0) return points;
        List<float[]> output = new ArrayList<>();
        output.add(points.get(0));
        double remaining = step;
        for (int i = 1; i < points.size(); i++) {
            float[] start = points.get(i - 1), end = points.get(i);
            double length = distance(start, end);
            if (length <= 0) continue;
            double advanced = 0;
            while (length - advanced >= remaining) {
                advanced += remaining;
                double fraction = advanced / length;
                output.add(new float[]{(float) (start[0] + (end[0] - start[0]) * fraction),
                        (float) (start[1] + (end[1] - start[1]) * fraction)});
                remaining = step;
            }
            remaining -= length - advanced;
        }
        if (distance(output.get(output.size() - 1), points.get(points.size() - 1)) > step * 0.4) {
            output.add(points.get(points.size() - 1));
        }
        return output;
    }

    private static double turn(float[] a, float[] b, float[] c) {
        double first = Math.atan2(b[1] - a[1], b[0] - a[0]);
        double second = Math.atan2(c[1] - b[1], c[0] - b[0]);
        double degrees = Math.toDegrees(second - first);
        while (degrees > 180) degrees -= 360;
        while (degrees < -180) degrees += 360;
        return Math.abs(degrees);
    }

    private static Double regressionSlope(List<Group> groups) {
        if (groups.size() < 3) return null;
        double[] x = new double[groups.size()], y = new double[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            x[i] = (groups.get(i).minX + groups.get(i).maxX) / 2;
            y[i] = (groups.get(i).minY + groups.get(i).maxY) / 2;
        }
        double mx = mean(x), my = mean(y), numerator = 0, denominator = 0;
        for (int i = 0; i < x.length; i++) {
            numerator += (x[i] - mx) * (y[i] - my);
            denominator += (x[i] - mx) * (x[i] - mx);
        }
        return denominator < 1e-6 ? null : Math.toDegrees(Math.atan(numerator / denominator));
    }

    private static Map<String, Double> familyProbability(Features features, String forced) {
        Map<String, Double> result = new HashMap<>();
        if ("latino".equals(forced) || "arabo".equals(forced) || "cjk".equals(forced)) {
            for (String family : FAMILIES) result.put(family, family.equals(forced) ? 1.0 : 0.0);
            return result;
        }
        Map<String, Double> c = features.values;
        double dots = c.get("puntini") / Math.max(1, c.get("n_gruppi"));
        double arabic = (ramp(c.get("horiz"), 0.20, 0.50)
                + ramp(c.get("largo_max"), 1.4, 3.0) + ramp(dots, 0, 0.6)) / 3;
        arabic *= c.containsKey("rtl") ? ramp(c.get("rtl"), 0.45, 0.85) : 0.35;
        double cjk = ramp(c.get("densita"), 3, 6);
        if (c.containsKey("aspect")) cjk *= 0.5 + 0.5 * similarity(c.get("aspect"), 1, 0.4);
        double latin = 0.55 * (1 - cjk) * (1 - arabic) + 0.15;
        double total = arabic + cjk + latin;
        result.put("latino", latin / total);
        result.put("arabo", arabic / total);
        result.put("cjk", cjk / total);
        return result;
    }

    private static double profileScore(Map<String, Double> features, JSONObject profile) throws JSONException {
        Map<String, Double> weights = new HashMap<>();
        weights.put("slant", 1.6); weights.put("aspect", 1.0); weights.put("ang", 0.8);
        weights.put("curve", 0.6); weights.put("connect", 1.6); weights.put("ortho", 1.0);
        weights.put("horiz", 0.8); weights.put("closed", 0.5); weights.put("nstroke", 0.8);
        weights.put("drift", 1.2);
        double weightedSum = 0, totalWeight = 0;
        JSONArray keys = profile.names();
        if (keys == null) return 0;
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.getString(i);
            if (key.equals("f") || key.equals("g") || !features.containsKey(key)) continue;
            JSONArray expected = profile.getJSONArray(key);
            double value = features.get(key), center = expected.getDouble(0), tolerance = expected.getDouble(1);
            double weight = weights.getOrDefault(key, 1.0);
            weightedSum += similarity(value, center, tolerance) * weight;
            totalWeight += weight;
        }
        return totalWeight == 0 ? 0 : weightedSum / totalWeight;
    }

    private static void normalizeConfidence(List<Match> matches) {
        if (matches.isEmpty()) return;
        double maximum = matches.get(0).score, total = 0;
        for (Match match : matches) {
            match.exponential = Math.exp((match.score - maximum) / 0.06);
            total += match.exponential;
        }
        for (Match match : matches) match.confidence = match.exponential * 100 / total;
    }

    private static double similarity(double value, double center, double tolerance) {
        if (tolerance <= 0) return value == center ? 1 : 0;
        double difference = (value - center) / tolerance;
        return Math.exp(-0.5 * difference * difference);
    }

    private static double ramp(double value, double low, double high) {
        return Math.max(0, Math.min(1, (value - low) / (high - low)));
    }

    private static double weightedMedian(List<Double> values, List<Double> weights) {
        if (values.isEmpty()) return 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble(values::get));
        double sum = 0;
        for (double weight : weights) sum += weight;
        double accumulated = 0;
        for (int index : order) {
            accumulated += weights.get(index);
            if (accumulated >= sum / 2) return values.get(index);
        }
        return values.get(order.get(order.size() - 1));
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
    }

    private static double mean(List<Double> values) {
        double total = 0;
        for (double value : values) total += value;
        return values.isEmpty() ? 0 : total / values.size();
    }

    private static double mean(double[] values) {
        double total = 0;
        for (double value : values) total += value;
        return values.length == 0 ? 0 : total / values.length;
    }

    private static double distance(float[] first, float[] second) {
        return Math.hypot(first[0] - second[0], first[1] - second[1]);
    }

    private static void put(Map<String, Double> values, String key, Double value) {
        if (value != null && !value.isNaN() && !value.isInfinite()) values.put(key, value);
    }

    static final class Result {
        final List<Match> matches;
        final Map<String, Double> families;
        final String reliability;
        final String family;
        final String message;

        Result(String message) {
            this.matches = Collections.emptyList();
            this.families = Collections.emptyMap();
            this.reliability = "molto bassa";
            this.family = "";
            this.message = message;
        }

        Result(List<Match> matches, Map<String, Double> families, String reliability, String family) {
            this.matches = matches;
            this.families = families;
            this.reliability = reliability;
            this.family = family;
            this.message = "";
        }

        String summary() {
            if (!message.isEmpty()) return message;
            StringBuilder output = new StringBuilder("Famiglia stimata: ")
                    .append(family.toUpperCase(Locale.ROOT))
                    .append(" · affidabilità ").append(reliability).append("\n\n");
            int limit = Math.min(5, matches.size());
            for (int i = 0; i < limit; i++) {
                Match match = matches.get(i);
                output.append(i + 1).append(". ").append(match.name)
                        .append(" — ").append(String.format(Locale.ITALY, "%.1f%%", match.confidence)).append('\n');
            }
            output.append("\nRisultato euristico basato su geometria, non su esempi addestrati. ")
                    .append("Con il mouse la pressione non è misurabile e stili simili possono confondersi.");
            if (family.equals("cjk")) output.append(" Cinese e giapponese hanno profili geometrici sovrapposti.");
            if (reliability.equals("bassa") || reliability.equals("molto bassa")) {
                output.append("\nDisegna una parola di 4-5 lettere o più caratteri per aumentare l'affidabilità.");
            }
            return output.toString();
        }
    }

    static final class Match {
        final String id, name, family;
        final double score;
        double exponential, confidence;

        Match(String id, String name, String family, double score) {
            this.id = id;
            this.name = name;
            this.family = family;
            this.score = score;
        }
    }

    private static final class Features {
        final Map<String, Double> values;
        Features(Map<String, Double> values) { this.values = values; }
    }

    private static final class Trace {
        final List<float[]> points;
        final double minX, minY, maxX, maxY, length;

        Trace(List<float[]> points) {
            this.points = points;
            double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            double pathLength = 0;
            for (int i = 0; i < points.size(); i++) {
                float[] point = points.get(i);
                x0 = Math.min(x0, point[0]); y0 = Math.min(y0, point[1]);
                x1 = Math.max(x1, point[0]); y1 = Math.max(y1, point[1]);
                if (i > 0) pathLength += distance(points.get(i - 1), point);
            }
            minX = x0; minY = y0; maxX = x1; maxY = y1; length = pathLength;
        }
    }

    private static final class Group {
        double minX, minY, maxX, maxY;
        final List<Integer> indices = new ArrayList<>();
        Group(double minX, double minY, double maxX, double maxY, int index) {
            this.minX = minX; this.minY = minY; this.maxX = maxX; this.maxY = maxY;
            indices.add(index);
        }
        void include(Group group) {
            minX = Math.min(minX, group.minX); minY = Math.min(minY, group.minY);
            maxX = Math.max(maxX, group.maxX); maxY = Math.max(maxY, group.maxY);
            indices.addAll(group.indices);
        }
        double width() { return maxX - minX; }
        double height() { return maxY - minY; }
    }
}
