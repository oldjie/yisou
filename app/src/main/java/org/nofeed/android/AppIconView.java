package org.nofeed.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Rect;
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

    static Bitmap render(Context context, Drawable drawable, int size) {
        AppIconView view = new AppIconView(context, drawable);
        view.layout(0, 0, size, size);
        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(result));
        return result;
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
        if (icon.getClass() == AdaptiveIconDrawable.class) {
            // Preserve Android's expanded adaptive bounds so the artwork keeps
            // its launcher scale. Repair an empty layer without shrinking the
            // foreground into its resource's safe-zone a second time.
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable)icon;
            drawLayer(adaptive.getBackground(), target, w, h);
            drawLayer(adaptive.getForeground(), target, w, h);
        } else {
            // OEM subclasses may draw themed artwork in their own draw()
            // rather than in the public foreground/background layers.
            icon.draw(target);
        }
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }

    private static void drawLayer(Drawable layer, Canvas canvas, int w, int h) {
        if (layer == null) return;
        Rect previous = new Rect(layer.getBounds());
        if (previous.isEmpty()) layer.setBounds(0, 0, w, h);
        layer.draw(canvas); layer.setBounds(previous);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paint.getShader() != null) canvas.drawPath(mask, paint);
    }
}
