package io.Pushjet.api;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.google.zxing.common.BitMatrix;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

public class MiscUtil {

    public static String hash(String s) {
        return hash(s, "MD5");
    }

    public static String hash(String s, String hash) {
        MessageDigest m;

        try {
            m = MessageDigest.getInstance(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        m.update(s.getBytes(), 0, s.length());
        return new BigInteger(1, m.digest()).toString(16);
    }

    public static String iconFilename(String url) {
        return String.format("icon_%s", hash(url));
    }

    public static Bitmap matrixToBitmap(BitMatrix matrix) {
        int height = matrix.getHeight();
        int width = matrix.getWidth();
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    // Stolen from org.thoughtcrime.securesms.util.BitmapUtil
    public static Bitmap bitmapFromDrawable(final Drawable drawable, final int width, final int height) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        int canvasWidth = drawable.getIntrinsicWidth();
        if (canvasWidth <= 0) canvasWidth = width;

        int canvasHeight = drawable.getIntrinsicHeight();
        if (canvasHeight <= 0) canvasHeight = height;

        Bitmap bitmap;

        try {
            bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        } catch (Exception e) {
            bitmap = null;
        }

        return bitmap;
    }

    public static long timeDiffInDays(Date date) {
        long time = date.getTime();
        long now = System.currentTimeMillis();
        return (now - time) / (1000 * 3600 * 24);
    }

    public static void WriteToClipboard(String data, String title, Context ctx) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB) {
            android.text.ClipboardManager clipboard = (android.text.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setText(data);
        } else {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(title, data);
            clipboard.setPrimaryClip(clip);
        }
    }
}
