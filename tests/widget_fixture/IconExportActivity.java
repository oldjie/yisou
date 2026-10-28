// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import org.json.JSONArray;
import org.json.JSONObject;

/** Maintainer tool: render icon resources from local original APKs; never run their code. */
public final class IconExportActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); TextView result = new TextView(this); setContentView(result);
        try {
            String[] packages = {"com.douban.frodo", "com.taobao.taobao", "com.taobao.idlefish", "com.tencent.mm", "tv.danmaku.bili", "com.xunmeng.pinduoduo", "com.xingin.xhs"};
            String[] names = {"douban", "taobao", "xianyu", "wechat", "bilibili", "pinduoduo", "xiaohongshu"};
            File base = getExternalFilesDir(null), output = new File(base, "icons"); output.mkdirs();
            JSONArray sources = new JSONArray();
            for (int i = 0; i < packages.length; i++) {
                File apk = new File(new File(base, "archives"), packages[i] + ".apk");
                PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
                if (info == null || !packages[i].equals(info.packageName)) throw new IllegalArgumentException("APK identity mismatch");
                info.applicationInfo.sourceDir = apk.getAbsolutePath(); info.applicationInfo.publicSourceDir = apk.getAbsolutePath();
                Drawable icon = getPackageManager().getApplicationIcon(info.applicationInfo);
                File png = new File(output, "platform_"+names[i]+".png");
                try (FileOutputStream file = new FileOutputStream(png)) {
                    if (!AppIconView.render(this, icon, 256).compress(Bitmap.CompressFormat.PNG, 100, file)) throw new IllegalStateException("PNG export failed");
                }
                sources.put(new JSONObject().put("package", packages[i]).put("version", info.versionName)
                        .put("apk_sha256", digest(apk)).put("icon", png.getName()).put("png_sha256", digest(png)));
            }
            try (FileOutputStream file = new FileOutputStream(new File(output, "sources.json"))) { file.write(sources.toString(2).getBytes("UTF-8")); }
            result.setText("ICONS_READY");
        } catch (Exception error) { result.setText("ICON_EXPORT_FAILED: "+error.getClass().getSimpleName()+" "+error.getMessage()); }
    }
    private static String digest(File file) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] block = new byte[65536]; int n; while ((n=input.read(block)) != -1) sha.update(block,0,n);
        }
        StringBuilder result = new StringBuilder(); for (byte b : sha.digest()) result.append(String.format(java.util.Locale.ROOT,"%02x",b & 255)); return result.toString();
    }
}
