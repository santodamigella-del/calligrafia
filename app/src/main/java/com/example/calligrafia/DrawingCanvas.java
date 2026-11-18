package com.example.calligrafia;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DrawingCanvas extends View {
    private static final int PAPER = 0xFFFFFCF3;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke activeStroke;
    private float penWidth = 9f;
    private float nibAngle = 30f;
    private float pressureScale = 1f;
    private int inkColor = 0xFF38271E;
    private boolean guidesVisible = true;
    private int paperColor = PAPER;
    private boolean modelVisible;
    private String modelLetter = "a";
    private List<List<float[]>> modelStrokes = new ArrayList<>();
    private float animationProgress = 1f;
    private PendingText pendingText;
    private final List<TextItem> textItems = new ArrayList<>();
    private int nextGroupId = 1;
    private Typeface activeTypeface = Typeface.DEFAULT;
    private String activeFontAsset = "";
    private boolean rightToLeftModel;

    public DrawingCanvas(Context context) {
        super(context);
        init();
    }

    public void insertCursiveWord(CursiveWriter.Generated word) {
        if (word.strokes.isEmpty() || word.width <= 0) return;
        float availableWidth = Math.max(1f, getWidth() - 2 * getPaddingLeft() - 32f);
        float scale = Math.max(12f, Math.min(penWidth * 5f, availableWidth / (float) word.width));
        float originX = 20f;
        float baseline = Math.max(64f, getHeight() * 0.62f);
        int groupId = nextGroupId++;
        for (CursiveWriter.StrokePath path : word.strokes) {
            Stroke generated = new Stroke(penWidth, inkColor, nibAngle, pressureScale);
            generated.groupId = groupId;
            generated.segmentWidths = new ArrayList<>();
            for (float[] point : path.points) {
                generated.points.add(new Point(originX + point[0] * scale, baseline + point[1] * scale, 1f));
            }
            for (float width : path.widths) generated.segmentWidths.add(Math.max(0.7f, width * scale));
            strokes.add(generated);
        }
        invalidate();
    }

    public DrawingCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setFocusable(true);
    }

    public void setPenSettings(float width, float angle, float pressure) {
        penWidth = width;
        nibAngle = angle;
        pressureScale = pressure;
        invalidate();
    }

    public void setPenWidth(float value) {
        penWidth = value;
    }

    public void setNibAngle(float value) {
        nibAngle = value;
    }

    public void setPressureScale(float value) {
        pressureScale = value;
    }

    public void setInkColor(int color) {
        inkColor = color;
    }

    public void setPaperColor(int color) {
        paperColor = color;
        invalidate();
    }

    public void setTypeface(Typeface typeface, String fontAsset) {
        activeTypeface = typeface == null ? Typeface.DEFAULT : typeface;
        activeFontAsset = fontAsset == null ? "" : fontAsset;
        invalidate();
    }

    public String getActiveFontAsset() {
        return activeFontAsset;
    }

    public int getPaperColor() {
        return paperColor;
    }

    public void setReferenceModel(String letter, List<List<float[]>> model) {
        setReferenceModel(letter, model, false);
    }

    public void setReferenceModel(String letter, List<List<float[]>> model, boolean rightToLeft) {
        modelLetter = letter;
        modelStrokes = model == null ? new ArrayList<>() : model;
        rightToLeftModel = rightToLeft;
        animationProgress = 1f;
        invalidate();
    }

    public void setModelVisible(boolean visible) {
        modelVisible = visible;
        invalidate();
    }

    public void setAnimationProgress(float progress) {
        animationProgress = Math.max(0f, Math.min(1f, progress));
        invalidate();
    }

    public void setPendingText(String text, int color, float size) {
        pendingText = new PendingText(text, color, size);
    }

    public int getInkColor() {
        return inkColor;
    }

    public List<List<float[]>> getStrokesForAnalysis() {
        List<List<float[]>> result = new ArrayList<>();
        for (Stroke stroke : strokes) {
            List<float[]> points = new ArrayList<>();
            for (Point point : stroke.points) points.add(new float[]{point.x, point.y});
            result.add(points);
        }
        return result;
    }

    public void toggleGuides() {
        guidesVisible = !guidesVisible;
        invalidate();
    }

    public void undo() {
        if (!textItems.isEmpty()) {
            textItems.remove(textItems.size() - 1);
            invalidate();
            return;
        }
        if (!strokes.isEmpty()) {
            int groupId = strokes.get(strokes.size() - 1).groupId;
            strokes.remove(strokes.size() - 1);
            if (groupId != 0) {
                while (!strokes.isEmpty() && strokes.get(strokes.size() - 1).groupId == groupId) {
                    strokes.remove(strokes.size() - 1);
                }
            }
            invalidate();
        }
    }

    public void clear() {
        strokes.clear();
        activeStroke = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(paperColor);
        if (guidesVisible) drawGuides(canvas);
        for (Stroke stroke : strokes) drawStroke(canvas, stroke);
        if (activeStroke != null) drawStroke(canvas, activeStroke);
        drawReferenceModel(canvas);
        drawTextItems(canvas);
    }

    private void drawGuides(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(paperColor == 0xFF202124 ? 0xFF41434A : 0xFFD8CCB9);
        float spacing = 62f * getResources().getDisplayMetrics().density;
        for (float y = spacing; y < getHeight(); y += spacing) {
            canvas.drawLine(0, y, getWidth(), y, paint);
        }
        paint.setColor(0xFFC9A99A);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(0, spacing * 2, getWidth(), spacing * 2, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawStroke(Canvas canvas, Stroke stroke) {
        if (stroke.points.isEmpty()) return;
        paint.setColor(stroke.color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (stroke.points.size() == 1) {
            Point point = stroke.points.get(0);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(stroke.color);
            canvas.drawCircle(point.x, point.y,
                    stroke.width * pressure(point.pressure, stroke.pressureScale) / 2f, paint);
            paint.setStyle(Paint.Style.STROKE);
            return;
        }
        float nibRadians = (float) Math.toRadians(stroke.angle);
        for (int i = 1; i < stroke.points.size(); i++) {
            Point start = stroke.points.get(i - 1);
            Point end = stroke.points.get(i);
            float direction = (float) Math.atan2(end.y - start.y, end.x - start.x);
            float contrast = (float) Math.abs(Math.sin(direction - nibRadians));
            float widthFactor = 0.25f + 0.75f * contrast;
            paint.setStrokeWidth(stroke.segmentWidths == null
                    ? stroke.width * widthFactor * pressure((start.pressure + end.pressure) / 2f, stroke.pressureScale)
                    : stroke.segmentWidths.get(i - 1));
            Path segment = new Path();
            segment.moveTo(start.x, start.y);
            segment.lineTo(end.x, end.y);
            canvas.drawPath(segment, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawReferenceModel(Canvas canvas) {
        if (!modelVisible || modelStrokes.isEmpty()) return;
        float unit = Math.min(getWidth() * 0.52f, getHeight() * 0.74f);
        float left = rightToLeftModel ? getWidth() * 0.84f - unit : getWidth() * 0.14f;
        float top = Math.max(12f, getHeight() * 0.13f);
        float baseline = top + unit;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, penWidth * 0.32f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setPathEffect(new DashPathEffect(new float[]{12f, 9f}, 0));
        int modelColor = paperColor == 0xFF202124 ? 0xBFE9D9C5 : 0x80664D3C;
        paint.setColor(modelColor);
        float totalLength = 0f;
        List<Path> paths = new ArrayList<>();
        List<Float> lengths = new ArrayList<>();
        for (List<float[]> stroke : modelStrokes) {
            Path path = new Path();
            if (stroke.isEmpty()) {
                paths.add(path);
                lengths.add(0f);
                continue;
            }
            float[] first = stroke.get(0);
            path.moveTo(left + first[0] * unit, top + first[1] * unit);
            for (int i = 1; i < stroke.size(); i++) {
                float[] point = stroke.get(i);
                path.lineTo(left + point[0] * unit, top + point[1] * unit);
            }
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            float length = approximatePathLength(stroke, unit);
            totalLength += length;
            paths.add(path);
            lengths.add(length);
        }
        float targetLength = totalLength * animationProgress;
        float completed = 0f;
        for (int i = 0; i < paths.size(); i++) {
            float length = lengths.get(i);
            if (length <= 0f || completed >= targetLength) break;
            Path path = paths.get(i);
            if (completed + length <= targetLength) {
                canvas.drawPath(path, paint);
                completed += length;
            } else {
                android.graphics.PathMeasure measure = new android.graphics.PathMeasure(path, false);
                Path partial = new Path();
                measure.getSegment(0, Math.max(0.1f, targetLength - completed), partial, true);
                canvas.drawPath(partial, paint);
                break;
            }
        }
        paint.setPathEffect(null);
        paint.setColor(modelColor);
        paint.setStrokeWidth(1f);
        canvas.drawLine(left - 8f, baseline, left + unit + 8f, baseline, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(paperColor == 0xFF202124 ? 0xFFE9D9C5 : 0xFF6D3516);
        paint.setTextSize(15f);
        canvas.drawText(modelLetter, left, Math.max(16f, top - 4f), paint);
    }

    private float approximatePathLength(List<float[]> points, float unit) {
        float length = 0f;
        for (int i = 1; i < points.size(); i++) {
            float dx = (points.get(i)[0] - points.get(i - 1)[0]) * unit;
            float dy = (points.get(i)[1] - points.get(i - 1)[1]) * unit;
            length += (float) Math.hypot(dx, dy);
        }
        return length;
    }

    private void drawTextItems(Canvas canvas) {
        for (TextItem item : textItems) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(item.color);
            paint.setTextSize(item.size);
            paint.setTypeface(item.typeface);
            canvas.drawText(item.text, item.x, item.y, paint);
        }
        paint.setTypeface(Typeface.DEFAULT);
    }

    private float pressure(float measured, float scale) {
        return Math.max(0.25f, Math.min(1.5f, measured * scale));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        float measuredPressure = event.getPressure() > 0 ? event.getPressure() : 1f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (pendingText != null) {
                    textItems.add(new TextItem(pendingText.text, x, y, pendingText.size, pendingText.color,
                            activeTypeface, activeFontAsset));
                    pendingText = null;
                    invalidate();
                    performClick();
                    return true;
                }
                activeStroke = new Stroke(penWidth, inkColor, nibAngle, pressureScale);
                activeStroke.points.add(new Point(x, y, measuredPressure));
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeStroke != null) {
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        activeStroke.points.add(new Point(event.getHistoricalX(i), event.getHistoricalY(i),
                                event.getHistoricalPressure(i)));
                    }
                    activeStroke.points.add(new Point(x, y, measuredPressure));
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (activeStroke != null) {
                    strokes.add(activeStroke);
                    activeStroke = null;
                    invalidate();
                }
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    public JSONArray toJson() throws JSONException {
        JSONArray output = new JSONArray();
        for (Stroke stroke : strokes) {
            JSONObject savedStroke = new JSONObject();
            savedStroke.put("width", stroke.width);
            savedStroke.put("color", stroke.color);
            savedStroke.put("angle", stroke.angle);
            savedStroke.put("pressureScale", stroke.pressureScale);
            savedStroke.put("groupId", stroke.groupId);
            if (stroke.segmentWidths != null) {
                JSONArray widths = new JSONArray();
                for (float width : stroke.segmentWidths) widths.put(width);
                savedStroke.put("segmentWidths", widths);
            }
            JSONArray points = new JSONArray();
            for (Point point : stroke.points) {
                JSONArray savedPoint = new JSONArray();
                savedPoint.put(point.x);
                savedPoint.put(point.y);
                savedPoint.put(point.pressure);
                points.put(savedPoint);
            }
            savedStroke.put("points", points);
            output.put(savedStroke);
        }
        return output;
    }

    public JSONArray textToJson() throws JSONException {
        JSONArray output = new JSONArray();
        for (TextItem item : textItems) {
            JSONObject text = new JSONObject();
            text.put("text", item.text);
            text.put("x", item.x);
            text.put("y", item.y);
            text.put("size", item.size);
            text.put("color", item.color);
            text.put("fontAsset", item.fontAsset);
            output.put(text);
        }
        return output;
    }

    public void loadTextJson(JSONArray savedTexts) throws JSONException {
        List<TextItem> restored = new ArrayList<>();
        if (savedTexts != null) {
            for (int i = 0; i < savedTexts.length(); i++) {
                JSONObject text = savedTexts.getJSONObject(i);
                restored.add(new TextItem(text.getString("text"), (float) text.getDouble("x"),
                        (float) text.getDouble("y"), (float) text.getDouble("size"), text.getInt("color"),
                        FontCatalog.forAsset(getContext(), text.optString("fontAsset", ""), ""),
                        text.optString("fontAsset", "")));
            }
        }
        textItems.clear();
        textItems.addAll(restored);
        invalidate();
    }

    public void loadJson(JSONArray savedStrokes) throws JSONException {
        if (savedStrokes == null) throw new JSONException("Missing strokes");
        List<Stroke> restored = new ArrayList<>();
        for (int i = 0; i < savedStrokes.length(); i++) {
            JSONObject savedStroke = savedStrokes.getJSONObject(i);
            Stroke stroke = new Stroke((float) savedStroke.getDouble("width"), savedStroke.getInt("color"),
                    (float) savedStroke.getDouble("angle"), (float) savedStroke.optDouble("pressureScale", 1f));
            stroke.groupId = savedStroke.optInt("groupId", 0);
            JSONArray savedWidths = savedStroke.optJSONArray("segmentWidths");
            if (savedWidths != null) {
                stroke.segmentWidths = new ArrayList<>();
                for (int j = 0; j < savedWidths.length(); j++) stroke.segmentWidths.add((float) savedWidths.getDouble(j));
                if (stroke.groupId >= nextGroupId) nextGroupId = stroke.groupId + 1;
            }
            JSONArray points = savedStroke.getJSONArray("points");
            for (int j = 0; j < points.length(); j++) {
                JSONArray point = points.getJSONArray(j);
                stroke.points.add(new Point((float) point.getDouble(0), (float) point.getDouble(1),
                        (float) point.getDouble(2)));
            }
            restored.add(stroke);
        }
        strokes.clear();
        strokes.addAll(restored);
        invalidate();
    }

    public void clearAll() {
        strokes.clear();
        textItems.clear();
        pendingText = null;
        activeStroke = null;
        invalidate();
    }

    public String toSvg() {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
                .append(getWidth()).append("\" height=\"").append(getHeight())
                .append("\" viewBox=\"0 0 ").append(getWidth()).append(' ').append(getHeight()).append("\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#")
                .append(String.format(java.util.Locale.US, "%06X", paperColor & 0xFFFFFF)).append("\"/>");
        for (Stroke stroke : strokes) {
            if (stroke.points.size() == 1) {
                Point point = stroke.points.get(0);
                svg.append("<circle cx=\"").append(point.x).append("\" cy=\"").append(point.y)
                        .append("\" r=\"").append(stroke.width / 2f)
                        .append("\" fill=\"#")
                        .append(String.format(java.util.Locale.US, "%06X", stroke.color & 0xFFFFFF))
                        .append("\"/>");
            }
            for (int i = 1; i < stroke.points.size(); i++) {
                Point start = stroke.points.get(i - 1);
                Point end = stroke.points.get(i);
                float width = stroke.segmentWidths == null ? stroke.width
                        : stroke.segmentWidths.get(i - 1);
                svg.append("<path d=\"M").append(start.x).append(' ').append(start.y)
                        .append(" L").append(end.x).append(' ').append(end.y)
                        .append("\" fill=\"none\" stroke=\"#")
                        .append(String.format(java.util.Locale.US, "%06X", stroke.color & 0xFFFFFF))
                        .append("\" stroke-width=\"").append(width)
                        .append("\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>");
            }
        }
        for (TextItem item : textItems) {
            svg.append("<text x=\"").append(item.x).append("\" y=\"").append(item.y)
                    .append("\" font-size=\"").append(item.size).append("\" fill=\"#")
                    .append(String.format(java.util.Locale.US, "%06X", item.color & 0xFFFFFF))
                    .append("\">").append(escapeXml(item.text)).append("</text>");
        }
        return svg.append("</svg>").toString();
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    public boolean exportWorksheet(android.graphics.pdf.PdfDocument document, String styleName,
                                   List<String> letters, Map<String, List<List<float[]>>> models) {
        if (letters == null || letters.isEmpty()) return false;
        int rowsPerPage = 6;
        int pageCount = (letters.size() + rowsPerPage - 1) / rowsPerPage;
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            android.graphics.pdf.PdfDocument.Page page =
                    document.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create());
            Canvas canvas = page.getCanvas();
            canvas.drawColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF49382B);
            paint.setTextSize(20);
            paint.setTypeface(Typeface.create("serif", Typeface.BOLD));
            canvas.drawText("Calligrafia Studio · " + styleName, 36, 42, paint);
            paint.setTextSize(10);
            paint.setTypeface(Typeface.DEFAULT);
            canvas.drawText("Traccia il modello, poi ripeti la forma. Schema didattico da verificare su una fonte.", 36, 62, paint);
            for (int row = 0; row < rowsPerPage; row++) {
                int index = pageIndex * rowsPerPage + row;
                if (index >= letters.size()) break;
                float top = 88 + row * 122;
                paint.setColor(0xFF9A866F);
                paint.setStrokeWidth(0.7f);
                canvas.drawLine(36, top + 54, 559, top + 54, paint);
                canvas.drawLine(36, top + 91, 559, top + 91, paint);
                canvas.drawLine(36, top + 112, 559, top + 112, paint);
                String letter = letters.get(index);
                paint.setTextSize(18);
                paint.setColor(0xFF49382B);
                canvas.drawText(letter, 38, top + 38, paint);
                List<List<float[]>> model = models.get(letter);
                if (model == null) continue;
                float unit = 50;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.7f);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setColor(0xFFB7A58F);
                for (int copy = 0; copy < 2; copy++) {
                    float xOffset = 78 + copy * 132;
                    for (List<float[]> stroke : model) {
                        if (stroke.isEmpty()) continue;
                        Path path = new Path();
                        path.moveTo(xOffset + stroke.get(0)[0] * unit, top + stroke.get(0)[1] * unit);
                        for (int point = 1; point < stroke.size(); point++) {
                            path.lineTo(xOffset + stroke.get(point)[0] * unit,
                                    top + stroke.get(point)[1] * unit);
                        }
                        canvas.drawPath(path, paint);
                    }
                }
                paint.setStyle(Paint.Style.FILL);
            }
            document.finishPage(page);
        }
        return true;
    }

    public String evaluateAgainstModel() {
        List<Stroke> drawn = new ArrayList<>();
        for (Stroke stroke : strokes) if (stroke.points.size() > 1) drawn.add(stroke);
        if (drawn.isEmpty()) return "Disegna almeno un tratto prima di valutare l'esercizio.";
        if (modelStrokes.isEmpty()) return "Per questa lettera non c'è uno schema vettoriale. Confronta il gesto con la guida didattica e con una fonte verificata.";
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (Stroke stroke : drawn) for (Point point : stroke.points) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
        }
        float drawnWidth = Math.max(1f, maxX - minX);
        float drawnHeight = Math.max(1f, maxY - minY);
        float ratio = drawnWidth / drawnHeight;
        float modelMinX = Float.MAX_VALUE, modelMaxX = -Float.MAX_VALUE;
        float modelMinY = Float.MAX_VALUE, modelMaxY = -Float.MAX_VALUE;
        for (List<float[]> stroke : modelStrokes) for (float[] point : stroke) {
            modelMinX = Math.min(modelMinX, point[0]);
            modelMaxX = Math.max(modelMaxX, point[0]);
            modelMinY = Math.min(modelMinY, point[1]);
            modelMaxY = Math.max(modelMaxY, point[1]);
        }
        float modelRatio = (modelMaxX - modelMinX) / Math.max(0.01f, modelMaxY - modelMinY);
        float difference = Math.abs(ratio - modelRatio);
        String shape = difference < 0.22f ? "Le proporzioni sono vicine al riferimento."
                : ratio > modelRatio ? "La forma è più larga del riferimento: prova a stringere il corpo."
                : "La forma è più stretta del riferimento: prova ad aprire il corpo.";
        String count = drawn.size() == modelStrokes.size() ? "Il numero di tratti corrisponde allo schema."
                : "Lo schema propone " + modelStrokes.size() + " tratti; ne hai disegnati " + drawn.size() + ".";
        return shape + "\n" + count
                + "\nRiscontro geometrico orientativo: non valuta fedeltà storica, qualità del gesto o pressione reale.";
    }

    public String analyzeRegularity() {
        List<RectF> bounds = new ArrayList<>();
        List<Float> slants = new ArrayList<>();
        for (Stroke stroke : strokes) {
            if (stroke.points.size() < 2) continue;
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (Point point : stroke.points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
            if (maxY - minY < 2f && maxX - minX < 2f) continue;
            bounds.add(new RectF(minX, minY, maxX, maxY));
            Point start = stroke.points.get(0);
            Point end = stroke.points.get(stroke.points.size() - 1);
            float dx = end.x - start.x, dy = end.y - start.y;
            if (Math.abs(dy) > Math.abs(dx) * 1.4f) slants.add((float) Math.toDegrees(Math.atan2(dx, Math.abs(dy))));
        }
        if (bounds.size() < 4) return "Servono almeno 4 tratti separati per un riscontro indicativo. Disegna lettere staccate e riprova.";
        float[] heights = new float[bounds.size()];
        float[] baselines = new float[bounds.size()];
        for (int i = 0; i < bounds.size(); i++) {
            heights[i] = bounds.get(i).height();
            baselines[i] = bounds.get(i).bottom;
        }
        float hMean = mean(heights), baselineMean = mean(baselines);
        float hCv = coefficientOfVariation(heights, hMean);
        float baseCv = coefficientOfVariation(baselines, baselineMean);
        String result = "Altezza dei tratti: " + score(hCv, 0.3f) + "/100.\n"
                + "Allineamento della base: " + score(baseCv, 0.25f) + "/100.\n";
        if (slants.size() >= 3) {
            float slantMean = mean(toArray(slants));
            float slantStd = standardDeviation(toArray(slants), slantMean);
            result += "Coerenza dell'inclinazione: " + score(slantStd, 12f) + "/100.\n";
        } else {
            result += "Inclinazione: dati insufficienti nei tratti separati.\n";
        }
        return result + "Analisi geometrica orientativa per tratti staccati; non equivale a una valutazione paleografica.";
    }

    private float[] toArray(List<Float> values) {
        float[] output = new float[values.size()];
        for (int i = 0; i < values.size(); i++) output[i] = values.get(i);
        return output;
    }

    private float mean(float[] values) {
        float sum = 0;
        for (float value : values) sum += value;
        return values.length == 0 ? 0f : sum / values.length;
    }

    private float coefficientOfVariation(float[] values, float mean) {
        if (mean <= 0f) return 1f;
        return standardDeviation(values, mean) / mean;
    }

    private float standardDeviation(float[] values, float mean) {
        if (values.length < 2) return 0f;
        float sum = 0f;
        for (float value : values) sum += (value - mean) * (value - mean);
        return (float) Math.sqrt(sum / values.length);
    }

    private int score(float variation, float limit) {
        return Math.max(0, Math.min(100, Math.round(100f * (1f - variation / limit))));
    }

    private static final class Stroke {
        final float width;
        final int color;
        final float angle;
        final float pressureScale;
        final List<Point> points = new ArrayList<>();
        List<Float> segmentWidths;
        int groupId;

        Stroke(float width, int color, float angle, float pressureScale) {
            this.width = width;
            this.color = color;
            this.angle = angle;
            this.pressureScale = pressureScale;
        }
    }

    private static final class Point {
        final float x;
        final float y;
        final float pressure;

        Point(float x, float y, float pressure) {
            this.x = x;
            this.y = y;
            this.pressure = pressure;
        }
    }

    private static final class PendingText {
        final String text;
        final int color;
        final float size;

        PendingText(String text, int color, float size) {
            this.text = text;
            this.color = color;
            this.size = size;
        }
    }

    private static final class TextItem {
        final String text;
        final float x, y, size;
        final int color;
        final Typeface typeface;
        final String fontAsset;

        TextItem(String text, float x, float y, float size, int color, Typeface typeface, String fontAsset) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
            this.typeface = typeface;
            this.fontAsset = fontAsset;
        }
    }
}
