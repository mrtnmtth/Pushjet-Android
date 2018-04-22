package io.Pushjet.api;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import io.Pushjet.api.PushjetApi.PushjetException;
import io.Pushjet.api.PushjetApi.PushjetMessage;
import io.Pushjet.api.PushjetApi.PushjetService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SuppressWarnings("WeakerAccess")
public class DatabaseHandler extends SQLiteOpenHelper {
    private String TABLE_SUBSCRIPTION = "subscription";
    private String KEY_SUBSCRIPTION_TOKEN = "service";
    private String KEY_SUBSCRIPTION_SECRET = "secret";
    private String KEY_SUBSCRIPTION_NAME = "name";
    private String KEY_SUBSCRIPTION_ICON = "icon";
    private String KEY_SUBSCRIPTION_TIMESTAMP = "timestamp";
    private String[] TABLE_SUBSCRIPTION_KEYS = new String[]{KEY_SUBSCRIPTION_TOKEN, KEY_SUBSCRIPTION_SECRET, KEY_SUBSCRIPTION_NAME, KEY_SUBSCRIPTION_ICON, KEY_SUBSCRIPTION_TIMESTAMP};

    private String TABLE_MESSAGE = "messages";
    private String KEY_MESSAGE_ID = "id";
    private String KEY_MESSAGE_SERVICE = "service";
    private String KEY_MESSAGE_TEXT = "text";
    private String KEY_MESSAGE_TITLE = "title";
    private String KEY_MESSAGE_LEVEL = "level";
    private String KEY_MESSAGE_TIMESTAMP = "timestamp";
    private String KEY_MESSAGE_LINK = "link";
    private String[] TABLE_MESSAGE_KEYS = new String[]{KEY_MESSAGE_ID, KEY_MESSAGE_SERVICE, KEY_MESSAGE_TEXT, KEY_MESSAGE_TITLE, KEY_MESSAGE_LEVEL, KEY_MESSAGE_TIMESTAMP, KEY_MESSAGE_LINK};

    public DatabaseHandler(Context context) {
        super(context, "Pushjet", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE_SUBSCRIPTION = "CREATE TABLE `" + TABLE_SUBSCRIPTION + "` (" +
                "`" + KEY_SUBSCRIPTION_TOKEN + "` VARCHAR PRIMARY KEY, " +
                "`" + KEY_SUBSCRIPTION_SECRET + "` VARCHAR, " +
                "`" + KEY_SUBSCRIPTION_NAME + "` VARCHAR," +
                "`" + KEY_SUBSCRIPTION_ICON + "` VARCHAR," +
                "`" + KEY_SUBSCRIPTION_TIMESTAMP + "` INTEGER)";

        String CREATE_TABLE_MESSAGE = "CREATE TABLE `" + TABLE_MESSAGE + "` (" +
                "`" + KEY_MESSAGE_ID + "` INTEGER PRIMARY KEY AUTOINCREMENT," +
                "`" + KEY_MESSAGE_SERVICE + "` VARCHAR, " +
                "`" + KEY_MESSAGE_TEXT + "` TEXT, " +
                "`" + KEY_MESSAGE_TITLE + "` VARCHAR, " +
                "`" + KEY_MESSAGE_LEVEL + "` INT," +
                "`" + KEY_MESSAGE_TIMESTAMP + "` INTEGER," +
                "`" + KEY_MESSAGE_LINK + "` VARCHAR)";

        db.execSQL(CREATE_TABLE_SUBSCRIPTION);
        db.execSQL(CREATE_TABLE_MESSAGE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion < 3) {
            db.execSQL("ALTER TABLE listen RENAME TO " + TABLE_SUBSCRIPTION);
        }
    }

    public PushjetMessage[] getAllMessages() {
        PushjetService[] services = this.getAllServices();
        SQLiteDatabase db = this.getReadableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            List<PushjetMessage> result = new ArrayList<>();
            Cursor cMsg = db.query(TABLE_MESSAGE, TABLE_MESSAGE_KEYS, null, null, null, null, null);
            if (cMsg.getCount() > 0 && cMsg.moveToFirst()) {
                do {
                    result.add(getMessageFromRow(cMsg, services));
                } while (cMsg.moveToNext());
            }
            PushjetMessage[] ret = new PushjetMessage[result.size()];
            return result.toArray(ret);
        } finally {
            db.close();
        }
    }

