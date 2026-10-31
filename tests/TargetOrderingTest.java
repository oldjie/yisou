package org.nofeed.android;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public final class TargetOrderingTest {
    private static void check(boolean condition, String why) { if (!condition) throw new AssertionError(why); }
    public static void main(String[] args) {
        EnumSet<SearchTarget> installed = EnumSet.of(SearchTarget.TAOBAO, SearchTarget.XIAOHONGSHU);
        List<SearchTarget> defaults = TargetOrdering.resolve("", installed);
        check(defaults.get(0)==SearchTarget.TAOBAO && defaults.get(1)==SearchTarget.XIAOHONGSHU, "Installed applications lead the default order");
        check(defaults.size()==11 && defaults.get(2)==SearchTarget.DOUBAN, "All missing applications remain present");
        SearchTarget[] legacy = {SearchTarget.DOUBAN, SearchTarget.TAOBAO, SearchTarget.XIANYU,
                SearchTarget.WECHAT, SearchTarget.BILIBILI, SearchTarget.PINDUODUO, SearchTarget.XIAOHONGSHU};
        check(Arrays.equals(Arrays.copyOf(SearchTarget.values(), legacy.length), legacy), "Existing target IDs retain their positions after expansion");
        String custom = "WECHAT,DOUBAN,TAOBAO,XIANYU,BILIBILI,PINDUODUO,XIAOHONGSHU";
        List<SearchTarget> upgraded = TargetOrdering.resolve(custom,installed);
        check(TargetOrdering.encode(upgraded.subList(0,7)).equals(custom), "The saved seven-app manual order survives an upgrade unchanged");
        check(upgraded.subList(7,11).equals(Arrays.asList(SearchTarget.MEITUAN, SearchTarget.CTRIP,
                SearchTarget.AMAP, SearchTarget.TAOBAO_SHANGOU)), "New apps append to the saved order instead of resetting it");
        String allCustom = TargetOrdering.encode(upgraded);
        check(TargetOrdering.encode(TargetOrdering.resolve(allCustom,installed)).equals(allCustom), "The expanded manual order round-trips");
        check(TargetOrdering.resolve(custom,EnumSet.of(SearchTarget.AMAP)).subList(0,7).equals(upgraded.subList(0,7)), "Installing a new app cannot reorder the user's saved seven apps");
        List<SearchTarget> partial = TargetOrdering.resolve("WECHAT,UNKNOWN,WECHAT",installed);
        check(partial.size()==11 && partial.get(0)==SearchTarget.WECHAT && partial.get(1)==SearchTarget.TAOBAO, "Unknown and duplicate entries cannot hide targets; new targets append");
        check(TargetOrdering.resolve("UNKNOWN",installed).equals(defaults), "Invalid saved state falls back safely");
        check(TargetOrdering.resolve(null,EnumSet.noneOf(SearchTarget.class)).equals(Arrays.asList(SearchTarget.values())), "All-uninstalled state retains all icons in standard order");
        System.out.println("Target ordering tests passed (installed priority, manual override, persistence, malformed state, all missing).");
    }
}
