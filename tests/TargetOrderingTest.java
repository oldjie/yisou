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
        check(defaults.size()==7 && defaults.get(2)==SearchTarget.DOUBAN, "All missing applications remain present");
        String custom = "WECHAT,DOUBAN,TAOBAO,XIANYU,BILIBILI,PINDUODUO,XIAOHONGSHU";
        check(TargetOrdering.encode(TargetOrdering.resolve(custom,installed)).equals(custom), "Manual order overrides installation status and round-trips");
        List<SearchTarget> partial = TargetOrdering.resolve("WECHAT,UNKNOWN,WECHAT",installed);
        check(partial.size()==7 && partial.get(0)==SearchTarget.WECHAT && partial.get(1)==SearchTarget.TAOBAO, "Unknown and duplicate entries cannot hide targets; new targets append");
        check(TargetOrdering.resolve("UNKNOWN",installed).equals(defaults), "Invalid saved state falls back safely");
        check(TargetOrdering.resolve(null,EnumSet.noneOf(SearchTarget.class)).equals(Arrays.asList(SearchTarget.values())), "All-uninstalled state retains all icons in standard order");
        System.out.println("Target ordering tests passed (installed priority, manual override, persistence, malformed state, all missing).");
    }
}
