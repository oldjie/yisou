// SPDX-License-Identifier: AGPL-3.0-only
package fixture;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Actual system AppWidgetHost for disposable-emulator tests, not a launcher. */
public class MainActivity extends Activity {
    AppWidgetHost host;
    AppWidgetManager manager;
    int compactHeight = 80;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); host = new AppWidgetHost(this, 4040); manager = AppWidgetManager.getInstance(this); render(getIntent());
    }
    @Override protected void onStart() { super.onStart(); host.startListening(); }
    @Override protected void onStop() { host.stopListening(); super.onStop(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); render(intent); }
    int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    void render(Intent intent) {
        if (intent.hasExtra("compact_height")) compactHeight = intent.getIntExtra("compact_height", 80);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(dp(16), dp(30), dp(16), dp(12)); root.setBackgroundColor(0xFFE3E7DF);
        TextView title = new TextView(this); title.setText("Android 系统小组件测试宿主\n非华为/小米桌面实机"); title.setTextSize(16); root.addView(title);
        add(root, "SearchWidget", compactHeight, "4×1 快捷搜索");
        add(root, "SearchPanelWidget", 156, "4×2 搜索面板");
        setContentView(root);
    }
    void add(LinearLayout root, String cls, int height, String label) {
        TextView text = new TextView(this); text.setText(label); text.setPadding(0, dp(24), 0, dp(10)); root.addView(text);
        android.content.SharedPreferences prefs = getSharedPreferences("host", MODE_PRIVATE);
        int id = prefs.getInt(cls, -1);
        ComponentName provider = new ComponentName("org.nofeed.android", "org.nofeed.android." + cls);
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 300);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height);
        if (id < 0) {
            id = host.allocateAppWidgetId();
            if (!manager.bindAppWidgetIdIfAllowed(id, provider, options)) { text.setText("TEST_BIND_DENIED"); host.deleteAppWidgetId(id); return; }
            prefs.edit().putInt(cls, id).apply();
        } else manager.updateAppWidgetOptions(id, options);
        AppWidgetHostView view = host.createView(this, id, manager.getAppWidgetInfo(id));
        root.addView(view, new LinearLayout.LayoutParams(-1, dp(height)));
    }
}
