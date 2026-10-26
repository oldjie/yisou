package org.nofeed.android;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;

public class SearchWidget extends AppWidgetProvider {
    private static Context appContext;
    static void initialize(Context context) { appContext = context.getApplicationContext(); }
    static void add(android.app.Activity activity) {
        new android.app.AlertDialog.Builder(activity).setTitle("添加桌面小组件")
                .setItems(new String[]{"快捷搜索 · 4列×1行", "搜索面板 · 4列×2行"}, (d, which) -> {
                    AppWidgetManager manager = AppWidgetManager.getInstance(activity);
                    ComponentName provider = new ComponentName(activity, which == 0 ? SearchWidget.class : SearchPanelWidget.class);
                    if (manager.isRequestPinAppWidgetSupported() && manager.requestPinAppWidget(provider, null, null)) return;
                    new android.app.AlertDialog.Builder(activity).setTitle("从桌面添加")
                            .setMessage("在桌面长按空白处或双指捏合，进入小组件列表，找到‘一搜’。\n\n华为通常位于‘服务卡片’页面最底部的‘窗口小工具’。选择4列×1行或4列×2行，添加后可长按调整尺寸。")
                            .setPositiveButton("知道了", null).show();
                }).setNegativeButton("取消", null).show();
    }
    static void refresh() {
        if (appContext == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        SearchWidget provider = new SearchWidget();
        provider.onUpdate(appContext, manager, manager.getAppWidgetIds(new ComponentName(appContext, SearchWidget.class)));
        provider.onUpdate(appContext, manager, manager.getAppWidgetIds(new ComponentName(appContext, SearchPanelWidget.class)));
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        int[] views = {R.id.widget_douban, R.id.widget_taobao, R.id.widget_xianyu, R.id.widget_wechat};
        int[] icons = {R.id.widget_douban_icon, R.id.widget_taobao_icon, R.id.widget_xianyu_icon, R.id.widget_wechat_icon};
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            android.appwidget.AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            boolean panel = height > 0 ? height >= 110 : info != null && info.provider.getClassName().equals(SearchPanelWidget.class.getName());
            RemoteViews view = new RemoteViews(context.getPackageName(), panel ? R.layout.search_widget : R.layout.search_widget_compact);
            if (panel) view.setOnClickPendingIntent(R.id.widget_query, open(context, id, null));
            for (SearchTarget target : SearchTarget.values()) {
                android.graphics.drawable.Drawable icon;
                try { icon = context.getPackageManager().getApplicationIcon(target.packageName); }
                catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException unavailable) { icon = context.getDrawable(R.drawable.ic_app_unavailable); }
                int size = Math.min(160, (int)(34 * context.getResources().getDisplayMetrics().density + .5f));
                view.setImageViewBitmap(icons[target.ordinal()], AppIconView.render(context, icon, size));
                view.setContentDescription(views[target.ordinal()], target.label + "，输入并搜索");
                view.setOnClickPendingIntent(views[target.ordinal()], open(context, id, target));
            }
            manager.updateAppWidget(id, view);
        }
    }

    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        onUpdate(c, manager, new int[]{id});
    }

    private static PendingIntent open(Context context, int id, SearchTarget target) {
        String name = target == null ? "choose" : target.name();
        Intent intent = new Intent(context, WidgetSearchActivity.class)
                .setData(Uri.parse("yisou://widget/" + id + "/" + name))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (target != null) intent.putExtra("target", target.name());
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
