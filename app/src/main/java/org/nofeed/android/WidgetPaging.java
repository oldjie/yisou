// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

/** Width-aware pages with one whole item of overlap, independent of app order. */
final class WidgetPaging {
    private final int stride, lastStart, visible, lastItem;
    final int lastPage;

    WidgetPaging(int count, int widgetWidthDp, boolean dynamicViewport) {
        // Match search_widget_compact: 16dp padding + 60dp field + 4dp gap
        // + 20dp arrow. Legacy RemoteViews retain their fixed 120dp viewport.
        int viewport = Math.max(44, widgetWidthDp - 100);
        if (!dynamicViewport) viewport = Math.min(120, viewport);
        visible = Math.max(1, viewport / 44);
        lastItem = Math.max(0, count - 1);
        stride = Math.max(1, visible - 1);
        lastStart = Math.max(0, count - visible);
        lastPage = (lastStart + stride - 1) / stride;
    }

    State at(int page, boolean backwards) {
        page = Math.max(0, Math.min(lastPage, page));
        return new State(page, Math.min(lastStart, page * stride),
                page > 0 && (page == lastPage || backwards));
    }

    State next(int page, boolean backwards) {
        State current = at(page, backwards);
        return at(current.page + (current.backwards ? -1 : 1), current.backwards);
    }

    int scrollTarget(State page, boolean movingBackwards) {
        // RemoteViews uses smoothScrollToPosition: it only brings an item into
        // view, not to the leading edge. Forward pages must expose their LAST
        // full item; backwards pages expose their FIRST. An overlap item that
        // is already visible would otherwise make the forward click a no-op.
        if (page.page == 0 || movingBackwards) return page.position;
        return Math.min(lastItem, page.position + visible - 1);
    }

    static final class State {
        final int page, position;
        final boolean backwards;
        State(int page, int position, boolean backwards) {
            this.page = page; this.position = position; this.backwards = backwards;
        }
    }
}
