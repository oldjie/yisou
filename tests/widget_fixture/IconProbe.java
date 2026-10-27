// SPDX-License-Identifier: AGPL-3.0-only
package org.nofeed.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;

/** Exercises the production renderer with standard layers and an OEM-like wrapper. */
public final class IconProbe {
    public static void verify(Context context) {
        AdaptiveIconDrawable plain = new AdaptiveIconDrawable(new ColorDrawable(Color.GREEN), new ColorDrawable(Color.WHITE));
        Bitmap standard = AppIconView.render(context, plain, 64);
        if (standard.getPixel(32, 32) != Color.WHITE) throw new AssertionError("Standard adaptive layers missing");
        AdaptiveIconDrawable wrapper = new AdaptiveIconDrawable(new ColorDrawable(Color.TRANSPARENT), new ColorDrawable(Color.TRANSPARENT)) {
            @Override public void draw(Canvas canvas) {
                Paint p = new Paint(); p.setColor(Color.GREEN); canvas.drawRect(getBounds(), p);
            }
        };
        Bitmap themed = AppIconView.render(context, wrapper, 64);
        if (themed.getPixel(32, 32) != Color.GREEN) throw new AssertionError("OEM wrapper draw() bypassed");
        Bitmap regular = AppIconView.render(context, new ColorDrawable(Color.RED), 64);
        if (regular.getPixel(32, 32) != Color.RED) throw new AssertionError("Regular icon missing");
    }
}
