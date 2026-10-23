package org.nofeed.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SearchWidget extends AppWidgetProvider {
    private static Context appContext;
    static void initialize(Context context) { appContext = context.getApplicationContext(); }
    static void refresh() {
        if (appContext == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        new SearchWidget().onUpdate(appContext, manager, manager.getAppWidgetIds(new ComponentName(appContext, SearchWidget.class)));
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        int[] views = {R.id.widget_douban, R.id.widget_taobao, R.id.widget_xianyu, R.id.widget_wechat};
        for (int id : ids) {
            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.search_widget);
            view.setTextViewText(R.id.widget_count, "一搜 · 已主动搜索 " + new LocalStore(context).prefs.getLong("count", 0) + " 次");
            for (SearchTarget target : SearchTarget.values()) {
                Intent intent = new Intent(context, MainActivity.class).putExtra("target", target.name())
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                view.setOnClickPendingIntent(views[target.ordinal()], PendingIntent.getActivity(context, target.ordinal(), intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
            manager.updateAppWidget(id, view);
        }
    }
}
