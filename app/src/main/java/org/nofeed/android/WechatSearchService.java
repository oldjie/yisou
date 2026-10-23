package org.nofeed.android;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

/** Coordinate playback only. Never retrieves a window tree or recognizes controls. */
public class WechatSearchService extends AccessibilityService {
    private static final String PACKAGE = "com.tencent.mm";
    private static WechatSearchService connected;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::step;
    private String keyword, inputMethodPackage, layoutAtStart;
    private long started, nextAt, overlayGrace;
    private boolean seenWechat, busy, fullCalibration, learnPaste, savePoints;
    private int stage, requestId;
    private WechatCalibration.Points points;
    private WechatCalibration.Overlay overlay;

    private boolean learning() { return learnPaste || fullCalibration; }
    private long tapDuration() { return learning() ? 70 : 35; }
    private long settleDelay() {
        if (learning()) return 1100;
        return stage == 1 ? 280 : stage == 3 ? 80 : 100;
    }

    static boolean ready() { return connected != null; }
    static String status(Context c) { return new LocalStore(c).prefs.getString("wechat_last_status", ""); }
    private void status(String value) { new LocalStore(this).prefs.edit().putString("wechat_last_status", value).apply(); }

    static void openPermissionSettings(Activity activity) {
        for (String action : new String[]{Settings.ACTION_ACCESSIBILITY_SETTINGS, Settings.ACTION_SETTINGS}) {
            try { activity.startActivity(new Intent(action)); return; }
            catch (RuntimeException unavailable) { /* Some vendor settings entries are restricted. */ }
        }
        new AlertDialog.Builder(activity).setTitle("开启微信搜索辅助")
                .setMessage("系统设置页面未能打开。请从手机桌面打开设置，进入‘辅助功能 → 无障碍’，找到‘一搜 · 微信搜索辅助’并开启。开启后返回一搜，再点搜索。")
                .setPositiveButton("知道了", null).show();
    }

    static void showOptions(Activity activity, String draft) {
        new AlertDialog.Builder(activity).setTitle("微信搜索辅助")
                .setItems(new String[]{"开启无障碍权限", "校准搜索位置 · " + WechatCalibration.status(activity), "清除已保存的位置"}, (dialog, which) -> {
                    if (which == 0) openPermissionSettings(activity);
                    else if (which == 1) new AlertDialog.Builder(activity).setTitle("首次校准，之后自动")
                            .setMessage("先让微信回到聊天列表首页。接下来依次点选搜索图标、输入框、粘贴菜单、键盘搜索键，保存这四个位置。\n\n会把当前关键词写入系统剪贴板并自动搜索；输入框为空时使用‘测试’。请保持手机竖屏。")
                            .setNegativeButton("取消", null).setPositiveButton("开始校准", (d, w) -> launch(activity, draft.trim().isEmpty() ? "测试" : draft, true)).show();
                    else { WechatCalibration.clear(activity); Toast.makeText(activity, "已清除微信搜索位置", Toast.LENGTH_SHORT).show(); }
                }).setNegativeButton("关闭", null).show();
    }

