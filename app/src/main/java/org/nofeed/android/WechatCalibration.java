package org.nofeed.android;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** User-selected points, kept locally and invalidated when the layout changes. */
final class WechatCalibration {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("wechat_positions", Context.MODE_PRIVATE); }
    static String layout(Context c) {
        c = c.getApplicationContext();
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        String version;
        try { version = c.getPackageManager().getPackageInfo("com.tencent.mm", 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException e) { version = "missing"; }
        String ime = android.provider.Settings.Secure.getString(c.getContentResolver(), android.provider.Settings.Secure.DEFAULT_INPUT_METHOD);
        return m.widthPixels + ":" + m.heightPixels + ":" + m.densityDpi + ":" + c.getResources().getConfiguration().fontScale + ":" + version + ":" + ime;
    }
    static Points load(Context c) {
        SharedPreferences p = prefs(c);
        if (!layout(c).equals(p.getString("layout", ""))) return null;
        Points points = new Points();
        for (int i = 0; i < 8; i++) {
            if (!p.contains("point_" + i)) return null;
            points.xy[i] = p.getFloat("point_" + i, -1);
            if (points.xy[i] < 0) return null;
        }
        return points;
    }
    static String status(Context c) {
        return load(c) != null ? "已保存" : prefs(c).contains("layout") ? "需重新校准" : "未校准";
    }
    static void clear(Context c) { prefs(c).edit().clear().apply(); }
    static Points screenshotProfile(Context c) {
        c = c.getApplicationContext();
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        // Only the supplied portrait WeChat 8.0.78 screenshots establish these
        // three locations. The paste popup is deliberately left for calibration.
        try {
            if (!"8.0.78".equals(c.getPackageManager().getPackageInfo("com.tencent.mm", 0).versionName)) return null;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) { return null; }
        if (Math.abs((float)m.widthPixels / m.heightPixels - 1084f / 2412f) > .018f) return null;
        Points p = new Points();
        p.xy[0] = m.widthPixels * .830f; p.xy[1] = m.heightPixels * .073f;
        p.xy[2] = m.widthPixels * .420f; p.xy[3] = m.heightPixels * .084f;
        p.xy[6] = m.widthPixels * .892f; p.xy[7] = m.heightPixels * .934f;
        return p;
    }
    static final class Points {
        final float[] xy = new float[8];
        void save(Context c) {
            SharedPreferences.Editor edit = prefs(c).edit().putString("layout", layout(c));
            for (int i = 0; i < 8; i++) edit.putFloat("point_" + i, xy[i]);
            edit.apply();
        }
    }
    interface Pick { void at(float x, float y); }
    static final class Overlay {
        final WindowManager manager;
        final FrameLayout view;
        boolean closed;
        Overlay(AccessibilityService service, int step, Pick picked, Runnable cancel) {
            manager = (WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
            view = new FrameLayout(service); view.setBackgroundColor(0x09000000);
            view.setContentDescription("微信位置校准第" + step + "步");
            LinearLayout panel = new LinearLayout(service); panel.setOrientation(LinearLayout.VERTICAL);
            int pad = (int)(16 * service.getResources().getDisplayMetrics().density);
            panel.setPadding(pad, pad, pad, pad); panel.setBackgroundColor(0xFFFFFFFF); panel.setClickable(true);
            TextView prompt = new TextView(service); prompt.setTextColor(0xFF202520); prompt.setTextSize(17);
            prompt.setTypeface(service.getResources().getFont(R.font.noto_serif_sc_regular));
            prompt.setText(step + "/4 · " + (step == 1 ? "点一下微信的搜索图标" : step == 2 ? "点一下搜索输入框" : step == 3 ? "点一下弹出菜单中的‘粘贴’" : "点一下键盘的‘搜索’键") + "\n这次点击会保存并立即执行。");
            panel.addView(prompt);
            Button stop = new Button(service); stop.setTypeface(service.getResources().getFont(R.font.noto_serif_sc_regular)); stop.setText("取消校准"); stop.setOnClickListener(v -> cancel.run()); panel.addView(stop);
            FrameLayout.LayoutParams box = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            box.setMargins(pad, 0, pad, pad * 4); view.addView(panel, box);
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP && !closed) picked.at(event.getRawX(), event.getRawY());
                return true;
            });
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(-1, -1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            manager.addView(view, params);
        }
        void close() {
            if (!closed) {
                closed = true;
                try { manager.removeViewImmediate(view); }
                catch (IllegalArgumentException detached) { /* Already detached by the system. */ }
            }
        }
    }
}
