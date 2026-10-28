// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/** Local, permission-protected collection adapter for Android 8–11. */
public final class WidgetAppsService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private java.util.List<SearchTarget> order = AppCatalog.ordered(WidgetAppsService.this);
            public void onCreate() { }
            public void onDataSetChanged() { order = AppCatalog.ordered(WidgetAppsService.this); }
            public void onDestroy() { }
            public int getCount() { return order.size(); }
            public RemoteViews getViewAt(int position) {
                return position >= 0 && position < getCount() ? SearchWidget.icon(WidgetAppsService.this, order.get(position)) : null;
            }
            public RemoteViews getLoadingView() { return null; }
            public int getViewTypeCount() { return 1; }
            public long getItemId(int position) { return order.get(position).ordinal(); }
            public boolean hasStableIds() { return true; }
        };
    }
}
