// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

final class LicenseInfo {
    static void show(Activity activity) {
        String notice = "一搜 · AGPL-3.0-only\nCopyright (c) 2026 Yisou contributors\n\n"
                + "本程序可按 GNU AGPL 第 3 版使用、修改与再分发，包括遵守许可的商用。对外分发须提供完整对应源码；修改版的网络交互还须遵守第 13 条。程序不附任何担保。\n\n"
                + "0.3.7 及更早版本已授出的 MIT 权限不因本次变更收回。字体和其他第三方材料保留各自许可。以下为完整许可原文：\n\n";
        try (InputStream input = activity.getResources().openRawResource(R.raw.agpl_3);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
            notice += output.toString("UTF-8");
        } catch (java.io.IOException e) { notice += "完整许可也可在本版本源码的 LICENSE 文件中查看。"; }
        TextView text = new TextView(activity); text.setText(notice); text.setTextSize(13);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setTextColor(Appearance.palette(activity).ink); text.setTextIsSelectable(true);
        int padding = (int)(16 * activity.getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(activity); scroll.addView(text);
        new AlertDialog.Builder(activity).setTitle("开源许可与源码").setView(scroll)
                .setNegativeButton("关闭", null).setPositiveButton("本版本源码", (d, w) -> source(activity)).show();
    }

    private static void source(Activity activity) {
        String url = "https://github.com/oldjie/yisou";
        try {
            String version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            if (version != null) url += "/releases/tag/v" + Uri.encode(version);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (RuntimeException unavailable) {
            new AlertDialog.Builder(activity).setTitle("源码地址").setMessage(url).setPositiveButton("知道了", null).show();
        }
    }
}
