package io.Pushjet.api;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Map;

import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

public class PushjetFcmService extends FirebaseMessagingService {
    private static final String TAG = "PushjetFCM";
    private static int NOTIFICATION_ID = 0;

    @Override
    public void onMessageReceived(RemoteMessage message){
        Map data = message.getData();

        if (data.size() > 0) {
            try {
                JSONObject AzMsg = new JSONObject((String) data.get("message"));
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
                NotificationUtil.sendNotification(msg, notifyID, this);

            } catch (JSONException e) {
                Log.e("PushjetJson", e.getMessage());
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        Log.d(TAG, "Refreshed token: " + token);
        getSharedPreferences("_", MODE_PRIVATE).edit().putString("fcm_token", token).apply();
    }

    // https://stackoverflow.com/a/41515597
    public static String getToken(Context context) {
        String token = context.getSharedPreferences("_", MODE_PRIVATE).getString("fcm_token", "");

        return (token.isEmpty()) ? getInstanceIdToken(context) : token;
    }

    private static String getInstanceIdToken(final Context context) {

        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String token = task.getResult().getToken();

                        context.getSharedPreferences("_", Context.MODE_PRIVATE).edit()
                                .putString("fcm_token", token).apply();
                    }
                });

        return context.getSharedPreferences("_", MODE_PRIVATE).getString("fcm_token", "");
    }
}
