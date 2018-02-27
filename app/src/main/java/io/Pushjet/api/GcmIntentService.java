package io.Pushjet.api;


import android.annotation.TargetApi;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;

public class GcmIntentService extends IntentService {
    private static int NOTIFICATION_ID = 0;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty() && GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType)) {
            try {
                JSONObject AzMsg = new JSONObject(extras.getString("message"));
                JSONObject AzServ = AzMsg.getJSONObject("service");
                PushjetService srv = new PushjetService(
                        AzServ.getString("public"),
                        AzServ.getString("name"),
                        new Date((long) AzServ.getInt("created") * 1000)
                );
                srv.setIcon(AzServ.getString("icon"));

                PushjetMessage msg = new PushjetMessage(
                        srv,
                        AzMsg.getString("message"),
                        AzMsg.getString("title"),
                        AzMsg.getInt("timestamp")
                );
                msg.setLevel(AzMsg.getInt("level"));
                msg.setLink(AzMsg.getString("link"));
                DatabaseHandler db = new DatabaseHandler(this);
                db.addMessage(msg);

                // assign local variable notifyID to prevent race conditions
                int notifyID = NOTIFICATION_ID++;
                sendNotification(msg, notifyID);
                if (msg.hasLink()) {
                    new DownloadLinkImageAsync(notifyID, msg).execute();
                }
            } catch (JSONException ignore) {
                Log.e("PushjetJson", ignore.getMessage());
            }
        }
        GcmBroadcastReceiver.completeWakefulIntent(intent);
        sendBroadcast(new Intent("PushjetMessageRefresh"));
    }

    private void sendNotification(PushjetMessage msg, int notifyID) {
        this.sendNotification(msg, notifyID, null);
    }

    private void sendNotification(PushjetMessage msg, int notifyID, Bitmap bigPicture) {
        NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_notif)
                        .setContentTitle(msg.getTitleOrName())
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg.getMessage()))
                        .setContentText(msg.getMessage())
                        .setWhen(msg.getTimestamp().getTime())
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true);

        if (msg.getService().hasIcon()) {
            try {
                Bitmap icon = msg.getService().getIconBitmap(getApplicationContext());
                Resources res = getApplicationContext().getResources();

                int nHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
                int nWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);

                mBuilder.setLargeIcon(MiscUtil.scaleBitmap(icon, nWidth, nHeight));
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        }

        if (bigPicture != null)
            mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bigPicture));

        setPriority(mBuilder, msg);
        mBuilder.setDefaults(Notification.DEFAULT_ALL);

        Intent openAppIntent = new Intent(this, PushListActivity.class);
        PendingIntent piOpenApp = PendingIntent.getActivity(this, 0, openAppIntent, 0);
        mBuilder.setContentIntent(piOpenApp);

        if (msg.hasLink() && bigPicture == null && !msg.getLink().startsWith("geo:")) {
            try {
                Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
                PendingIntent piOpenLink = PendingIntent.getActivity(this, 0, openLinkIntent, 0);
                mBuilder.addAction(R.drawable.ic_action_open_link,
                        getString(R.string.notification_open_link), piOpenLink);
                mBuilder.addAction(R.drawable.ic_stat_notif,
                        getString(R.string.notification_open_pushjet), piOpenApp);
                mBuilder.setContentIntent(piOpenLink);
            } catch (Exception ignore) {
            }
        }

        // if link is a geo URI create map intent
        if (msg.hasLink() && msg.getLink().startsWith("geo:")) {
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
            PendingIntent piMap = PendingIntent.getActivity(this, 0, mapIntent, 0);
            mBuilder.addAction(R.drawable.ic_action_map,
                    getString(R.string.notification_open_map), piMap);
            mBuilder.addAction(R.drawable.ic_stat_notif,
                    getString(R.string.notification_open_pushjet), piOpenApp);
            mBuilder.setContentIntent(piMap);
        }

        mNotificationManager.notify(notifyID, mBuilder.build());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void setPriority(NotificationCompat.Builder mBuilder, PushjetMessage msg) {
        int priority = msg.getLevel() - 3;
        if(Math.abs(priority) > 2) {
            priority = 0;
        }

        mBuilder.setPriority(priority);
    }

    public class DownloadLinkImageAsync extends AsyncTask<Void, Void, Bitmap> {
        private final int notifyID;
        private final PushjetMessage msg;

        private DownloadLinkImageAsync(int notifyID, PushjetMessage msg) {
            this.notifyID = notifyID;
            this.msg = msg;
        }

        @Override
        protected Bitmap doInBackground(Void... voids) {
            if (!msg.hasLink())
                return null;
            String link = msg.getLink();
            // if link is a geo URI, download Google Maps image
            if (link.startsWith("geo:")) {
                link = "https://maps.googleapis.com/maps/api/staticmap?zoom=16&size=512x300&" +
                        "maptype=roadmap&markers=" + link.substring(4);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Bitmap bitmap = null;
            try {
                HttpGet httpget = new HttpGet(link);
                HttpResponse response = (new DefaultHttpClient()).execute(httpget);
                Header contentType = response.getFirstHeader("Content-Type");
                if (!contentType.getValue().startsWith("image/"))
                    return null;
                response.getEntity().writeTo(out);
                byte[] data = out.toByteArray();
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            } catch (Exception ignore) {
                ignore.printStackTrace();
            } finally {
                try {
                    out.close();
                } catch (IOException ignore) {
                }
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null)
                // update notification
                GcmIntentService.this.sendNotification(this.msg, this.notifyID, bitmap);
        }
    }
}

