// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import java.util.EnumSet;
import java.util.List;

final class AppCatalog {
    static boolean installed(Context context, SearchTarget target) {
        try { context.getPackageManager().getApplicationInfo(target.packageName, 0); return true; }
        catch (PackageManager.NameNotFoundException | RuntimeException unavailable) { return false; }
    }
    static List<SearchTarget> ordered(Context context) {
        EnumSet<SearchTarget> installed = EnumSet.noneOf(SearchTarget.class);
        for (SearchTarget t : SearchTarget.values()) if (installed(context, t)) installed.add(t);
        return TargetOrdering.resolve(new LocalStore(context).prefs.getString("target_order", ""), installed);
    }
    static void save(Context context, List<SearchTarget> order) {
        new LocalStore(context).prefs.edit().putString("target_order", TargetOrdering.encode(order)).apply();
        SearchWidget.initialize(context); SearchWidget.refresh();
    }
    static void reset(Context context) {
        new LocalStore(context).prefs.edit().remove("target_order").apply();
        SearchWidget.initialize(context); SearchWidget.refresh();
    }
    static Drawable icon(Context context, SearchTarget target) {
        int id;
        switch (target) {
            case DOUBAN: id = R.drawable.platform_douban; break;
            case TAOBAO: id = R.drawable.platform_taobao; break;
            case XIANYU: id = R.drawable.platform_xianyu; break;
            case WECHAT: id = R.drawable.platform_wechat; break;
            case BILIBILI: id = R.drawable.platform_bilibili; break;
            case PINDUODUO: id = R.drawable.platform_pinduoduo; break;
            case XIAOHONGSHU: id = R.drawable.platform_xiaohongshu; break;
            case MEITUAN: id = R.drawable.platform_meituan; break;
            case CTRIP: id = R.drawable.platform_ctrip; break;
            case AMAP: id = R.drawable.platform_amap; break;
            case TAOBAO_SHANGOU: id = R.drawable.platform_taobao_shangou; break;
            default: throw new AssertionError(target);
        }
        return context.getDrawable(id);
    }
}
