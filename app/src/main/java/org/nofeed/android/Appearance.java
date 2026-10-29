// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;

/** Local appearance preference shared by activities and launcher widgets. */
final class Appearance {
    private Appearance() { }

    static String mode(Context context) {
        String mode = context.getApplicationContext().getSharedPreferences("local", Context.MODE_PRIVATE)
                .getString("appearance_mode", "system");
        return "light".equals(mode) || "dark".equals(mode) ? mode : "system";
    }

    static void save(Context context, String mode) {
        if (!"system".equals(mode) && !"light".equals(mode) && !"dark".equals(mode)) return;
        context.getApplicationContext().getSharedPreferences("local", Context.MODE_PRIVATE)
                .edit().putString("appearance_mode", mode).apply();
    }

    static boolean isDark(Context context) {
        String mode = mode(context);
        if ("dark".equals(mode)) return true;
        if ("light".equals(mode)) return false;
        // Activity override resources reflect a manual choice. The application
        // configuration continues to track the real system appearance.
        return (context.getApplicationContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Attach the manual override before the framework first loads resources. */
    static Context wrap(Context base) {
        String mode = mode(base);
        if ("system".equals(mode)) return base;
        Configuration override = new Configuration();
        override.uiMode = (base.getResources().getConfiguration().uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | ("dark".equals(mode) ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(override);
    }

    /** The resource configuration is already installed by attachBaseContext. */
    static void apply(Activity activity, boolean widget) {
        activity.setTheme(widget ? R.style.WidgetSearchTheme : R.style.AppTheme);
    }

    static Palette palette(Context context) { return new Palette(isDark(context)); }

    static final class Palette {
        final int paper, settings, surface, field, selected, ink, muted, hint, divider, ripple, accent;
        Palette(boolean dark) {
            paper = dark ? 0xFF141715 : 0xFFFFFFFF;
            settings = dark ? 0xFF141715 : 0xFFF5F6F4;
            surface = dark ? 0xFF20251F : 0xFFFFFFFF;
            field = dark ? 0xFF272D26 : 0xFFF3F5F1;
            selected = dark ? 0xFF2B382B : 0xFFF0F2ED;
            ink = dark ? 0xFFE4E9E1 : 0xFF282F29;
            muted = dark ? 0xFFA7B0A3 : 0xFF7E847A;
            hint = dark ? 0xFF99A293 : 0xFF999E98;
            divider = dark ? 0xFF343C32 : 0xFFE0E2D9;
            ripple = dark ? 0x30D4E4CD : 0x18474E47;
            accent = dark ? 0xFFC7D9BF : 0xFF474E47;
        }
    }

    /** Match the page all the way into the system bars, including older APIs. */
    static void bars(Activity activity, int background) {
        Window window = activity.getWindow();
        boolean light = luminance(background) > .5;
        window.setStatusBarColor(background);
        window.setNavigationBarColor(Build.VERSION.SDK_INT >= 27 || !light ? background : 0xFF282F29);
        int flags = window.getDecorView().getSystemUiVisibility();
        flags = light ? flags | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 27)
            flags = light ? flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false); window.setNavigationBarContrastEnforced(false);
            window.getDecorView().setForceDarkAllowed(false);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(light ? mask : 0, mask);
            }
        }
        TypedArray attributes = activity.obtainStyledAttributes(new int[]{android.R.attr.windowIsFloating});
        boolean floating = attributes.getBoolean(0, false); attributes.recycle();
        // A floating widget input window must retain its transparent surround.
        if (!floating) window.setBackgroundDrawable(new ColorDrawable(background));
    }

    private static double luminance(int color) {
        return .2126 * Color.red(color) / 255 + .7152 * Color.green(color) / 255 + .0722 * Color.blue(color) / 255;
    }
}
