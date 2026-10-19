package br.com.experimento.autododge;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private Prefs() {}
    public static float read(Context c, String key, float fallback) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getFloat(key, fallback);
    }
    public static String loadModel(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("learned_model_v2", "");
    }
    public static void saveModel(Context c, String model) {
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
            .putString("learned_model_v2", model).apply();
    }
    public static void save(Context c, String key, float v) {
        c.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putFloat(key, v).apply();
    }
}
