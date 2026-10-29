// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Native settings list: shared spacing, system typography and touch feedback. */
final class SettingsUi {
    private final Context context;
    private final Appearance.Palette colors;
    SettingsUi(Context context) { this.context = context; colors = Appearance.palette(context); }
    int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    TextView text(String value, int size, int color) {
        TextView v = new TextView(context); v.setText(value); v.setTextSize(size);
        v.setTextColor(color); v.setTypeface(Typeface.DEFAULT); v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    GradientDrawable surface(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    void interactive(View view, int color, int radius, Runnable click) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(colors.ripple), surface(color, radius), null));
        view.setFocusable(true); view.setOnClickListener(v -> click.run());
    }
    LinearLayout group(LinearLayout body, String heading) {
        TextView title = text(heading, 12, colors.muted); title.setPadding(dp(4), dp(22), 0, dp(8)); body.addView(title);
        LinearLayout group = new LinearLayout(context); group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(surface(colors.surface, 16)); group.setClipToOutline(true);
        body.addView(group, new LinearLayout.LayoutParams(-1, -2)); return group;
    }
    private LinearLayout row(LinearLayout group, String title, String summary) {
        if (group.getChildCount() > 0) {
            View line = new View(context); line.setBackgroundColor(colors.divider);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.leftMargin = dp(16); p.rightMargin = dp(16); group.addView(line, p);
        }
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(12), dp(12), dp(12)); row.setMinimumHeight(dp(60));
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 15, colors.ink));
        if (summary != null && !summary.isEmpty()) {
            TextView detail = text(summary, 12, colors.muted); detail.setPadding(0, dp(4), dp(8), 0); labels.addView(detail);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); group.addView(row); return row;
    }
    void action(LinearLayout group, String title, String summary, Runnable click) {
        LinearLayout row = row(group, title, summary);
        TextView arrow = text("›", 22, colors.muted); arrow.setGravity(Gravity.CENTER);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(32)));
        row.setContentDescription(title + (summary == null ? "" : "，" + summary));
        interactive(row, colors.surface, 0, click);
    }
    void choice(LinearLayout group, String title, String value, Runnable click) {
        LinearLayout row = row(group, title, null);
        TextView button = text(value + "  ▾", 14, colors.ink); button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0); button.setMinHeight(dp(48));
        button.setContentDescription(title + "：" + value + "，点击选择");
        interactive(button, colors.field, 10, click); row.addView(button);
        // The label and its surrounding row deliberately have no click listener.
    }
    void toggle(LinearLayout group, String title, String summary, boolean checked, java.util.function.Consumer<Boolean> change) {
        LinearLayout row = row(group, title, summary);
        Switch control = new Switch(context); control.setShowText(false); control.setChecked(checked);
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}};
        control.setThumbTintList(new ColorStateList(states, new int[]{colors.accent, colors.muted}));
        control.setTrackTintList(new ColorStateList(states, new int[]{colors.muted, colors.divider}));
        control.setContentDescription(title); control.setMinWidth(dp(48)); control.setMinHeight(dp(48));
        control.setOnCheckedChangeListener((v, enabled) -> change.accept(enabled)); row.addView(control);
        interactive(row, colors.surface, 0, () -> control.setChecked(!control.isChecked()));
    }
}
