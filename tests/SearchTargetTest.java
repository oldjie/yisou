package org.nofeed.android;

/** Run without Android/JUnit: exercises actual platform adapters and escaping. */
public class SearchTargetTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (SearchTarget t : SearchTarget.values()) {
            if (t == SearchTarget.WECHAT) { check(t.appTemplate == null && t.webTemplate == null, "WeChat must use client automation, never a fake URI or web fallback"); continue; }
            String app = SearchTarget.buildUrl(t.appTemplate, "  三体 & a+b?#/ 🎈  ");
            String decoded = t == SearchTarget.TAOBAO ? decode(app.substring(app.indexOf("h5Url=") + 6)) : app;
            check(decoded.contains("%E4%B8%89%E4%BD%93%20%26%20a%2Bb%3F%23%2F%20%F0%9F%8E%88"), "UTF-8 and reserved characters: " + t);
            check(!app.contains("{query}"), "No unresolved placeholder");
            check(SearchTarget.buildUrl(t.webTemplate, "100%棉").contains("100%25"), "Literal percent");
        }
        boolean failed = false;
        try { SearchTarget.buildUrl(SearchTarget.DOUBAN.appTemplate, "  "); } catch (IllegalArgumentException e) { failed = true; }
        check(failed, "Blank search rejected");
        failed = false;
        try { SearchTarget.buildUrl("https://example.com/", "text"); } catch (IllegalArgumentException e) { failed = true; }
        check(failed, "Missing placeholder rejected");
        String query = "哈利波特 & a+b?#/🎈";
        String gateway = SearchTarget.buildUrl(SearchTarget.TAOBAO.appTemplate, query);
        check(decode(gateway.substring(gateway.indexOf("h5Url=") + 6)).equals(SearchTarget.buildUrl(SearchTarget.TAOBAO.webTemplate, query)), "Taobao nested URL preserves all characters after one decode");
        check(SearchTarget.DOUBAN.appTemplate.contains("/search/result?"), "Douban must open results instead of search input");
        check(SearchTarget.XIANYU.appTemplate.contains("initSubPageTag=searchResult"), "Xianyu must select result subpage");
        check(SearchTarget.TAOBAO.appTemplate.startsWith("tbopen://m.taobao.com/tbopen/"), "Taobao must use public gateway instead of private result activity");
        System.out.println("SearchTarget tests passed (3 adapters, Unicode, special characters, validation).");
    }
    private static String decode(String value) {
        try { return java.net.URLDecoder.decode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