    public void addMessage(PushjetMessage msg) {
        ContentValues vMsg = new ContentValues();
        vMsg.put(KEY_MESSAGE_SERVICE, msg.getService().getToken());
        vMsg.put(KEY_MESSAGE_TEXT, msg.getMessage());
        vMsg.put(KEY_MESSAGE_LEVEL, msg.getLevel());
        vMsg.put(KEY_MESSAGE_TITLE, msg.getTitle());
        vMsg.put(KEY_MESSAGE_LINK, msg.getLink());
        vMsg.put(KEY_MESSAGE_TIMESTAMP, Math.round(msg.getTimestamp().getTime() / 1000L));

        SQLiteDatabase db = this.getWritableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            db.insert(TABLE_MESSAGE, null, vMsg);

            if (!isListening(msg.getService())) {
                PushjetService srv = new PushjetService(msg.getService().getToken(), "UNKNOWN");
                addService(srv);
            }
        } finally {
            db.close();
        }
    }

    public void cleanService(PushjetService service) {
        if (isListening(service)) {
            SQLiteDatabase db = this.getWritableDatabase();
            String fmt = "`%s` = '%s'";

            //noinspection TryFinallyCanBeTryWithResources
            try {
                db.delete(TABLE_MESSAGE, String.format(fmt, KEY_MESSAGE_SERVICE, service.getToken()), null);
            } finally {
                db.close();
            }
        }
    }

    public void addService(PushjetService service) {
        addServices(new PushjetService[]{service});
    }

    public void removeService(PushjetService service) {
        if (isListening(service)) {
            SQLiteDatabase db = this.getWritableDatabase();

            String fmt = "`%s` = '%s'";
            db.delete(TABLE_MESSAGE, String.format(fmt, KEY_MESSAGE_SERVICE, service.getToken()), null);
            db.delete(TABLE_SUBSCRIPTION, String.format(fmt, KEY_SUBSCRIPTION_TOKEN, service.getToken()), null);
            db.close();
        }
    }

    public void deleteMessage(PushjetMessage message) {
        SQLiteDatabase db = this.getWritableDatabase();
        String fmt = "`%s` = '%s'";

        //noinspection TryFinallyCanBeTryWithResources
        try {
            db.delete(TABLE_MESSAGE, String.format(fmt, KEY_MESSAGE_ID, message.getId()), null);
        } finally {
            db.close();
        }
    }

    public PushjetMessage getMessage(int id) throws PushjetException {
        SQLiteDatabase db = this.getReadableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            Cursor cMsg = db.query(TABLE_MESSAGE, TABLE_MESSAGE_KEYS, KEY_MESSAGE_ID + " = ?", new String[]{id + ""}, null, null, null);
            if (cMsg.getCount() > 0 && cMsg.moveToFirst())
                return getMessageFromRow(cMsg);
            throw new PushjetException("Message not found", 400);
        } finally {
            db.close();
        }
    }

    public void addServices(PushjetService[] services) {
        SQLiteDatabase db = this.getWritableDatabase();
        try {
            for (PushjetService service : services) {
                boolean subscribed = isListening(service);
                if (!db.isOpen()) db = this.getWritableDatabase();

                if (subscribed) {
                    String fmt = "`%s` = '%s'";
                    db.delete(TABLE_SUBSCRIPTION, String.format(fmt, KEY_SUBSCRIPTION_TOKEN, service.getToken()), null);
                }

                ContentValues vSrv = new ContentValues();
                vSrv.put(KEY_SUBSCRIPTION_TOKEN, service.getToken());
                vSrv.put(KEY_SUBSCRIPTION_NAME, service.getName());
                vSrv.put(KEY_SUBSCRIPTION_SECRET, service.getSecret());
                vSrv.put(KEY_SUBSCRIPTION_ICON, service.getIcon());
                vSrv.put(KEY_SUBSCRIPTION_TIMESTAMP, Math.round(service.getCreated().getTime() / 1000L));

                db.insert(TABLE_SUBSCRIPTION, null, vSrv);
            }
        } finally {
            db.close();
        }
    }

    public int getServiceCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        String countQuery = "SELECT  * FROM " + TABLE_SUBSCRIPTION;

        //noinspection TryFinallyCanBeTryWithResources
        try {
            return db.rawQuery(countQuery, null).getCount();
        } finally {
            db.close();
        }
    }

    public PushjetService getService(String token) {
        SQLiteDatabase db = this.getReadableDatabase();
        PushjetService srv;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            Cursor cLsn = db.query(TABLE_SUBSCRIPTION, TABLE_SUBSCRIPTION_KEYS, KEY_SUBSCRIPTION_TOKEN + " = ?", new String[]{token}, null, null, null);
            cLsn.moveToFirst();
            srv = new PushjetService(
                    cLsn.getString(0),
                    cLsn.getString(2),
                    cLsn.getString(3),
                    cLsn.getString(1),
                    new Date((long) cLsn.getInt(4) * 1000)
            );
            cLsn.close();
        } catch (CursorIndexOutOfBoundsException ignore) {
            srv = new PushjetService(token, "UNKNOWN");
        } finally {
            db.close();
        }
        return srv;
    }

    public void refreshServices(PushjetService[] services) {
        this.addServices(services);

        PushjetService[] subscribed = this.getAllServices();
        for (PushjetService l1 : subscribed) {
            boolean rm = true;
            for (PushjetService l2 : services) {
                if (l1.getToken().equals(l2.getToken())) {
                    rm = false; break;
                }
            }

            if (rm) this.removeService(l1);
        }
    }

    public boolean isListening(PushjetService service) {
        return this.isListening(service.getToken());
    }

    public boolean isListening(String service) {
        SQLiteDatabase db = this.getReadableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            Cursor cursor = db.query(TABLE_SUBSCRIPTION, TABLE_SUBSCRIPTION_KEYS, KEY_SUBSCRIPTION_TOKEN + " = ?", new String[]{service}, null, null, null);
            int count = cursor.getCount();
            cursor.close();
            return count > 0;
        } finally {
            db.close();
        }
    }

    public PushjetService[] getAllServices() {
        SQLiteDatabase db = this.getReadableDatabase();
        List<PushjetService> result = new ArrayList<>();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            Cursor cSrv = db.query(TABLE_SUBSCRIPTION, TABLE_SUBSCRIPTION_KEYS, null, null, null, null, null);
            if (cSrv.getCount() > 0 && cSrv.moveToFirst()) {
                do {
                    result.add(new PushjetService(
                            cSrv.getString(0),
                            cSrv.getString(2),
                            cSrv.getString(3),
                            cSrv.getString(1),
                            new Date((long) cSrv.getInt(4) * 1000)
                    ));
                } while (cSrv.moveToNext());
            }
            cSrv.close();
            return result.toArray(new PushjetService[result.size()]);
        } finally {
            db.close();
        }
    }

    public void truncateMessages() {
        SQLiteDatabase db = this.getReadableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            db.delete(TABLE_MESSAGE, null, null);
        } finally {
            db.close();
        }
    }

    public void truncateServices() {
        SQLiteDatabase db = this.getReadableDatabase();

        //noinspection TryFinallyCanBeTryWithResources
        try {
            db.delete(TABLE_SUBSCRIPTION, null, null);
        } finally {
            db.close();
        }
    }

    private PushjetMessage getMessageFromRow(Cursor cMsg) {
        PushjetService[] srvcs = {};
        return getMessageFromRow(cMsg, srvcs);
    }

    private PushjetMessage getMessageFromRow(Cursor cMsg, PushjetService[] srvs) {
        String token = cMsg.getString(1);
        PushjetService service = null;
        for (PushjetService srv : srvs) {
            if (srv.getToken().equals(token))
                service = srv;
        }
        if (service == null)
            service = getService(token);

        PushjetMessage msg = new PushjetMessage(
                service,
                cMsg.getString(2),
                cMsg.getString(3),
                cMsg.getInt(5)
        );
        msg.setId(cMsg.getInt(0));
        msg.setLevel(cMsg.getInt(4));
        msg.setLink(cMsg.getString(6));

        return msg;
    }
}
