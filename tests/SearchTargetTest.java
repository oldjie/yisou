package org.nofeed.android;

/** Run without Android/JUnit: exercises actual platform adapters and escaping. */
public class SearchTargetTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        check(SearchTarget.values().length == 11, "Eleven supported targets");
        for (SearchTarget t : SearchTarget.values()) {
            if (t == SearchTarget.WECHAT) { check(t.appTemplate == null && t.webTemplate == null, "WeChat must use client automation, never a fake URI or web fallback"); continue; }
            String app = SearchTarget.buildUrl(t.appTemplate, "  三体 & a+b?#/ 🎈  ");
            String decoded = t == SearchTarget.TAOBAO ? decode(app.substring(app.indexOf("h5Url=") + 6)) : t == SearchTarget.CTRIP ? ctripPage(app) : app;
            check(decoded.contains("%E4%B8%89%E4%BD%93%20%26%20a%2Bb%3F%23%2F%20%F0%9F%8E%88"), "UTF-8 and reserved characters: " + t);
            check(!app.contains("{query}"), "No unresolved placeholder");
            if (t.webTemplate != null) check(SearchTarget.buildUrl(t.webTemplate, "100%棉").contains("100%25"), "Literal percent");
            else check(t==SearchTarget.MEITUAN || t==SearchTarget.CTRIP || t==SearchTarget.TAOBAO_SHANGOU, "Only reviewed native-only targets omit web fallback");
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
        check(SearchTarget.BILIBILI.appTemplate.startsWith("bilibili://search?keyword="), "Bilibili native search");
        check(SearchTarget.PINDUODUO.appTemplate.contains("/search_result.html?search_key="), "Pinduoduo results route");
        check(SearchTarget.XIAOHONGSHU.appTemplate.startsWith("xhsdiscover://search/result?keyword="), "Xiaohongshu results route");
        check(SearchTarget.MEITUAN.appTemplate.contains("/search/result?q="), "Meituan results rather than input page");
        check(SearchTarget.TAOBAO_SHANGOU.packageName.equals("me.ele") && SearchTarget.TAOBAO_SHANGOU.appTemplate.startsWith("eleme://xsearchresult?keyword="), "Standalone flash-shopping result route, separate from Taobao");
        check(SearchTarget.AMAP.appTemplate.startsWith("androidamap://poi?") && !SearchTarget.AMAP.appTemplate.contains("lat="), "Official Amap keyword route uses destination app context");
        String ctrip = SearchTarget.buildUrl(SearchTarget.CTRIP.appTemplate, query);
        String page = ctripPage(ctrip);
        check(ctrip.startsWith("ctrip://wireless/h5?url=") && ctrip.endsWith("&type=5"), "Ctrip public native result gateway");
        check(page.startsWith("/rn_search/_crn_config?CRNModuleName=CtripApp&CRNType=1"), "Ctrip global results module");
        String keyword = page.substring(page.indexOf("&keyword=")+9, page.indexOf("&cityid="));
        check(decode(keyword).equals(query), "Ctrip keyword survives UTF-8, reserved characters, emoji and two routing layers");
        check(page.contains("&cityid=0&lat=-180.0&lon=-180.0") && page.contains("&datatype=all"), "Ctrip uses its own unlocated defaults and all result types");
        check(!ctrip.contains("\n") && !ctrip.contains("%3D"), "Ctrip gateway receives the raw Base64 expected by its parser");
        System.out.println("SearchTarget tests passed (10 URI adapters + WeChat, Unicode, special characters, validation).");
    }
    private static String ctripPage(String uri) {
        String payload = uri.substring(uri.indexOf("url=")+4, uri.lastIndexOf("&type=5"));
        try { return new String(java.util.Base64.getDecoder().decode(payload), "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    private static String decode(String value) {
        try { return java.net.URLDecoder.decode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
