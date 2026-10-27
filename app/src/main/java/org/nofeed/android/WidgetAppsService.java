// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/** Local, permission-protected collection adapter for Android 8–11. */
public final class WidgetAppsService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            public void onCreate() { }
            public void onDataSetChanged() { }
            public void onDestroy() { }
            public int getCount() { return SearchTarget.values().length; }
            public RemoteViews getViewAt(int position) {
                return position >= 0 && position < getCount() ? SearchWidget.icon(WidgetAppsService.this, SearchTarget.values()[position]) : null;
            }
            public RemoteViews getLoadingView() { return null; }
            public int getViewTypeCount() { return 1; }
            public long getItemId(int position) { return position; }
            public boolean hasStableIds() { return true; }
        };
    }
}
