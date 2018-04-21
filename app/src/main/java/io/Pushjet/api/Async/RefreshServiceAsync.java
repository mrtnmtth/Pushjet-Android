package io.Pushjet.api.Async;

import android.os.AsyncTask;

import io.Pushjet.api.DatabaseHandler;
import io.Pushjet.api.PushjetApi.PushjetApi;
import io.Pushjet.api.PushjetApi.PushjetException;
import io.Pushjet.api.PushjetApi.PushjetService;


public class RefreshServiceAsync extends AsyncTask<Void, Void, PushjetService[]> {
    private DatabaseHandler db;
    private PushjetApi api;
    private RefreshServiceCallback callback;

    public RefreshServiceAsync(PushjetApi api, DatabaseHandler db) {
        this.api = api;
        this.db = db;
        this.callback = null;
    }

    public void setCallback(RefreshServiceCallback callback) {
        this.callback = callback;
    }


    @Override
    protected PushjetService[] doInBackground(Void... voids) {
        try {
            PushjetService[] subscription = this.api.listSubscriptions();
            db.refreshServices(subscription);
            return subscription;
        } catch (PushjetException e) {
            e.printStackTrace();
        }
        return new PushjetService[0];
    }

    @Override
    protected void onPostExecute(PushjetService[] services) {
        super.onPostExecute(services);
        if (callback != null)
            callback.onComplete(services);
    }
}
