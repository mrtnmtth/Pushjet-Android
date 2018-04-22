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
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.squareup.picasso.Picasso;

import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

@SuppressWarnings("deprecation")
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

        assert extras != null;
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

                final PushjetMessage msg = new PushjetMessage(
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
                final int notifyID = NOTIFICATION_ID++;
                sendNotification(msg, notifyID);

                if (msg.hasLink()) {
                    String link = msg.getLink();
                    int width = 512;
                    int height = 300;

                    // if link is a geo URI, download Google Maps image
                    if (link.startsWith("geo:")) {
                        link = "https://maps.googleapis.com/maps/api/staticmap" +
                                "?zoom=16&size=" + width + "x" + height +
                                "&maptype=roadmap&markers=" + link.substring(4);
                    }

                    // check content type and download with Picasso
                    URL url = new URL(link);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    String contentType = conn.getHeaderField("Content-Type");

                    if (contentType.startsWith("image/")) {
                        Picasso p = Picasso.with(getApplicationContext());
                        p.setIndicatorsEnabled(true);
                        Bitmap image = p.load(link)
                                .resize(width, height)
                                .centerCrop()
                                .onlyScaleDown()
                                .get();
                        sendNotification(msg, notifyID, image);
                    }
                }
            } catch (JSONException ignore) {
                Log.e("PushjetJson", ignore.getMessage());
            } catch (IOException e) {
                // image download has failed | link does not reference image file
                Log.e("PushjetLink", e.getMessage());
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

        Context context = getApplicationContext();
        Resources res = context.getResources();
        int widthResId = android.R.dimen.notification_large_icon_width;
        int heightResId = android.R.dimen.notification_large_icon_height;
        PushjetService service = msg.getService();

        if (service.hasIcon()) {
            try {
                @SuppressWarnings("SuspiciousNameCombination")
                Bitmap icon = Picasso.with(context)
                        .load(service.getIconUri())
                        .placeholder(service.getIconPlaceholder(context))
                        .resizeDimen(widthResId, heightResId)
                        .centerInside()
                        .onlyScaleDown()
                        .get();

                mBuilder.setLargeIcon(icon);
            } catch (IOException ignore) {
                ignore.printStackTrace();
            }
        } else {
            int width = res.getDimensionPixelSize(widthResId);
            int height = res.getDimensionPixelSize(heightResId);
            Bitmap icon = MiscUtil.bitmapFromDrawable(service.getIconPlaceholder(context),
                    width, height);
            mBuilder.setLargeIcon(icon);
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
}