    static void launch(Activity activity, String query) { launch(activity, query, false); }
    private static void launch(Activity activity, String query, boolean calibrate) {
        Intent launcher = activity.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (launcher == null) {
            new AlertDialog.Builder(activity).setMessage("请先安装微信。").setPositiveButton("知道了", null).show(); return;
        }
        if (!ready()) {
            new AlertDialog.Builder(activity).setTitle("开启微信搜索辅助")
                    .setMessage("需要开启系统无障碍服务‘一搜 · 微信搜索辅助’，仅用来模拟点击和长按，不读取界面控件。华为手机通常位于‘设置 → 辅助功能 → 无障碍’。\n\n搜索词会写入系统剪贴板，再自动粘贴到微信。首次按提示保存点击位置，之后自动搜索。\n\n开启后返回本页，再点一次搜索。")
                    .setNegativeButton("取消", null).setPositiveButton("去开启", (d, w) -> openPermissionSettings(activity)).show(); return;
        }
        try {
            ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("一搜搜索词", query.trim()));
        } catch (RuntimeException e) {
            new AlertDialog.Builder(activity).setMessage("系统未允许写入搜索词到剪贴板，请检查系统的剪贴板权限。").setPositiveButton("知道了", null).show(); return;
        }
        connected.begin(query.trim(), calibrate);
        launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { activity.startActivity(launcher); }
        catch (RuntimeException e) { connected.finish(false, "无法打开微信"); }
    }

    private void begin(String query, boolean calibrate) {
        finish(false, null);
        if (query.isEmpty()) return;
        points = WechatCalibration.load(this);
        boolean saved = points != null;
        if (!saved) points = WechatCalibration.screenshotProfile(this);
        fullCalibration = calibrate || points == null || (!saved && WechatCalibration.prefs(this).contains("layout"));
        if (fullCalibration) points = new WechatCalibration.Points();
        layoutAtStart = WechatCalibration.layout(this);
        learnPaste = !saved || fullCalibration; savePoints = learnPaste;
        keyword = query; started = SystemClock.uptimeMillis(); nextAt = started + (learning() ? 2800 : 300);
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        inputMethodPackage = ime == null ? "" : ime.split("/", 2)[0];
        stage = 0; seenWechat = false; busy = false; overlayGrace = 0;
        status(fullCalibration ? "等待首次位置校准" : "正在打开微信");
        handler.postDelayed(tick, learning() ? 300 : 30);
    }

    @Override protected void onServiceConnected() { super.onServiceConnected(); connected = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (keyword == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventTime() < started) return;
        String owner = String.valueOf(event.getPackageName());
        if (PACKAGE.equals(owner)) {
            if (!seenWechat && !learning()) nextAt = Math.max(nextAt, SystemClock.uptimeMillis() + 120);
            seenWechat = true; return;
        }
        if (owner.equals(inputMethodPackage)) return;
        long now = SystemClock.uptimeMillis();
        if (owner.equals(getPackageName()) && (overlay != null || now < overlayGrace || now - started < 2800)) return;
        if (seenWechat) finish(false, "已离开微信，搜索已停止");
    }
    @Override public void onInterrupt() { finish(false, null); }
    @Override public void onDestroy() { finish(false, null); if (connected == this) connected = null; super.onDestroy(); }

    private void step() {
        handler.removeCallbacks(tick);
        if (keyword == null) return;
        long now = SystemClock.uptimeMillis();
        if (now - started > (learnPaste || fullCalibration ? 120000 : 20000)) { finish(false, "搜索超时，已停止。请在设置里重新校准微信搜索位置。"); return; }
        if (overlay != null || busy || !seenWechat || now < nextAt) { later(); return; }
        if (!layoutAtStart.equals(WechatCalibration.layout(this))) { finish(false, "屏幕布局变化，请重新校准"); return; }
        switch (stage) {
            case 0:
                status("点击微信搜索入口");
                if (fullCalibration) pick(0); else play(0, tapDuration(), () -> stage = 1);
                break;
            case 1:
                status("定位微信搜索输入框");
                if (fullCalibration) pick(1);
                else if (!learning()) focusAndHold();
                else play(1, tapDuration(), () -> stage = 2);
                break;
            case 2:
                status("长按输入框，打开粘贴菜单");
                play(1, Math.max(750, ViewConfiguration.getLongPressTimeout() + 80), () -> stage = 3);
                break;
            case 3:
                status(learnPaste ? "首次请点选粘贴菜单的位置" : "粘贴搜索词");
                if (learnPaste) pick(2); else play(2, tapDuration(), () -> stage = 4);
                break;
            case 4:
                status("点击键盘搜索键");
                if (fullCalibration) pick(3); else play(3, tapDuration(), this::complete);
                break;
            default: finish(false, "搜索已停止");
        }
    }

    private void pick(int point) {
        final int token = requestId;
        overlayGrace = SystemClock.uptimeMillis() + 1500;
        try {
            overlay = new WechatCalibration.Overlay(this, point + 1, (x, y) -> {
                if (token != requestId || keyword == null) return;
                closeOverlay(); points.xy[point * 2] = x; points.xy[point * 2 + 1] = y;
                busy = true;
                // Let the overlay detach from the input dispatcher before
                // replaying the tap into the underlying WeChat window.
                handler.postDelayed(() -> {
                    if (token == requestId && keyword != null)
                        play(point, 70, () -> { if (point == 3) complete(); else stage = point == 0 ? 1 : point == 1 ? 2 : 4; });
                }, 250);
            }, () -> finish(false, "校准已取消，位置未保存"));
        } catch (RuntimeException e) { finish(false, "无法显示校准，请关闭再开启微信搜索辅助后重试"); return; }
        later();
    }
    private void play(int point, long duration, Runnable next) {
        Path path = new Path(); path.moveTo(points.xy[point * 2], points.xy[point * 2 + 1]);
        dispatch(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build(), next);
    }
    private void focusAndHold() {
        Path path = new Path(); path.moveTo(points.xy[2], points.xy[3]);
        // One scheduled gesture avoids an extra callback/polling cycle while
        // preserving the system's actual long-press threshold.
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 35))
                .addStroke(new GestureDescription.StrokeDescription(path, 95, ViewConfiguration.getLongPressTimeout() + 80))
                .build();
        dispatch(gesture, () -> stage = 3);
    }
    private void dispatch(GestureDescription gesture, Runnable next) {
        if (keyword == null) return;
        if (!layoutAtStart.equals(WechatCalibration.layout(this))) { finish(false, "屏幕布局变化，请重新校准"); return; }
        final int token = requestId;
        busy = true;
        boolean accepted;
        try {
            accepted = dispatchGesture(gesture, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription g) {
                    if (keyword == null || token != requestId) return;
                    busy = false; next.run(); nextAt = SystemClock.uptimeMillis() + settleDelay(); later();
                }
                @Override public void onCancelled(GestureDescription g) { if (keyword != null && token == requestId) finish(false, "点击被中断，搜索已停止"); }
            }, handler);
        } catch (RuntimeException e) { accepted = false; }
        if (!accepted) { busy = false; finish(false, "无法模拟点击，请关闭再开启微信搜索辅助后重试"); }
        else later();
    }
    private void complete() {
        if (savePoints) points.save(this);
        finish(true, savePoints ? "搜索位置已保存，下次自动使用" : null);
    }
    private void closeOverlay() {
        overlayGrace = SystemClock.uptimeMillis() + 1500;
        if (overlay != null) { overlay.close(); overlay = null; }
    }
    private void later() { handler.removeCallbacks(tick); if (keyword != null) handler.postDelayed(tick, learning() ? 300 : 30); }
    private void finish(boolean submitted, String message) {
        requestId++; handler.removeCallbacksAndMessages(null); closeOverlay();
        String completed = keyword; keyword = null; inputMethodPackage = null; busy = false;
        if (submitted && completed != null) { new LocalStore(this).record(SearchTarget.WECHAT, completed, false); status("已执行搜索点击"); }
        else if (message != null) status(message);
        if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
