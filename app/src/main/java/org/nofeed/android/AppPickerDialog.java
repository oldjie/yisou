// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Local app choice and drag reorder. Drag data contains a platform name only. */
final class AppPickerDialog {
    interface Selection { void choose(SearchTarget target); }
    private final Activity activity;
    private final SearchTarget selected;
    private final Selection selection;
    private final Runnable changed;
    private final ScrollView scroll;
    private final LinearLayout list;
    private List<SearchTarget> order, beforeDrag;
    private SearchTarget dragging;
    private AlertDialog dialog;

    static void show(Activity activity, SearchTarget selected, Selection selection, Runnable changed) {
        new AppPickerDialog(activity, selected, selection, changed).show();
    }
    private AppPickerDialog(Activity activity, SearchTarget selected, Selection selection, Runnable changed) {
        this.activity = activity; this.selected = selected; this.selection = selection; this.changed = changed;
        order = AppCatalog.ordered(activity); list = new LinearLayout(activity); list.setOrientation(LinearLayout.VERTICAL);
        scroll = new ScrollView(activity); scroll.setFillViewport(false); scroll.addView(list);
        scroll.setOnDragListener((view, event) -> drag(event));
    }
    private int dp(float n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) {
        TextView text = new TextView(activity); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT); text.setGravity(Gravity.CENTER_VERTICAL); return text;
    }
    private void show() {
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(8), 0, dp(8), 0);
        TextView hint = label("点选应用 · 长按左侧拖动排序", 12, 0xFF7E847A); hint.setPadding(dp(12), dp(4), dp(12), dp(12)); body.addView(hint);
        int available = Math.round(activity.getResources().getDisplayMetrics().heightPixels / activity.getResources().getDisplayMetrics().density) - 240;
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(Math.max(200, Math.min(392, available)))));
        dialog = new AlertDialog.Builder(activity).setTitle("应用")
                .setView(body).setNegativeButton("完成", null).setNeutralButton("恢复默认", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            AppCatalog.reset(activity); order = AppCatalog.ordered(activity); render(); changed.run();
        }));
        render(); dialog.show();
    }
    private void render() {
        list.removeAllViews();
        for (SearchTarget target : order) {
            LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setAlpha(target == dragging ? .35f : 1f);
            if (target == selected) { GradientDrawable fill = new GradientDrawable(); fill.setColor(0xFFF0F2ED); fill.setCornerRadius(dp(12)); row.setBackground(fill); }
            ImageView handle = new ImageView(activity); handle.setImageResource(R.drawable.ic_drag_handle); handle.setScaleType(ImageView.ScaleType.CENTER);
            handle.setContentDescription("拖动排序："+target.label); handle.setFocusable(true);
            handle.setOnLongClickListener(v -> v.startDragAndDrop(ClipData.newPlainText("排序应用", target.name()), new View.DragShadowBuilder(row), target, 0));
            // A click also exposes accessible move actions without a drag gesture.
            handle.setOnClickListener(v -> new AlertDialog.Builder(activity).setTitle(target.label+"排序")
                    .setItems(new String[]{"上移", "下移"}, (d, which) -> {
                        int from = order.indexOf(target), to = Math.max(0, Math.min(order.size()-1, from+(which==0?-1:1)));
                        if (from != to) { move(from, to); AppCatalog.save(activity, order); changed.run(); }
                    }).show());
            row.addView(handle, new LinearLayout.LayoutParams(dp(44), dp(56)));
            row.addView(new AppIconView(activity, AppCatalog.icon(activity, target)), new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView name = label(target.label, 15, 0xFF282F29); name.setPadding(dp(12),0,0,0);
            row.addView(name, new LinearLayout.LayoutParams(0, dp(56), 1));
            TextView status = label(AppCatalog.installed(activity,target) ? "已安装" : "未安装", 11, 0xFF7E847A);
            status.setPadding(dp(6),0,dp(12),0); row.addView(status);
            row.setContentDescription("选择应用："+target.label); row.setFocusable(true);
            row.setOnClickListener(v -> { selection.choose(target); dialog.dismiss(); });
            list.addView(row, new LinearLayout.LayoutParams(-1, dp(56)));
        }
    }
    private void move(int from, int to) {
        if (from < 0 || from == to) return;
        SearchTarget target = order.remove(from); order.add(to, target); render();
    }
    private boolean drag(DragEvent event) {
        if (!(event.getLocalState() instanceof SearchTarget)) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                dragging = (SearchTarget)event.getLocalState(); beforeDrag = new ArrayList<>(order); render(); return true;
            case DragEvent.ACTION_DRAG_LOCATION:
                int to = Math.max(0, Math.min(order.size()-1, (int)((event.getY()+scroll.getScrollY())/dp(56))));
                move(order.indexOf(dragging), to);
                if (event.getY()<dp(32)) scroll.smoothScrollBy(0,-dp(16));
                else if (event.getY()>scroll.getHeight()-dp(32)) scroll.smoothScrollBy(0,dp(16));
                return true;
            case DragEvent.ACTION_DROP:
                if (!order.equals(beforeDrag)) { AppCatalog.save(activity, order); changed.run(); } return true;
            case DragEvent.ACTION_DRAG_ENDED:
                if (!event.getResult() && beforeDrag != null) order = beforeDrag;
                dragging = null; beforeDrag = null; render(); return true;
            default: return true;
        }
    }
}
