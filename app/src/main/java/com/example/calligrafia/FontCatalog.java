package com.example.calligrafia;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

final class FontCatalog {
    static final String STYLE_DEFAULT = "Stile automatico";
    static final String[] CHOICES = {
            STYLE_DEFAULT, "EB Garamond", "EB Garamond Italic", "Almendra",
            "Unifraktur Maguntia", "Pirata One", "Kaushan Script", "Great Vibes",
            "Noto Naskh Arabic", "Noto Serif SC", "Noto Serif JP"
    };

    private static final String TAG = "CalligrafiaStudio";
    private static final Map<String, String> STYLE_FONTS = new LinkedHashMap<>();
    private static final Map<String, Typeface> TYPEFACES = new LinkedHashMap<>();

    static {
        STYLE_FONTS.put("occidentale_onciale", "Almendra-Regular.ttf");
        STYLE_FONTS.put("occidentale_semionciale", "Almendra-Regular.ttf");
        STYLE_FONTS.put("occidentale_carolina", "EBGaramond.ttf");
        STYLE_FONTS.put("occidentale_umanistica", "EBGaramond.ttf");
        STYLE_FONTS.put("occidentale_textura", "UnifrakturMaguntia-Regular.ttf");
        STYLE_FONTS.put("occidentale_textura_quadrata", "UnifrakturMaguntia-Regular.ttf");
        STYLE_FONTS.put("occidentale_rotunda", "PirataOne-Regular.ttf");
        STYLE_FONTS.put("occidentale_fraktur", "UnifrakturMaguntia-Regular.ttf");
        STYLE_FONTS.put("occidentale_gotica_cursiva", "PirataOne-Regular.ttf");
        STYLE_FONTS.put("occidentale_bastarda", "PirataOne-Regular.ttf");
        STYLE_FONTS.put("occidentale_batarde_francaise", "PirataOne-Regular.ttf");
        STYLE_FONTS.put("occidentale_cancelleresca", "EBGaramond-Italic.ttf");
        STYLE_FONTS.put("occidentale_copperplate", "GreatVibes-Regular.ttf");
        STYLE_FONTS.put("occidentale_spencerian", "GreatVibes-Regular.ttf");
        STYLE_FONTS.put("moderna_brush_lettering", "KaushanScript-Regular.ttf");
        STYLE_FONTS.put("araba_hijazi", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("araba_kufico", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("araba_naskh", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("araba_thuluth", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("araba_maghribi", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("araba_nastaliq", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("persiana_nastaliq", "NotoNaskhArabic.ttf");
        STYLE_FONTS.put("cinese_oracolare", "NotoSerifSC.ttf");
        STYLE_FONTS.put("cinese_zhuanshu", "NotoSerifSC.ttf");
        STYLE_FONTS.put("cinese_lishu", "NotoSerifSC.ttf");
        STYLE_FONTS.put("cinese_kaishu", "NotoSerifSC.ttf");
        STYLE_FONTS.put("cinese_xingshu", "NotoSerifSC.ttf");
        STYLE_FONTS.put("cinese_caoshu", "NotoSerifSC.ttf");
        STYLE_FONTS.put("giapponese_kaisho", "NotoSerifJP.ttf");
        STYLE_FONTS.put("giapponese_gyosho", "NotoSerifJP.ttf");
        STYLE_FONTS.put("giapponese_sosho", "NotoSerifJP.ttf");
        STYLE_FONTS.put("giapponese_kana", "NotoSerifJP.ttf");
    }

    private FontCatalog() {}

    static Typeface forStyle(Context context, String styleId) {
        return load(context, STYLE_FONTS.getOrDefault(styleId, "EBGaramond.ttf"));
    }

    static Typeface forChoice(Context context, String choice, String styleId) {
        String file;
        switch (choice) {
            case "EB Garamond": file = "EBGaramond.ttf"; break;
            case "EB Garamond Italic": file = "EBGaramond-Italic.ttf"; break;
            case "Almendra": file = "Almendra-Regular.ttf"; break;
            case "Unifraktur Maguntia": file = "UnifrakturMaguntia-Regular.ttf"; break;
            case "Pirata One": file = "PirataOne-Regular.ttf"; break;
            case "Kaushan Script": file = "KaushanScript-Regular.ttf"; break;
            case "Great Vibes": file = "GreatVibes-Regular.ttf"; break;
            case "Noto Naskh Arabic": file = "NotoNaskhArabic.ttf"; break;
            case "Noto Serif SC": file = "NotoSerifSC.ttf"; break;
            case "Noto Serif JP": file = "NotoSerifJP.ttf"; break;
            default: return forStyle(context, styleId);
        }
        return load(context, file);
    }

    static Typeface forAsset(Context context, String file, String styleId) {
        return file == null || file.isEmpty() ? forStyle(context, styleId) : load(context, file);
    }

    static String fileForChoice(String choice, String styleId) {
        switch (choice) {
            case "EB Garamond": return "EBGaramond.ttf";
            case "EB Garamond Italic": return "EBGaramond-Italic.ttf";
            case "Almendra": return "Almendra-Regular.ttf";
            case "Unifraktur Maguntia": return "UnifrakturMaguntia-Regular.ttf";
            case "Pirata One": return "PirataOne-Regular.ttf";
            case "Kaushan Script": return "KaushanScript-Regular.ttf";
            case "Great Vibes": return "GreatVibes-Regular.ttf";
            case "Noto Naskh Arabic": return "NotoNaskhArabic.ttf";
            case "Noto Serif SC": return "NotoSerifSC.ttf";
            case "Noto Serif JP": return "NotoSerifJP.ttf";
            default: return STYLE_FONTS.getOrDefault(styleId, "EBGaramond.ttf");
        }
    }

    private static Typeface load(Context context, String asset) {
        Typeface cached = TYPEFACES.get(asset);
        if (cached != null) return cached;
        try {
            Typeface typeface = Typeface.createFromAsset(context.getAssets(), "fonts/" + asset);
            TYPEFACES.put(asset, typeface);
            return typeface;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Bundled font could not be loaded: " + asset, exception);
            return Typeface.DEFAULT;
        }
    }
}
