package io.Pushjet.api;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

public class NotificationUtil {
    /**
     * Creates a notification channel group for the given service with five notification channels
     * mapping to the pushjet message importance levels.
     *
     * @param service the pushjet service that the channels will be created for.
     * @param context the application context.
     */
    public static void createServiceChannels(PushjetService service, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = service.getToken();
            CharSequence name = service.getName();

            int[] importance = new int[5];
            importance[0] = NotificationManager.IMPORTANCE_NONE;
            importance[1] = NotificationManager.IMPORTANCE_MIN;
            importance[2] = NotificationManager.IMPORTANCE_LOW;
            importance[3] = NotificationManager.IMPORTANCE_DEFAULT;
            importance[4] = NotificationManager.IMPORTANCE_HIGH;

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.createNotificationChannelGroup(new NotificationChannelGroup(id, name));

                for (int i = 0; i < 5; i++) {
                    String level = Integer.toString(i + 1);
                    NotificationChannel channel = new NotificationChannel(id + "##" + level,
                            "Message level " + level, importance[i]);
                    channel.setDescription("Messages from service " + name.toString().toUpperCase() + " with importance level " + level);
                    channel.setGroup(id);

                    notificationManager.createNotificationChannel(channel);
                }
            }
        }
    }

    /**
     * Deletes the notification channel group and all its notification channels for the given
     * service.
     *
     * @param service the pushjet service that's notification channels will be deleted.
     * @param context the application context.
     */
    public static void deleteServiceChannels(PushjetService service, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String id = service.getToken();
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);

            if (notificationManager != null) {
                notificationManager.deleteNotificationChannelGroup(id);
            }
        }
    }

    public static void sendNotification(PushjetMessage msg, int notifyID, Context context) {
        sendNotification(msg, notifyID, null, context);

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
            URL url;
            try {
                url = new URL(link);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                String contentType = conn.getHeaderField("Content-Type");

                if (contentType.startsWith("image/")) {
                    Bitmap image = Picasso.with(context.getApplicationContext())
                            .load(link)
                            .resize(width, height)
                            .centerCrop()
                            .onlyScaleDown()
                            .get();
                    NotificationUtil.sendNotification(msg, notifyID, image, context);
                }
            } catch (IOException e) {
                // image download has failed | link does not reference image file
                Log.e("PushjetLink", e.getMessage());
            }
        }
    }

    private static void sendNotification(PushjetMessage msg, int notifyID, Bitmap bigPicture,
                                         Context context) {
        NotificationManager mNotificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = msg.getService().getToken() + "##" + msg.getLevel();

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(R.drawable.ic_stat_notif)
                        .setContentTitle(msg.getTitleOrName())
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg.getMessage()))
                        .setContentText(msg.getMessage())
                        .setWhen(msg.getTimestamp().getTime())
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true);

        Resources res = context.getResources();
        int widthResId = android.R.dimen.notification_large_icon_width;
        int heightResId = android.R.dimen.notification_large_icon_height;
        PushjetService service = msg.getService();

        if (service.hasIcon()) {
            try {
                @SuppressWarnings("SuspiciousNameCombination")
                Bitmap icon = Picasso.with(context.getApplicationContext())
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

        Intent openAppIntent = new Intent(context, PushListActivity.class);
        PendingIntent piOpenApp = PendingIntent.getActivity(context, 0, openAppIntent, 0);
        mBuilder.setContentIntent(piOpenApp);

        if (msg.hasLink() && bigPicture == null && !msg.getLink().startsWith("geo:")) {
            try {
                Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
                PendingIntent piOpenLink = PendingIntent.getActivity(context, 0, openLinkIntent, 0);
                mBuilder.addAction(R.drawable.ic_action_open_link,
                        context.getString(R.string.notification_open_link), piOpenLink);
            } catch (Exception ignore) {
            }
        }

        // if link is a geo URI create map intent
        if (msg.hasLink() && msg.getLink().startsWith("geo:")) {
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg.getLink()));
            PendingIntent piMap = PendingIntent.getActivity(context, 0, mapIntent, 0);
            mBuilder.addAction(R.drawable.ic_action_map,
                    context.getString(R.string.notification_open_map), piMap);
        }

        if (mNotificationManager != null) {
            mNotificationManager.notify(notifyID, mBuilder.build());
        }
    }

    private static void setPriority(NotificationCompat.Builder mBuilder, PushjetMessage msg) {int priority = msg.getLevel() - 3;
        if(Math.abs(priority) > 2) {
            priority = 0;
        }

        mBuilder.setPriority(priority);
    }

}
