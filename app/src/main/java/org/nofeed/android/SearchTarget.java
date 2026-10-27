package org.nofeed.android;

import java.net.URLEncoder;

/** A platform adapter. Add platforms here; UI and dispatch remain generic. */
public enum SearchTarget {
    DOUBAN("豆瓣", "com.douban.frodo", "douban://douban.com/search/result?q={query}", "https://www.douban.com/search?q={query}"),
    TAOBAO("淘宝", "com.taobao.taobao", "tbopen://m.taobao.com/tbopen/index.html?action=ali.open.nav&module=h5&h5Url=https%3A%2F%2Fs.taobao.com%2Fsearch%3Fq%3D{query}", "https://s.taobao.com/search?q={query}"),
    XIANYU("闲鱼", "com.taobao.idlefish", "fleamarket://idle_search?flutter=true&flutter_animated=false&initSubPageTag=searchResult&keyword={query}", "https://www.goofish.com/search?q={query}"),
    WECHAT("微信", "com.tencent.mm", null, null),
    BILIBILI("哔哩哔哩", "tv.danmaku.bili", "bilibili://search?keyword={query}", "https://search.bilibili.com/all?keyword={query}"),
    PINDUODUO("拼多多", "com.xunmeng.pinduoduo", "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={query}", "https://mobile.yangkeduo.com/search_result.html?search_key={query}"),
    XIAOHONGSHU("小红书", "com.xingin.xhs", "xhsdiscover://search/result?keyword={query}", "https://www.xiaohongshu.com/search_result?keyword={query}");

    public final String label, packageName, appTemplate, webTemplate;

    SearchTarget(String label, String packageName, String appTemplate, String webTemplate) {
        this.label = label; this.packageName = packageName;
        this.appTemplate = appTemplate; this.webTemplate = webTemplate;
    }

    public static String buildUrl(String template, String query) {
        if (query.trim().isEmpty()) throw new IllegalArgumentException("请输入关键词");
        if (!template.contains("{query}")) throw new IllegalArgumentException("链接需要包含 {query}");
        try {
            String encoded = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20");
            // Taobao's gateway contains an encoded URL. Escape the keyword once
            // for the inner search URL, then again for the outer h5Url value.
            if (template.contains("h5Url=")) encoded = URLEncoder.encode(encoded, "UTF-8").replace("+", "%20");
            return template.replace("{query}", encoded);
        } catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }

    public String[] fallbackTemplates() {
        if (this == XIANYU) return new String[]{"fleamarket://x_search_items?keyword={query}"};
        return new String[0];
    }

}
