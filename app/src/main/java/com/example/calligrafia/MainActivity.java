package com.example.calligrafia;

import android.content.ContentValues;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "CalligrafiaStudio";
    private static final int PAPER = 0xFFFFFCF3;
    private static final int INK = 0xFF25221D;

    private final List<JSONObject> styles = new ArrayList<>();
    private final Map<String, List<List<float[]>>> baseModels = new HashMap<>();
    private final Map<String, List<List<float[]>>> asianModels = new HashMap<>();
    private final Map<String, List<List<float[]>>> arabicModels = new HashMap<>();
    private final Map<String, List<List<float[]>>> greekModels = new HashMap<>();
    private final Map<String, Map<String, List<List<float[]>>>> styleModels = new HashMap<>();
    private LinearLayout page;
    private DrawingCanvas drawingCanvas;
    private JSONObject activeStyle;
    private JSONObject cursiveModels;
    private Spinner studioStyleSpinner;
    private Spinner modelLetterSpinner;
    private Spinner modelFamilySpinner;
    private Spinner fontSpinner;
    private Spinner paperSpinner;
    private Map<String, List<List<float[]>>> activeModels = new HashMap<>();
    private String selectedModelFamily = "Automatico";
    private String selectedFontChoice = FontCatalog.STYLE_DEFAULT;
    private ValueAnimator ductusAnimator;
    private boolean modelVisible;
    private Spinner lessonStyleSpinner;
    private Spinner lessonLevelSpinner;
    private LinearLayout lessonContent;
    private String selectedTradition = "Tutte";
    private LinearLayout libraryResults;
    private Runnable currentBackAction = this::showHome;
    private boolean atHome = true;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;
    private String pendingDocumentExport;

    private void exportProject() {
        pendingDocumentExport = "project";
        createDocumentLauncher.launch("CalligrafiaStudio.json");
    }

    private void importProject() {
        openDocumentLauncher.launch(new String[]{"application/json", "application/octet-stream"});
    }

    private void exportWorksheetPdf() {
        pendingDocumentExport = "pdf";
        createDocumentLauncher.launch("Esercizi-calligrafia.pdf");
    }

    private void exportSvgDocument() {
        pendingDocumentExport = "svg";
        createDocumentLauncher.launch("CalligrafiaStudio.svg");
    }

    private void writeExportDocument(Uri uri) {
        if (uri == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IOException("Document provider returned no output stream");
            if ("project".equals(pendingDocumentExport)) {
                output.write(createProjectJson().toString(2).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Progetto esportato.", Toast.LENGTH_SHORT).show();
            } else if ("svg".equals(pendingDocumentExport)) {
                if (drawingCanvas.getWidth() <= 0 || drawingCanvas.getHeight() <= 0) {
                    throw new IOException("The drawing sheet is not laid out yet");
                }
                output.write(drawingCanvas.toSvg().getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "Disegno vettoriale SVG esportato.", Toast.LENGTH_SHORT).show();
            } else if ("pdf".equals(pendingDocumentExport)) {
                android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
                try {
                    List<String> letters = new ArrayList<>(activeModels.keySet());
                    letters.sort(String::compareTo);
                    String styleName = activeStyle == null ? "Calligrafia" : activeStyle.optString("nome", "Calligrafia");
                    if (!drawingCanvas.exportWorksheet(document, styleName, letters, activeModels)) {
                        throw new IOException("No model letters are available for this worksheet");
                    }
                    document.writeTo(output);
                } finally {
                    document.close();
                }
                Toast.makeText(this, "Schede PDF esportate.", Toast.LENGTH_SHORT).show();
            } else {
                throw new IOException("Unknown document export type");
            }
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Document export failed", exception);
            Toast.makeText(this, "Esportazione del documento non riuscita.", Toast.LENGTH_LONG).show();
        } finally {
            pendingDocumentExport = null;
        }
    }

    private void readImportedProject(Uri uri) {
        if (uri == null) return;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Document provider returned no input stream");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
            if (buffer.size() == 0) throw new IOException("Project file is empty");
            applyProjectJson(new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8)));
            Toast.makeText(this, "Progetto importato.", Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Project import failed", exception);
            Toast.makeText(this, "Il file non è un progetto Calligrafia Studio leggibile.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (atHome) finish();
                else currentBackAction.run();
            }
        });
        createDocumentLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("*/*"),
                this::writeExportDocument);
        openDocumentLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                this::readImportedProject);
        getWindow().setStatusBarColor(0xFF5C4636);
        getWindow().setNavigationBarColor(0xFF5C4636);
        if (!loadStyles() || !loadModels() || !loadCalligraphyModels() || !loadCursiveModels()) {
            Toast.makeText(this, "Impossibile caricare il catalogo degli stili.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        showHome();
    }

    private boolean loadStyles() {
        try (InputStream input = getAssets().open("stili.json")) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            JSONArray catalog = new JSONArray(new String(bytes, 0, count, StandardCharsets.UTF_8));
            for (int i = 0; i < catalog.length(); i++) {
                styles.add(catalog.getJSONObject(i));
            }
            return styles.size() == 35;
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Catalog loading failed", exception);
            return false;
        }
    }

    private boolean loadModels() {
        try (InputStream input = getAssets().open("ductus_base.json")) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            JSONObject data = new JSONObject(new String(bytes, 0, count, StandardCharsets.UTF_8));
            JSONArray letters = data.names();
            if (letters == null) return false;
            for (int letterIndex = 0; letterIndex < letters.length(); letterIndex++) {
                String letter = letters.getString(letterIndex);
                JSONArray strokes = data.getJSONArray(letter);
                List<List<float[]>> parsedStrokes = new ArrayList<>();
                for (int i = 0; i < strokes.length(); i++) {
                    JSONArray points = strokes.getJSONArray(i);
                    List<float[]> parsedPoints = new ArrayList<>();
                    for (int j = 0; j < points.length(); j++) {
                        JSONArray point = points.getJSONArray(j);
                        parsedPoints.add(new float[]{(float) point.getDouble(0), (float) point.getDouble(1)});
                    }
                    parsedStrokes.add(parsedPoints);
                }
                baseModels.put(letter, parsedStrokes);
            }
            return !baseModels.isEmpty();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Ductus model loading failed", exception);
            return false;
        }
    }

    private boolean loadCalligraphyModels() {
        try {
            JSONObject byStyle = new JSONObject(readAsset("ductus_by_style.json"));
            JSONArray styleIds = byStyle.names();
            if (styleIds != null) {
                for (int i = 0; i < styleIds.length(); i++) {
                    String styleId = styleIds.getString(i);
                    styleModels.put(styleId, parseModels(byStyle.getJSONObject(styleId)));
                }
            }
            asianModels.putAll(parseModels(new JSONObject(readAsset("ductus_asian.json"))));
            JSONObject scripts = new JSONObject(readAsset("ductus_scripts.json"));
            arabicModels.putAll(parseModels(scripts.getJSONObject("arabo")));
            greekModels.putAll(parseModels(scripts.getJSONObject("greco")));
            return !styleModels.isEmpty() && !asianModels.isEmpty()
                    && !arabicModels.isEmpty() && !greekModels.isEmpty();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Calligraphy model loading failed", exception);
            return false;
        }
    }

    private Map<String, List<List<float[]>>> parseModels(JSONObject source) throws JSONException {
        Map<String, List<List<float[]>>> parsedModels = new HashMap<>();
        JSONArray characters = source.names();
        if (characters == null) return parsedModels;
        for (int characterIndex = 0; characterIndex < characters.length(); characterIndex++) {
            String character = characters.getString(characterIndex);
            JSONArray strokes = source.getJSONArray(character);
            List<List<float[]>> parsedStrokes = new ArrayList<>();
            for (int strokeIndex = 0; strokeIndex < strokes.length(); strokeIndex++) {
                JSONArray points = strokes.getJSONArray(strokeIndex);
                List<float[]> parsedPoints = new ArrayList<>();
                for (int pointIndex = 0; pointIndex < points.length(); pointIndex++) {
                    JSONArray point = points.getJSONArray(pointIndex);
                    parsedPoints.add(new float[]{(float) point.getDouble(0), (float) point.getDouble(1)});
                }
                parsedStrokes.add(parsedPoints);
            }
            parsedModels.put(character, parsedStrokes);
        }
        return parsedModels;
    }

    private boolean loadCursiveModels() {
        try {
            cursiveModels = new JSONObject(readAsset("cursive_models.json"));
            return cursiveModels.has("styles");
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Cursive model loading failed", exception);
            return false;
        }
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(dp(4), dp(5), dp(4), dp(5));
        return view;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private LinearLayout newColumn() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), dp(18));
        layout.setBackgroundColor(PAPER);
        return layout;
    }

    private void beginPage(String title, boolean showBack) {
        beginPage(title, showBack, this::showHome);
    }

    private void beginPage(String title, boolean showBack, Runnable backAction) {
        if (ductusAnimator != null) {
            ductusAnimator.cancel();
            ductusAnimator = null;
        }
        atHome = false;
        currentBackAction = backAction;
        page = newColumn();
        setContentView(page);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        if (showBack) {
            Button back = button("‹", backAction);
            back.setTextSize(26);
            heading.addView(back, new LinearLayout.LayoutParams(dp(54), dp(54)));
        }
        TextView titleView = text(title, 23, 0xFF49382B);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(heading);
    }

    private void showHome() {
        beginPage("Calligrafia Studio", false);
        atHome = true;
        currentBackAction = this::showHome;
        TextView intro = text("Il tuo laboratorio di scrittura", 16, 0xFF756657);
        intro.setPadding(dp(6), dp(2), dp(6), dp(18));
        page.addView(intro);
        addHomeCard("✒  Foglio di scrittura", "Disegna con il dito o con una penna. Regola il pennino, annulla, salva ed esporta.", this::showStudio);
        addHomeCard("✍  Esercitati su una lettera", "Segui il modello, riproduci il ductus e confronta le proporzioni del tuo tratto.", () -> showStudio(styles.get(2), true));
        addHomeCard("📚  Biblioteca storica", "Consulta le 35 schede, le tradizioni, le fonti e gli strumenti.", this::showLibrary);
        addHomeCard("🎓  Percorsi e lezioni", "Segui gli esercizi suggeriti per ciascuno stile, dal livello principiante all'avanzato.", this::showLessons);
        TextView note = text("Le indicazioni storiche sono didattiche: per ricostruire una mano specifica, confronta sempre un esemplare documentato.", 13, 0xFF756657);
        note.setPadding(dp(8), dp(18), dp(8), dp(8));
        page.addView(note);
    }

    private void addHomeCard(String title, String description, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackgroundColor(0xFFEDE3D2);
        TextView cardTitle = text(title, 18, 0xFF49382B);
        cardTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(cardTitle);
        card.addView(text(description, 14, 0xFF625344));
        card.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(7));
        page.addView(card, params);
    }

    private void showLibrary() {
        selectedTradition = "Tutte";
        beginPage("Biblioteca storica", true);
        page.addView(text("35 schede · descrizioni e fonti consultabili anche offline.", 14, 0xFF756657));

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Cerca stile, periodo, area o caratteristiche");
        page.addView(search);

        List<String> traditions = new ArrayList<>();
        traditions.add("Tutte");
        for (JSONObject style : styles) {
            String tradition = style.optString("tradizione");
            if (!traditions.contains(tradition)) traditions.add(tradition);
        }
        Spinner category = new Spinner(this);
        category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, traditions));
        page.addView(category);

        ScrollView scroll = new ScrollView(this);
        libraryResults = new LinearLayout(this);
        libraryResults.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(libraryResults);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Runnable refresh = () -> renderLibraryResults(search.getText().toString());
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTradition = traditions.get(position);
                refresh.run();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        refresh.run();
    }

    private void renderLibraryResults(String query) {
        if (libraryResults == null) return;
        libraryResults.removeAllViews();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        int matches = 0;
        for (JSONObject style : styles) {
            if (!selectedTradition.equals("Tutte") && !selectedTradition.equals(style.optString("tradizione"))) continue;
            String searchable = style.toString().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty() && !searchable.contains(normalized)) continue;
            matches++;
            String name = style.optString("nome");
            String meta = style.optString("tradizione") + " · " + style.optString("periodo");
            Button item = button(name + "\n" + meta, () -> showStyleDetails(style));
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setTextSize(15);
            libraryResults.addView(item, new LinearLayout.LayoutParams(-1, -2));
        }
        if (matches == 0) libraryResults.addView(text("Nessuna scheda trovata.", 15, 0xFF756657));
    }

    private void showStyleDetails(JSONObject style) {
        beginPage(style.optString("nome"), true, this::showLibrary);
        addDetail("Tradizione", style.optString("tradizione"));
        addDetail("Periodo", style.optString("periodo"));
        addDetail("Area", style.optString("area"));
        addDetail("Contesto storico", style.optString("descrizione"));
        addDetail("Caratteristiche", joinArray(style.optJSONArray("caratteristiche")));
        addDetail("Strumenti", style.optString("strumenti"));

        JSONObject guide = style.optJSONObject("guida_tecnica");
        if (guide != null) {
            addDetail("Ductus e ordine dei tratti", guide.optString("ductus"));
            addDetail("Calamo, pennino o pennello", guide.optString("strumento"));
            addDetail("Angolo e pressione", guide.optString("angolo_pressione"));
            addDetail("Proporzioni e griglia", guide.optString("proporzioni"));
            addDetail("Esercizio consigliato", guide.optString("esercizio"));
        }
        addDetail("Font digitali suggeriti", joinArray(style.optJSONArray("font_digitali")));
        addDetail("Nota sui font", style.optString("nota_font"));
        JSONArray sources = style.optJSONArray("fonti");
        if (sources != null) {
            StringBuilder links = new StringBuilder();
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.optJSONObject(i);
                if (source != null) {
                    if (links.length() > 0) links.append("\n");
                    links.append("• ").append(source.optString("titolo")).append("\n  ").append(source.optString("url"));
                }
            }
            addDetail("Fonti e approfondimenti", links.toString());
        }
        Button lessonButton = button("Apri il percorso didattico", () -> showLessons(style, () -> showStyleDetails(style)));
        page.addView(lessonButton);
        Button studioButton = button("Apri il foglio con questo stile", () -> showStudio(style));
        page.addView(studioButton);
        TextView disclaimer = text("Le indicazioni sono introduttive e non sostituiscono un modello verificato della specifica scuola, epoca e variante.", 13, 0xFF756657);
        page.addView(disclaimer);
    }

    private void addDetail(String title, String content) {
        if (content == null || content.trim().isEmpty()) return;
        TextView heading = text(title, 16, 0xFF49382B);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(heading);
        TextView body = text(content, 14, 0xFF413A32);
        page.addView(body);
    }

    private String joinArray(JSONArray array) {
        if (array == null || array.length() == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (result.length() > 0) result.append("\n• ");
            else result.append("• ");
            result.append(array.optString(i));
        }
        return result.toString();
    }

    private void showLessons() {
        showLessons(styles.isEmpty() ? null : styles.get(0), this::showHome);
    }

    private void showLessons(JSONObject initialStyle) {
        showLessons(initialStyle, this::showHome);
    }

    private void showLessons(JSONObject initialStyle, Runnable backAction) {
        beginPage("Percorsi e lezioni", true, backAction);
        page.addView(text("Seleziona uno stile e un livello. La progressione segue il materiale didattico incluso nell’app desktop.", 14, 0xFF756657));
        List<String> names = new ArrayList<>();
        int selectedPosition = 0;
        for (int i = 0; i < styles.size(); i++) {
            names.add(styles.get(i).optString("nome"));
            if (initialStyle != null && styles.get(i).optString("id").equals(initialStyle.optString("id"))) selectedPosition = i;
        }
        lessonStyleSpinner = new Spinner(this);
        lessonStyleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        lessonStyleSpinner.setSelection(selectedPosition);
        page.addView(lessonStyleSpinner);
        lessonLevelSpinner = new Spinner(this);
        lessonLevelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Principiante", "Intermedio", "Avanzato"}));
        page.addView(lessonLevelSpinner);
        ScrollView scroll = new ScrollView(this);
        lessonContent = new LinearLayout(this);
        lessonContent.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(lessonContent);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { renderLesson(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        };
        lessonStyleSpinner.setOnItemSelectedListener(listener);
        lessonLevelSpinner.setOnItemSelectedListener(listener);
        renderLesson();
    }

    private void renderLesson() {
        if (lessonContent == null || lessonStyleSpinner == null || lessonLevelSpinner == null) return;
        lessonContent.removeAllViews();
        JSONObject style = styles.get(lessonStyleSpinner.getSelectedItemPosition());
        String level = lessonLevelSpinner.getSelectedItem().toString();
        JSONObject guide = style.optJSONObject("guida_tecnica");
        if (guide != null) {
            addLessonDetail("Tecnica", guide.optString("ductus"));
            addLessonDetail("Strumento", guide.optString("strumento"));
            addLessonDetail("Angolo e pressione", guide.optString("angolo_pressione"));
        }
        JSONArray exercises = style.optJSONArray("passi_esercizio");
        int from = level.equals("Principiante") ? 0 : 1;
        int to = exercises == null ? 0 : (level.equals("Principiante") ? Math.min(3, exercises.length()) : exercises.length());
        addLessonDetail("Percorso · " + level, "");
        for (int i = from; i < to; i++) {
            addLessonDetail((i + 1) + ".", exercises.optString(i));
        }
        if (level.equals("Principiante")) {
            addLessonDetail("Compito", "Completa una riga di esercizi lenti; ripeti ogni forma tre volte e annota la più regolare.");
            addLessonDetail("Autovalutazione", "Orientamento dello strumento · allineamento · regolarità e controllo del gesto.");
        } else if (level.equals("Intermedio")) {
            addLessonDetail("Compito", "Scrivi una breve sequenza di lettere o caratteri modello; ripetila variando una sola impostazione per volta.");
            addLessonDetail("Autovalutazione", "Proporzioni e inclinazione · spazi interni · continuità tra i tratti.");
        } else {
            addLessonDetail("Compito", "Realizza una breve composizione, confrontala con una fonte storica identificata e descrivi le differenze.");
            addLessonDetail("Autovalutazione", "Fedeltà al modello · ritmo e contrasto · coerenza delle legature o connessioni.");
        }
        addLessonDetail("Nota", "Le sequenze sono proposte didattiche: verifica il ductus puntuale su un modello della specifica scuola e variante.");
    }

    private void addLessonDetail(String title, String content) {
        TextView heading = text(title, 15, 0xFF49382B);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        lessonContent.addView(heading);
        if (!content.isEmpty()) lessonContent.addView(text(content, 14, 0xFF413A32));
    }

    private void showStudio() {
        showStudio(styles.isEmpty() ? null : styles.get(0));
    }

    private void showStudio(JSONObject style) {
        showStudio(style, false);
    }

    private void showStudio(JSONObject style, boolean practiceMode) {
        activeStyle = style;
        selectedModelFamily = "Automatico";
        selectedFontChoice = FontCatalog.STYLE_DEFAULT;
        beginPage("Foglio di scrittura", true);
        ViewGroup parent = (ViewGroup) page.getParent();
        if (parent != null) parent.removeView(page);
        ScrollView studioScroll = new ScrollView(this);
        studioScroll.setFillViewport(true);
        studioScroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(studioScroll);
        LinearLayout styleRow = new LinearLayout(this);
        styleRow.setGravity(Gravity.CENTER_VERTICAL);
        styleRow.addView(text("Stile", 15, 0xFF49382B));
        studioStyleSpinner = new Spinner(this);
        List<String> names = new ArrayList<>();
        int selection = 0;
        for (int i = 0; i < styles.size(); i++) {
            names.add(styles.get(i).optString("nome"));
            if (style != null && styles.get(i).optString("id").equals(style.optString("id"))) selection = i;
        }
        studioStyleSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        studioStyleSpinner.setSelection(selection);
        styleRow.addView(studioStyleSpinner, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(styleRow);

        drawingCanvas = new DrawingCanvas(this);
        drawingCanvas.setBackgroundColor(PAPER);
        drawingCanvas.setPenSettings(9, 30, 1f);
        modelVisible = practiceMode;
        drawingCanvas.setModelVisible(modelVisible);
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(-1, dp(300));
        canvasParams.setMargins(0, dp(5), 0, dp(7));
        page.addView(drawingCanvas, canvasParams);

        LinearLayout appearanceRow = new LinearLayout(this);
        appearanceRow.setGravity(Gravity.CENTER_VERTICAL);
        fontSpinner = new Spinner(this);
        fontSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, FontCatalog.CHOICES));
        appearanceRow.addView(fontSpinner, new LinearLayout.LayoutParams(0, -2, 1));
        paperSpinner = new Spinner(this);
        String[] paperNames = {"Pergamena", "Bianca", "Notte", "Bosco"};
        paperSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, paperNames));
        appearanceRow.addView(paperSpinner, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(appearanceRow);
        fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedFontChoice = FontCatalog.CHOICES[position];
                applyFontChoice();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        paperSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private final int[] colors = {PAPER, 0xFFFFFFFF, 0xFF202124, 0xFF344334};
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                drawingCanvas.setPaperColor(colors[position]);
                if (position == 2) drawingCanvas.setInkColor(0xFFF2E8D8);
                else if (position == 3) drawingCanvas.setInkColor(0xFFE9E6D8);
                else drawingCanvas.setInkColor(INK);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        addSlider("Larghezza pennino", 1, 30, 9, value -> drawingCanvas.setPenWidth(value));
        addSlider("Angolo del pennino", 0, 90, 30, value -> drawingCanvas.setNibAngle(value));
        addSlider("Pressione simulata", 25, 150, 100, value -> drawingCanvas.setPressureScale(value / 100f));

        LinearLayout modelRow = new LinearLayout(this);
        modelRow.setGravity(Gravity.CENTER_VERTICAL);
        modelFamilySpinner = new Spinner(this);
        String[] families = {"Automatico", "Latino", "Greco", "Arabo", "Cinese", "Giapponese"};
        modelFamilySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, families));
        modelRow.addView(modelFamilySpinner, new LinearLayout.LayoutParams(0, -2, 1));
        modelLetterSpinner = new Spinner(this);
        modelRow.addView(text("Lettera", 13, 0xFF49382B));
        modelRow.addView(modelLetterSpinner, new LinearLayout.LayoutParams(dp(80), -2));
        modelRow.addView(button("Modello", () -> {
            modelVisible = !modelVisible;
            drawingCanvas.setModelVisible(modelVisible);
        }), new LinearLayout.LayoutParams(0, -2, 1));
        modelRow.addView(button("Anima", this::animateDuctus), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(modelRow);
        modelFamilySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedModelFamily = families[position];
                rebuildModelLetters(null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        modelLetterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= modelLetterSpinner.getCount()) return;
                String letter = modelLetterSpinner.getItemAtPosition(position).toString();
                drawingCanvas.setReferenceModel(letter, activeModels.get(letter), isRightToLeftFamily());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        page.addView(text("Schemi didattici orientativi; i caratteri arabi sono isolati e non mostrano le legature contestuali.",
                11, 0xFF756657));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.addView(button("Annulla", () -> drawingCanvas.undo()), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Guide", () -> drawingCanvas.toggleGuides()), new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(button("Inchiostro", this::chooseInk), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(actions);
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER);
        tools.addView(button("Inserisci testo", this::placeText), new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(button("Valuta gesto", () -> showMessage("Riscontro dell'esercizio", drawingCanvas.evaluateAgainstModel())),
                new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(button("Regolarità", () -> showMessage("Analisi della regolarità", drawingCanvas.analyzeRegularity())),
                new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(tools);
        page.addView(button("Scrivi una parola in corsivo legato", this::showCursiveWriter));
        page.addView(button("Riconosci lo stile del disegno", this::chooseRecognitionFamily));
        LinearLayout fileActions = new LinearLayout(this);
        fileActions.setGravity(Gravity.CENTER);
        fileActions.addView(button("Pulisci", () -> confirmClear()), new LinearLayout.LayoutParams(0, -2, 1));
        fileActions.addView(button("Salva", this::saveProject), new LinearLayout.LayoutParams(0, -2, 1));
        fileActions.addView(button("Apri", this::loadProject), new LinearLayout.LayoutParams(0, -2, 1));
        fileActions.addView(button("Esporta PNG", this::exportPng), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(fileActions);
        LinearLayout exports = new LinearLayout(this);
        exports.setGravity(Gravity.CENTER);
        exports.addView(button("PDF esercizi", this::exportWorksheetPdf), new LinearLayout.LayoutParams(0, -2, 1));
        exports.addView(button("SVG vettoriale", this::exportSvgDocument), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(exports);
        LinearLayout projectActions = new LinearLayout(this);
        projectActions.setGravity(Gravity.CENTER);
        projectActions.addView(button("Esporta progetto", this::exportProject), new LinearLayout.LayoutParams(0, -2, 1));
        projectActions.addView(button("Importa progetto", this::importProject), new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(projectActions);

        studioStyleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                activeStyle = styles.get(position);
                if (FontCatalog.STYLE_DEFAULT.equals(selectedFontChoice)) applyFontChoice();
                if ("Automatico".equals(selectedModelFamily)) rebuildModelLetters(null);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        activeStyle = styles.get(selection);
        applyFontChoice();
        rebuildModelLetters("a");
    }

    private void applyFontChoice() {
        if (drawingCanvas == null || activeStyle == null) return;
        String styleId = activeStyle.optString("id");
        String fontAsset = FontCatalog.fileForChoice(selectedFontChoice, styleId);
        Typeface typeface = FontCatalog.STYLE_DEFAULT.equals(selectedFontChoice)
                ? FontCatalog.forStyle(this, styleId)
                : FontCatalog.forChoice(this, selectedFontChoice, styleId);
        drawingCanvas.setTypeface(typeface, fontAsset);
    }

    private Map<String, List<List<float[]>>> modelsForSelection() {
        if ("Latino".equals(selectedModelFamily)) return baseModels;
        if ("Greco".equals(selectedModelFamily)) return greekModels;
        if ("Arabo".equals(selectedModelFamily)) return arabicModels;
        if ("Cinese".equals(selectedModelFamily)) return filterAsianModels(false);
        if ("Giapponese".equals(selectedModelFamily)) return filterAsianModels(true);
        String styleId = activeStyle == null ? "" : activeStyle.optString("id");
        if (styleId.startsWith("araba_") || styleId.startsWith("persiana_")) return arabicModels;
        if (styleId.startsWith("cinese_")) return filterAsianModels(false);
        if (styleId.startsWith("giapponese_")) return filterAsianModels(true);
        Map<String, List<List<float[]>>> models = styleModels.get(styleId);
        return models == null || models.isEmpty() ? baseModels : models;
    }

    private Map<String, List<List<float[]>>> filterAsianModels(boolean includeKana) {
        Map<String, List<List<float[]>>> filtered = new HashMap<>();
        for (Map.Entry<String, List<List<float[]>>> entry : asianModels.entrySet()) {
            int codePoint = entry.getKey().codePointAt(0);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN || includeKana
                    && (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private void rebuildModelLetters(String preferredLetter) {
        if (modelLetterSpinner == null || drawingCanvas == null) return;
        String previous = preferredLetter;
        if (previous == null && modelLetterSpinner.getSelectedItem() != null) {
            previous = modelLetterSpinner.getSelectedItem().toString();
        }
        activeModels = modelsForSelection();
        List<String> letters = new ArrayList<>(activeModels.keySet());
        letters.sort((left, right) -> left.compareToIgnoreCase(right));
        modelLetterSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, letters));
        int selection = letters.indexOf(previous);
        if (selection < 0 && !letters.isEmpty()) selection = 0;
        if (selection >= 0) modelLetterSpinner.setSelection(selection);
        if (letters.isEmpty()) {
            drawingCanvas.setReferenceModel("", null, isRightToLeftFamily());
            Toast.makeText(this, "Modelli non disponibili per questo alfabeto.", Toast.LENGTH_SHORT).show();
        } else {
            drawingCanvas.setReferenceModel(letters.get(selection), activeModels.get(letters.get(selection)), isRightToLeftFamily());
        }
    }

    private boolean isRightToLeftFamily() {
        if ("Arabo".equals(selectedModelFamily)) return true;
        if ("Automatico".equals(selectedModelFamily) && activeStyle != null) {
            String id = activeStyle.optString("id");
            return id.startsWith("araba_") || id.startsWith("persiana_");
        }
        return false;
    }

    private interface SliderChange { void onChanged(int value); }

    private void addSlider(String label, int min, int max, int initial, SliderChange callback) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(label, 13, 0xFF49382B);
        row.addView(title, new LinearLayout.LayoutParams(dp(132), -2));
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(initial - min);
        row.addView(seekBar, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(String.valueOf(initial), 13, 0xFF756657);
        row.addView(value);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int current = progress + min;
                value.setText(String.valueOf(current));
                callback.onChanged(current);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        page.addView(row);
    }

    private void animateDuctus() {
        if (modelLetterSpinner == null || baseModels.get(modelLetterSpinner.getSelectedItem().toString()) == null) {
            Toast.makeText(this, "Schema vettoriale non disponibile per questa lettera.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ductusAnimator != null) ductusAnimator.cancel();
        modelVisible = true;
        drawingCanvas.setModelVisible(true);
        drawingCanvas.setAnimationProgress(0f);
        ductusAnimator = ValueAnimator.ofFloat(0f, 1f);
        ductusAnimator.setDuration(2400);
        ductusAnimator.addUpdateListener(animation ->
                drawingCanvas.setAnimationProgress((float) animation.getAnimatedValue()));
        ductusAnimator.start();
    }

    private void placeText() {
        EditText input = new EditText(this);
        input.setHint("Scrivi una parola o una frase");
        input.setTypeface(drawingCanvas == null ? Typeface.DEFAULT
                : FontCatalog.forAsset(this, drawingCanvas.getActiveFontAsset(),
                activeStyle == null ? "" : activeStyle.optString("id")));
        new AlertDialog.Builder(this).setTitle("Inserisci testo")
                .setView(input)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Posiziona", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "Inserisci del testo prima di posizionarlo.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    drawingCanvas.setPendingText(value, drawingCanvas.getInkColor(), dp(30));
                    Toast.makeText(this, "Tocca il foglio per posizionare il testo.", Toast.LENGTH_LONG).show();
                }).show();
    }

    private void showCursiveWriter() {
        EditText input = new EditText(this);
        input.setText(R.string.cursive_sample_word);
        input.setHint("Parola o frase in corsivo");
        Spinner style = new Spinner(this);
        String[] styleIds = {"occidentale_copperplate", "occidentale_spencerian",
                "occidentale_cancelleresca", "moderna_brush_lettering"};
        String[] styleNames = {"Copperplate / Roundhand", "Spencerian", "Cancelleresca / Italica", "Brush lettering"};
        style.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, styleNames));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), 0, dp(20), 0);
        content.addView(input);
        content.addView(style);
        content.addView(text("Schemi didattici simulati: confrontali con un manuale; non sono facsimili storici.", 13, 0xFF756657));
        new AlertDialog.Builder(this).setTitle("Parola corsiva · legature")
                .setView(content)
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Genera sul foglio", (dialog, which) -> {
                    try {
                        CursiveWriter.Generated word = CursiveWriter.generate(
                                input.getText().toString(), styleIds[style.getSelectedItemPosition()], cursiveModels);
                        if (word.strokes.isEmpty()) {
                            Toast.makeText(this, "Inserisci lettere latine supportate.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        drawingCanvas.insertCursiveWord(word);
                        if (!word.ignoredCharacters.isEmpty()) {
                            Toast.makeText(this, "Caratteri ignorati: " + word.ignoredCharacters, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Parola corsiva inserita. Annulla rimuove l'intero gruppo.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException exception) {
                        Log.e(TAG, "Cursive word generation failed", exception);
                        Toast.makeText(this, "Non è stato possibile generare la parola.", Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Chiudi", null).show();
    }

    private void chooseRecognitionFamily() {
        String[] families = {"Automatico", "Latino", "Arabo / persiano", "Asia orientale"};
        new AlertDialog.Builder(this).setTitle("Alfabeto del disegno")
                .setItems(families, (dialog, selection) -> {
                    String forced = selection == 1 ? "latino" : selection == 2 ? "arabo" : selection == 3 ? "cjk" : null;
                    recognizeStyle(forced);
                }).show();
    }

    private void recognizeStyle(String family) {
        try (InputStream input = getAssets().open("profili_stile.json")) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            JSONObject profiles = new JSONObject(new String(bytes, 0, count, StandardCharsets.UTF_8));
            StyleRecognizer.Result result = StyleRecognizer.recognize(
                    drawingCanvas.getStrokesForAnalysis(), profiles,
                    new JSONArray(readAsset("stili.json")), family);
            AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                    .setTitle("Stima euristica dello stile")
                    .setMessage(result.summary())
                    .setNegativeButton("Chiudi", null);
            if (!result.matches.isEmpty()) {
                dialog.setPositiveButton("Applica il primo suggerimento", (ignored, which) -> {
                    String suggestedId = result.matches.get(0).id;
                    for (int i = 0; i < styles.size(); i++) {
                        if (styles.get(i).optString("id").equals(suggestedId)) {
                            studioStyleSpinner.setSelection(i);
                            break;
                        }
                    }
                });
            }
            dialog.show();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Style recognition failed", exception);
            Toast.makeText(this, "Non è stato possibile analizzare il disegno.", Toast.LENGTH_LONG).show();
        }
    }

    private String readAsset(String name) throws IOException {
        try (InputStream input = getAssets().open(name)) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            return new String(bytes, 0, count, StandardCharsets.UTF_8);
        }
    }

    private void chooseInk() {
        String[] names = {"Nero seppia", "Nero", "Blu", "Bordeaux"};
        int[] colors = {0xFF38271E, 0xFF171717, 0xFF203A68, 0xFF702F38};
        new AlertDialog.Builder(this).setTitle("Scegli inchiostro")
                .setItems(names, (dialog, which) -> drawingCanvas.setInkColor(colors[which])).show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this).setMessage("Vuoi cancellare tutti i tratti?")
                .setNegativeButton("Annulla", null)
                .setPositiveButton("Pulisci", (dialog, which) -> drawingCanvas.clearAll()).show();
    }

    private void saveProject() {
        try {
            JSONObject project = createProjectJson();
            try (FileOutputStream output = openFileOutput("progetto_calligrafia.json", MODE_PRIVATE)) {
                output.write(project.toString().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Progetto salvato sul dispositivo.", Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Project save failed", exception);
            Toast.makeText(this, "Non è stato possibile salvare il progetto.", Toast.LENGTH_LONG).show();
        }
    }

    private void loadProject() {
        File file = new File(getFilesDir(), "progetto_calligrafia.json");
        if (!file.exists()) {
            Toast.makeText(this, "Non c'è ancora un progetto salvato.", Toast.LENGTH_SHORT).show();
            return;
        }
        try (InputStream input = openFileInput("progetto_calligrafia.json")) {
            byte[] bytes = new byte[input.available()];
            int count = input.read(bytes);
            applyProjectJson(new JSONObject(new String(bytes, 0, count, StandardCharsets.UTF_8)));
            Toast.makeText(this, "Progetto caricato.", Toast.LENGTH_SHORT).show();
        } catch (IOException | JSONException exception) {
            Log.e(TAG, "Project load failed", exception);
            Toast.makeText(this, "Il progetto salvato non è leggibile.", Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject createProjectJson() throws JSONException {
        JSONObject project = new JSONObject();
        project.put("styleId", activeStyle == null ? "" : activeStyle.optString("id"));
        project.put("strokes", drawingCanvas.toJson());
        project.put("textItems", drawingCanvas.textToJson());
        project.put("modelLetter", modelLetterSpinner == null || modelLetterSpinner.getSelectedItem() == null
                ? "a" : modelLetterSpinner.getSelectedItem().toString());
        project.put("modelFamily", selectedModelFamily);
        project.put("modelVisible", modelVisible);
        project.put("fontChoice", selectedFontChoice);
        project.put("fontAsset", drawingCanvas.getActiveFontAsset());
        project.put("paperColor", drawingCanvas.getPaperColor());
        project.put("inkColor", drawingCanvas.getInkColor());
        return project;
    }

    private void applyProjectJson(JSONObject project) throws JSONException {
        String styleId = project.optString("styleId");
        for (int i = 0; i < styles.size(); i++) {
            if (styles.get(i).optString("id").equals(styleId)) {
                activeStyle = styles.get(i);
                if (studioStyleSpinner != null) studioStyleSpinner.setSelection(i);
                break;
            }
        }
        selectedFontChoice = project.optString("fontChoice", FontCatalog.STYLE_DEFAULT);
        if (fontSpinner != null) {
            int fontPosition = 0;
            for (int i = 0; i < FontCatalog.CHOICES.length; i++) {
                if (FontCatalog.CHOICES[i].equals(selectedFontChoice)) fontPosition = i;
            }
            fontSpinner.setSelection(fontPosition);
        }
        applyFontChoice();
        drawingCanvas.loadJson(project.optJSONArray("strokes"));
        drawingCanvas.loadTextJson(project.optJSONArray("textItems"));
        selectedModelFamily = project.optString("modelFamily", "Automatico");
        if (modelFamilySpinner != null) {
            for (int i = 0; i < modelFamilySpinner.getCount(); i++) {
                if (modelFamilySpinner.getItemAtPosition(i).toString().equals(selectedModelFamily)) {
                    modelFamilySpinner.setSelection(i);
                    break;
                }
            }
        }
        rebuildModelLetters(project.optString("modelLetter", "a"));
        modelVisible = project.optBoolean("modelVisible", false);
        drawingCanvas.setModelVisible(modelVisible);
        drawingCanvas.setPaperColor(project.optInt("paperColor", PAPER));
        if (paperSpinner != null) {
            int color = project.optInt("paperColor", PAPER);
            if (color == 0xFFFFFFFF) paperSpinner.setSelection(1);
            else if (color == 0xFF202124) paperSpinner.setSelection(2);
            else if (color == 0xFF344334) paperSpinner.setSelection(3);
            else paperSpinner.setSelection(0);
        }
        drawingCanvas.setInkColor(project.optInt("inkColor", INK));
    }

    private void exportPng() {
        if (drawingCanvas.getWidth() <= 0 || drawingCanvas.getHeight() <= 0) {
            Toast.makeText(this, "Il foglio non è ancora pronto.", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = Bitmap.createBitmap(drawingCanvas.getWidth(), drawingCanvas.getHeight(), Bitmap.Config.ARGB_8888);
        drawingCanvas.draw(new android.graphics.Canvas(bitmap));
        String fileName = "Calligrafia-" + System.currentTimeMillis() + ".png";
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CalligrafiaStudio");
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore did not create an image");
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw new IOException("PNG encoding failed");
                }
            }

            Toast.makeText(this, "PNG esportato in Immagini/CalligrafiaStudio.", Toast.LENGTH_LONG).show();
        } catch (IOException exception) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Log.e(TAG, "PNG export failed", exception);
            Toast.makeText(this, "Esportazione PNG non riuscita.", Toast.LENGTH_LONG).show();
        } finally {
            bitmap.recycle();
        }
    }

}
