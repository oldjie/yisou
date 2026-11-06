package org.nofeed.android;

public final class WidgetPagingTest {
    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
    private static void coverage(int count, int width, boolean modern) {
        WidgetPaging pages = new WidgetPaging(count, width, modern);
        WidgetPaging.State state = pages.at(0, false);
        boolean[] seen = new boolean[count];
        int viewport = modern ? width - 100 : Math.min(120, width - 100);
        int visible = Math.max(1, viewport / 44);
        int previous = -1;
        for (int page = 0; page <= pages.lastPage; page++) {
            check(state.page == page, "Forward arrow visits consecutive pages");
            check(state.position >= 0 && state.position < Math.max(1, count), "Position stays within catalog");
            if (previous >= 0 && visible > 1)
                check(state.position < previous + visible, "Adjacent pages share a complete app");
            for (int i = state.position; i < Math.min(count, state.position + visible); i++) seen[i] = true;
            previous = state.position;
            if (page < pages.lastPage) {
                check(!state.backwards, "Do not reverse before reaching the last page");
                state = pages.next(state.page, state.backwards);
            }
        }
        for (int i = 0; i < count; i++) check(seen[i], "Arrow-only paging must fully expose app " + i + " at width " + width);
        for (int page = pages.lastPage; page > 0; page--) {
            check(state.backwards, "Return arrow keeps direction until the first page");
            state = pages.next(state.page, state.backwards);
            check(state.page == page - 1, "Return retraces every intermediate page");
        }
        check(state.position == 0 && !state.backwards, "First page restores forward arrow");
        check(pages.at(Integer.MAX_VALUE, true).page == pages.lastPage, "Stale page clamps after resize or catalog change");
        check(pages.at(-1, true).position == 0, "Invalid saved state resets safely");
    }
    public static void main(String[] args) {
        for (int count = 0; count <= 64; count++)
            for (int width = 220; width <= 800; width++) {
                coverage(count, width, true); coverage(count, width, false);
            }
        WidgetPaging pages = new WidgetPaging(11, 300, true);
        WidgetPaging.State middle = pages.next(0, false);
        check(middle.position == 3 && !middle.backwards, "The 4.5-app viewport must visit its middle, not jump to the end");
        check(pages.scrollTarget(middle, false) == 6, "Forward scroll must request the new page's final full item, outside the initial viewport");
        check(pages.scrollTarget(middle, true) == 3, "Backward scroll must request the page's first item");
        WidgetPaging.State end = pages.at(pages.lastPage, false);
        check(end.backwards && pages.scrollTarget(end, false) == 10, "Arrival at the end still moves forward before the arrow reverses");
        check(new WidgetPaging(11, 800, true).lastPage == 0, "No paging needed when all apps fit");
        System.out.println("Widget paging tests passed (all apps fully reachable, overlap, reverse traversal, resize, legacy viewport).");
    }
}
