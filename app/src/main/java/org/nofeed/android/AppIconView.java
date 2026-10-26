package org.nofeed.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Local app artwork inside one continuous rounded-square mask. */
final class AppIconView extends View {
    private final Drawable icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path mask = new Path();

    AppIconView(Context context, Drawable drawable) {
        super(context); icon = drawable.mutate();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        mask.reset();
        // A fifth-order superellipse keeps straight sides and soft, continuous
        // corners, matching a launcher mask rather than the APK's raw corners.
        for (int i = 0; i <= 128; i++) {
            double angle = i * Math.PI * 2 / 128;
            double x = Math.cos(angle), y = Math.sin(angle);
            float px = w * .5f * (1 + (float)(Math.signum(x) * Math.pow(Math.abs(x), .4)));
            float py = h * .5f * (1 + (float)(Math.signum(y) * Math.pow(Math.abs(y), .4)));
            if (i == 0) mask.moveTo(px, py); else mask.lineTo(px, py);
        }
        mask.close();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas target = new Canvas(bitmap); icon.setBounds(0, 0, w, h);
        if (icon instanceof AdaptiveIconDrawable) {
            // Preserve the adaptive layers' bounds but apply our own mask once,
            // avoiding a circle nested inside the rounded square.
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable)icon;
            if (adaptive.getBackground() != null) adaptive.getBackground().draw(target);
            if (adaptive.getForeground() != null) adaptive.getForeground().draw(target);
        } else icon.draw(target);
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paint.getShader() != null) canvas.drawPath(mask, paint);
    }
}
