package org.nofeed.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private final int paper = Color.WHITE, ink = Color.rgb(40,47,41), muted = Color.rgb(126,132,122);
    private LocalStore store;
    private Typeface uiFont;
    private LinearLayout root, content, choices;
    private EditText query;
    private TextView searchButton, count;
    private SearchTarget selected;
    private String screen = "home", draft = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        uiFont = getResources().getFont(R.font.noto_serif_sc_regular);
        store = new LocalStore(this);
        // Removed preferences must not affect future launches after an upgrade.
        android.content.SharedPreferences.Editor cleanup = store.prefs.edit();
        for (String key : store.prefs.getAll().keySet())
            if (key.startsWith("alias_") || key.startsWith("route_")) cleanup.remove(key);
        cleanup.apply();
        SearchWidget.initialize(this);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (state != null) {
            draft = state.getString("draft", "");
            selected = target(state.getString("selected"));
        } else selected = target(store.prefs.getString("default_target", ""));
        consumeIntent(getIntent());
        showHome();
    }

    private SearchTarget target(String name) {
        try { return SearchTarget.valueOf(name); } catch (Exception ignored) { return null; }
    }
    private void consumeIntent(Intent intent) {
        if (intent.hasExtra("target")) selected = target(intent.getStringExtra("target"));
        if (Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) draft = text.substring(0, Math.min(text.length(), 500));
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); consumeIntent(intent); showHome(); }
    @Override protected void onResume() {
        super.onResume();
        if ("settings".equals(screen)) showSettings();
        else if ("home".equals(screen) && choices != null) renderChoices();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("draft", query != null ? query.getText().toString() : draft);
        out.putString("selected", selected == null ? "" : selected.name());
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius));
        if (stroke != 0) shape.setStroke(dp(1), stroke);
        return shape;
    }
    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(uiFont);
        view.setFontFeatureSettings("kern"); view.setIncludeFontPadding(false); return view;
    }
    private TextView action(String value, Runnable click) {
        TextView view = text(value, 15, ink); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(48));
        view.setPadding(dp(12), dp(8), dp(12), dp(8)); view.setBackground(background(Color.TRANSPARENT, 14, 0));
        view.setOnClickListener(v -> click.run()); view.setFocusable(true); return view;
    }
    private void space(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, new LinearLayout.LayoutParams(1, dp(height))); }
    private void line(LinearLayout parent) { View v = new View(this); v.setBackgroundColor(0xFFE0E2D9); parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1))); }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void base(String page) {
        screen = page;
        root = column(); root.setBackgroundColor(paper); root.setPadding(dp(26), 0, dp(26), 0);
        // API 35 draws edge-to-edge. Insets keep every control outside system bars and keyboard.
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(dp(26) + insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    dp(26) + insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
    }
    private void captureDraft() { if (query != null) draft = query.getText().toString(); }

    private void showHome() {
        base("home");
        // A quiet header; the entire primary flow is one input and one app row.
        LinearLayout header = row();
        TextView logo = text("一搜", 17, ink); logo.setLetterSpacing(.02f); logo.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(logo, new LinearLayout.LayoutParams(0, dp(64), 1));
        TextView history = action("历史", () -> { captureDraft(); showHistory(); });
        history.setTextSize(12); history.setTextColor(muted); history.setContentDescription("搜索历史"); header.addView(history);
        TextView settings = action("设置", () -> { captureDraft(); showSettings(); });
        settings.setTextSize(12); settings.setTextColor(muted); settings.setContentDescription("设置"); header.addView(settings);
        root.addView(header);

        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = column(); content.setGravity(Gravity.CENTER_VERTICAL); scroll.addView(content);
        LinearLayout field = row(); field.setBackground(background(0xFFF5F6F4, 14, 0));
        query = new EditText(this); query.setSingleLine(true); query.setTextSize(17); query.setTextColor(ink); query.setHintTextColor(0xFF999E98);
        query.setHint("搜索…"); query.setBackgroundColor(Color.TRANSPARENT); query.setPadding(dp(16), 0, dp(4), 0);
        query.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); query.setIncludeFontPadding(false);
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setTypeface(uiFont);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH); query.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        query.setText(draft); query.setContentDescription("搜索关键词");
        field.addView(query, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView clear = action("×", () -> query.setText("")); clear.setTextSize(20); clear.setTextColor(muted); clear.setContentDescription("清空关键词");
        clear.setVisibility(draft.isEmpty() ? View.GONE : View.VISIBLE);
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int length, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int length) { clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE); }
            public void afterTextChanged(android.text.Editable s) { }
        });
        field.addView(clear, new LinearLayout.LayoutParams(dp(40), dp(56)));
        searchButton = action("→", this::submit); searchButton.setTextSize(25); searchButton.setTextColor(ink);
        searchButton.setContentDescription("搜索"); field.addView(searchButton, new LinearLayout.LayoutParams(dp(52), dp(56)));
        content.addView(field);
        query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP)) { submit(); return true; }
            return false;
        });
        space(content, 18);
        choices = row(); content.addView(choices); renderChoices(); updateButton();
        space(content, 80);
    }

    private void renderChoices() {
        choices.removeAllViews();
        for (SearchTarget t : SearchTarget.values()) {
            LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setPadding(dp(2), dp(6), dp(2), dp(6));
            Drawable appIcon = installedIcon(t);
            AppIconView icon = new AppIconView(this, appIcon != null ? appIcon : getDrawable(R.drawable.ic_app_unavailable));
            item.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            space(item, 7); item.addView(appLabel(t.label, selected == t ? ink : muted));
            item.setTooltipText(t.label + (appIcon == null ? "（未安装）" : ""));
            if (selected == t) item.setBackground(background(0xFFF0F2ED, 14, 0));
            item.setContentDescription(t.label + (selected == t ? "，已选择" : "，点击选择")); item.setSelected(selected == t); item.setFocusable(true);
            item.setOnClickListener(v -> { selected = t; renderChoices(); updateButton(); });
            choices.addView(item, new LinearLayout.LayoutParams(0, dp(84), 1));
        }
        LinearLayout more = column(); more.setGravity(Gravity.CENTER);
        AppIconView dots = new AppIconView(this, getDrawable(R.drawable.ic_more_apps));
        more.addView(dots, new LinearLayout.LayoutParams(dp(44), dp(44))); more.setTooltipText("更多应用");
        space(more, 7); more.addView(appLabel("更多", muted));
        more.setContentDescription("更多应用"); more.setFocusable(true); more.setOnClickListener(v -> moreTargets());
        choices.addView(more, new LinearLayout.LayoutParams(0, dp(84), 1));
    }
    private TextView appLabel(String value, int color) {
        TextView label = text(value, 12, color);
        label.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        label.setSingleLine(true); label.setGravity(Gravity.CENTER);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return label;
    }
    private Drawable installedIcon(SearchTarget target) {
        // Read only these four declared packages locally; no package-list scan,
        // icon download or copied third-party artwork is needed.
        try { return getPackageManager().getApplicationIcon(target.packageName); }
        catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) { return null; }
    }
    private void updateButton() { if (searchButton != null) searchButton.setContentDescription(selected == null ? "搜索" : "去" + selected.label + "搜索"); }
    private void updateCount() { if (count != null) count.setText(getString(R.string.search_count, store.prefs.getLong("count", 0))); }
    private void submit() {
        String value = query.getText().toString().trim();
        if (value.isEmpty()) { query.setError("先写下想搜索的内容"); query.requestFocus(); return; }
        if (selected == null) { moreTargets(); return; }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
        SearchLauncher.launch(this, store, selected, value, this::updateCount);
    }
    private void moreTargets() {
        String[] names = new String[SearchTarget.values().length];
        for (SearchTarget t : SearchTarget.values()) names[t.ordinal()] = t.label;
        new AlertDialog.Builder(this).setTitle("选择应用").setItems(names, (dialog, which) -> {
            selected = SearchTarget.values()[which]; renderChoices(); updateButton();
        }).setNegativeButton("取消", null).show();
    }

    private LinearLayout page(String name) {
        base(name);
        LinearLayout header = row(); TextView title = text(name, 25, ink);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(80), 1)); title.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = action("×", this::showHome); close.setTextSize(28); close.setContentDescription("返回首页"); header.addView(close); root.addView(header);
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout body = column(); body.setPadding(0, dp(8), 0, dp(32)); scroll.addView(body); return body;
    }
    private void showHistory() {
        LinearLayout body = page("历史记录");
        body.addView(text("回溯每一次主动寻找。点击记录，再次搜索。", 12, muted)); space(body, 24);
        JSONArray entries = store.history();
        if (entries.length() == 0) { space(body, 70); body.addView(text("还没有搜索记录", 20, ink)); space(body, 12); body.addView(text("下一次主动寻找，会留在这里。", 14, muted)); return; }
        body.addView(action("清空历史记录", () -> new AlertDialog.Builder(this).setTitle("清空历史记录？").setMessage("此操作会删除本机的搜索记录，累计次数保留。")
                .setNegativeButton("取消", null).setPositiveButton("清空", (d, w) -> { store.prefs.edit().remove("history").apply(); showHistory(); }).show()));
        String lastDay = "";
        SimpleDateFormat day = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINA), time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null) continue;
            SearchTarget t = target(entry.optString("target")); if (t == null) continue;
            Date date = new Date(entry.optLong("time")); String dateText = day.format(date);
            if (!dateText.equals(lastDay)) { space(body, 22); body.addView(text(dateText, 17, ink)); space(body, 10); lastDay = dateText; }
            String q = entry.optString("query");
            LinearLayout record = column(); record.setPadding(0, dp(12), 0, dp(12));
            record.addView(text("用「" + t.label + "」搜索「" + q + "」", 15, ink)); space(record, 6);
            record.addView(text(time.format(date) + (entry.optBoolean("web") ? " · 网页" : " · 应用"), 11, muted));
            record.setContentDescription(t.label + "，" + q + "，再次搜索"); record.setFocusable(true);
            record.setOnClickListener(v -> { selected = t; draft = q; showHome(); submit(); });
            record.setOnLongClickListener(v -> { new AlertDialog.Builder(this).setMessage("删除这条记录？").setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> { JSONArray next = new JSONArray(); for (int n = 0; n < entries.length(); n++) if (entries.optJSONObject(n) != entry) next.put(entries.opt(n)); store.prefs.edit().putString("history", next.toString()).apply(); showHistory(); }).show(); return true; });
            body.addView(record); line(body);
        }
    }

    private void showSettings() {
        LinearLayout body = page("设置");
        body.addView(action("添加桌面小组件", () -> SearchWidget.add(this))); space(body, 16);
        section(body, "搜索偏好");
        SearchTarget defaultTarget = target(store.prefs.getString("default_target", ""));
        body.addView(action("默认应用  ·  " + (defaultTarget == null ? "每次选择" : defaultTarget.label), () -> {
            String[] options = new String[SearchTarget.values().length + 1];
            options[0] = "每次选择";
            for (SearchTarget t : SearchTarget.values()) options[t.ordinal() + 1] = t.label;
            new AlertDialog.Builder(this).setTitle("默认应用").setSingleChoiceItems(options, defaultTarget == null ? 0 : defaultTarget.ordinal() + 1, (d, which) -> {
                store.prefs.edit().putString("default_target", which == 0 ? "" : SearchTarget.values()[which-1].name()).apply(); d.dismiss(); showSettings();
            }).show();
        }));
        Switch history = new Switch(this); history.setTypeface(uiFont); history.setText("保存本地搜索历史"); history.setTextSize(15); history.setTextColor(ink); history.setMinHeight(dp(56));
        history.setChecked(store.prefs.getBoolean("save_history", true)); history.setOnCheckedChangeListener((v, checked) -> store.prefs.edit().putBoolean("save_history", checked).apply()); body.addView(history);
        body.addView(text("关闭后不再新增历史；已有记录可在历史页清空。", 12, muted)); space(body, 24);
        body.addView(action("微信搜索辅助  ·  " + (WechatSearchService.ready() ? "已开启" : "未开启"), () -> WechatSearchService.showOptions(this, draft)));
        String wechatStatus = WechatSearchService.status(this);
        if (!wechatStatus.isEmpty()) body.addView(text(wechatStatus, 12, muted));
        space(body, 24); section(body, "关于一搜");
        body.addView(text("独立开发的 Android 版本 · 0.4.0\n\n让搜索直达目的地。无账号、无广告、无分析 SDK；数据仅存在本机。\n\n豆瓣、淘宝、闲鱼通过链接搜索。微信按保存的位置模拟点击，自动粘贴关键词并搜索；首次需开启辅助权限并保存位置。目标应用仍可能要求登录。\n\n本项目与上述平台无隶属关系。源代码采用 GNU AGPL 第 3 版（AGPL-3.0-only）。允许遵守许可的商用；对外分发须提供对应源码。程序不附任何担保。", 13, muted));
        space(body, 16); body.addView(action("开源许可与源码", () -> LicenseInfo.show(this)));
        space(body, 16); body.addView(action("隐私说明", () -> new AlertDialog.Builder(this).setTitle("隐私说明").setMessage("应用不申请联网、通讯录或存储权限。微信辅助只模拟点击、长按，不读取界面控件或截图。搜索词会覆盖系统剪贴板，再粘贴到微信；不读取原剪贴板内容，结束后不清空剪贴板。保存的点击位置可在设置里清除。普通搜索最多 20 秒，首次校准最多 2 分钟，切换应用即停止。本地保存默认应用、最多 200 条搜索历史与累计次数，可关闭或清除历史。系统备份已禁用。")
                .setPositiveButton("知道了", null).show()));
    }
    private void section(LinearLayout body, String label) { TextView title = text(label, 17, ink); title.setTypeface(uiFont, Typeface.BOLD); body.addView(title); space(body, 10); }
    @Override public void onBackPressed() { if (!"home".equals(screen)) showHome(); else super.onBackPressed(); }
}
