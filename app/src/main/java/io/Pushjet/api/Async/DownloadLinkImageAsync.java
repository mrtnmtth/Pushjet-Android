package io.Pushjet.api.Async;


import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.app.NotificationCompat;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import io.Pushjet.api.MiscUtil;
import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.R;

public class DownloadLinkImageAsync extends AsyncTask<Void, Void, Bitmap> {
    private Context context;
    private int notifyID;
    private PushjetMessage msg;

    public DownloadLinkImageAsync(Context context, int notifyID, PushjetMessage msg) {
        this.context = context;
        this.notifyID = notifyID;
        this.msg = msg;
    }

    @Override
    protected Bitmap doInBackground(Void... voids) {
        if (!msg.hasLink())
            return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            HttpGet httpget = new HttpGet(msg.getLink());
            HttpResponse response = (new DefaultHttpClient()).execute(httpget);
            Header contentType = response.getFirstHeader("Content-Type");
            if (!contentType.getValue().startsWith("image/"))
                return null;
            response.getEntity().writeTo(out);
            byte[] data = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            return bitmap;
        } catch (Exception ignore) {
            ignore.printStackTrace();
        } finally {
            try {
                out.close();
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Bitmap bitmap) {
        if (bitmap == null)
            return;

        NotificationManager mNotificationManager = (NotificationManager)
                this.context.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_stat_notif)
                        .setContentTitle(msg.getTitleOrName())
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg.getMessage()))
                        .setContentText(msg.getMessage())
                        .setAutoCancel(true);

        if (msg.getService().hasIcon()) {
            try {
                Bitmap icon = msg.getService().getIconBitmap(context);
                Resources res = context.getResources();

                int nHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
                int nWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

                mBuilder.setLargeIcon(MiscUtil.scaleBitmap(icon, nWidth, nHeight));
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }

        mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap));

        mNotificationManager.notify(notifyID, mBuilder.build());
    }
}
