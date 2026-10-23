package org.nofeed.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

final class SearchLauncher {
    static void launch(Activity activity, LocalStore store, SearchTarget target, String query, Runnable done) {
        if (target == SearchTarget.WECHAT) {
            WechatSearchService.launch(activity, query);
            return;
        }
        java.util.LinkedHashSet<String> templates = new java.util.LinkedHashSet<>();
        templates.add(target.appTemplate);
        java.util.Collections.addAll(templates, target.fallbackTemplates());
        for (String candidate : templates) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SearchTarget.buildUrl(candidate, query)))
                    .setPackage(target.packageName);
            activity.startActivity(intent);
            store.record(target, query, false);
            done.run();
            return;
        } catch (ActivityNotFoundException | SecurityException e) {
            // Some platform filters resolve to private activities. Try another
            // public entry point; never attempt to launch a private component.
        } catch (IllegalArgumentException e) {
            if (candidate.equals(target.appTemplate)) {
                new AlertDialog.Builder(activity).setMessage(e.getMessage()).setPositiveButton("知道了", null).show();
                return;
            }
        }
        }
            new AlertDialog.Builder(activity).setTitle("无法直达" + target.label)
                    .setMessage("可能尚未安装应用，或当前版本不支持这条搜索链接。可以改用网页版搜索。")
                    .setNegativeButton("返回", null).setPositiveButton("用网页版搜索", (dialog, which) -> {
                        try {
                            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SearchTarget.buildUrl(target.webTemplate, query)))
                                    .addCategory(Intent.CATEGORY_BROWSABLE));
                            store.record(target, query, true); done.run();
                        } catch (ActivityNotFoundException | SecurityException err) {
                            new AlertDialog.Builder(activity).setMessage("没有可用的浏览器，请先安装浏览器。").setPositiveButton("知道了", null).show();
                        }
                    }).show();
    }
}
