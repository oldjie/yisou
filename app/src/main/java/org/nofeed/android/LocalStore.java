package org.nofeed.android;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

final class LocalStore {
    final SharedPreferences prefs;
    LocalStore(Context context) { prefs = context.getSharedPreferences("local", Context.MODE_PRIVATE); }
    JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    void record(SearchTarget target, String query, boolean web) {
        try {
            JSONArray previous = history(), next = new JSONArray();
            next.put(new JSONObject().put("target", target.name()).put("query", query)
                    .put("time", System.currentTimeMillis()).put("web", web));
            for (int i = 0; i < Math.min(previous.length(), 199); i++) next.put(previous.get(i));
            SharedPreferences.Editor edit = prefs.edit().putLong("count", prefs.getLong("count", 0) + 1);
            if (prefs.getBoolean("save_history", true)) edit.putString("history", next.toString());
            edit.apply();
            SearchWidget.refresh();
        } catch (Exception ignored) { /* A dispatch must not fail because local history is unavailable. */ }
    }
}
