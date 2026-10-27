// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** Single-row search entry and a horizontally transformed system collection. */
public class SearchWidget extends AppWidgetProvider {
    private static Context appContext;
    static void initialize(Context context) { appContext = context.getApplicationContext(); }
    static void add(android.app.Activity activity) {
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        ComponentName provider = new ComponentName(activity, SearchWidget.class);
        if (manager.isRequestPinAppWidgetSupported() && manager.requestPinAppWidget(provider, null, null)) return;
        new android.app.AlertDialog.Builder(activity).setTitle("从桌面添加")
                .setMessage("在桌面长按空白处或双指捏合，进入小组件列表，找到‘一搜 · 搜索（4×1）’。\n\n华为通常位于‘服务卡片’页面最底部的‘窗口小工具’。添加后点搜索框或应用图标，图标行可左右滑动。")
                .setPositiveButton("知道了", null).show();
    }
    static void refresh() {
        if (appContext == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        new SearchWidget().onUpdate(appContext, manager, manager.getAppWidgetIds(new ComponentName(appContext, SearchWidget.class)));
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.search_widget_compact);
            view.setOnClickPendingIntent(R.id.widget_query, input(context, id, false));
            // ListView is rotated in XML; each icon is counter-rotated. Touch
            // coordinates follow Android's transform, enabling horizontal drag
            // without custom views or intercepting launcher-wide gestures.
            if (Build.VERSION.SDK_INT >= 31) {
                view.setViewLayoutHeight(R.id.widget_apps, Math.max(120, width - 100), TypedValue.COMPLEX_UNIT_DIP);
                RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true).setViewTypeCount(1);
                for (SearchTarget target : SearchTarget.values()) items.addItem(target.ordinal(), icon(context, target));
                view.setRemoteAdapter(R.id.widget_apps, items.build());
            } else {
                // Older systems keep a narrow 120dp viewport, still scrollable.
                Intent service = new Intent(context, WidgetAppsService.class)
                        .setData(Uri.parse("yisou://widget-items/" + id));
                view.setRemoteAdapter(R.id.widget_apps, service);
            }
            view.setPendingIntentTemplate(R.id.widget_apps, input(context, id, true));
            Intent advance = new Intent(context, SearchWidget.class).setAction(context.getPackageName()+".WIDGET_PAGE")
                    .setData(Uri.parse("yisou://widget/"+id+"/page")).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            view.setOnClickPendingIntent(R.id.widget_next, PendingIntent.getBroadcast(context, 0, advance, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, view);
            if (Build.VERSION.SDK_INT < 31) manager.notifyAppWidgetViewDataChanged(id, R.id.widget_apps);
        }
    }
    @Override public void onReceive(Context context, Intent intent) {
        if ((context.getPackageName()+".WIDGET_PAGE").equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info == null || !info.provider.equals(new ComponentName(context, SearchWidget.class))) return;
            android.content.SharedPreferences prefs = context.getSharedPreferences("widget_ui", Context.MODE_PRIVATE);
            boolean end = !prefs.getBoolean("end_"+id, false); prefs.edit().putBoolean("end_"+id, end).apply();
            RemoteViews action = new RemoteViews(context.getPackageName(), R.layout.search_widget_compact);
            action.setScrollPosition(R.id.widget_apps, end ? SearchTarget.values().length - 1 : 0);
            action.setTextViewText(R.id.widget_next, end ? "‹" : "›");
            action.setContentDescription(R.id.widget_next, end ? "前一组应用" : "下一组应用");
            manager.partiallyUpdateAppWidget(id, action); return;
        }
        super.onReceive(context, intent);
    }
    @Override public void onDeleted(Context context, int[] ids) {
        android.content.SharedPreferences.Editor edit = context.getSharedPreferences("widget_ui", Context.MODE_PRIVATE).edit();
        for (int id : ids) edit.remove("end_"+id); edit.apply();
    }
    static RemoteViews icon(Context context, SearchTarget target) {
        android.graphics.drawable.Drawable artwork;
        try { artwork = context.getPackageManager().getApplicationIcon(target.packageName); }
        catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) { artwork = context.getDrawable(R.drawable.ic_app_unavailable); }
        int size = Math.min(160, Math.round(28 * context.getResources().getDisplayMetrics().density));
        RemoteViews item = new RemoteViews(context.getPackageName(), R.layout.widget_app_item);
        item.setImageViewBitmap(R.id.widget_app_icon, AppIconView.render(context, artwork, size));
        item.setContentDescription(R.id.widget_app_item, target.label + "，输入并搜索");
        item.setOnClickFillInIntent(R.id.widget_app_item, new Intent().putExtra("target", target.name()));
        return item;
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        onUpdate(c, manager, new int[]{id});
    }
    private static PendingIntent input(Context context, int id, boolean collection) {
        Intent intent = new Intent(context, WidgetSearchActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .setPackage(context.getPackageName())
                .setData(Uri.parse("yisou://widget/" + id + (collection ? "/apps" : "/query")))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // Collections require fill-in extras. Component, package, action and data
        // are fixed; only the validated target enum is consumed by our activity.
        int mutability = collection ? (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0) : PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | mutability);
    }
}
