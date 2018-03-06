package io.Pushjet.api;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuItem;

import io.Pushjet.api.Async.FirstLaunchAsync;
import io.Pushjet.api.Async.GCMRegistrar;
import io.Pushjet.api.Async.ReceivePushAsync;
import io.Pushjet.api.Async.ReceivePushCallback;
import io.Pushjet.api.PushjetApi.PushjetApi;
import io.Pushjet.api.PushjetApi.PushjetMessage;

import java.util.ArrayList;
import java.util.Arrays;

public class PushListActivity extends Activity {
    private PushjetApi api;
    private DatabaseHandler db;
    private PushListAdapter adapter;
    private BroadcastReceiver receiver;
    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;

    private SwipeRefreshLayout.OnRefreshListener refreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            ReceivePushAsync receivePushAsync = new ReceivePushAsync(api, adapter);
            receivePushAsync.setCallBack(new ReceivePushCallback() {
                @Override
                public void receivePush(ArrayList<PushjetMessage> msg) {
                    refreshLayout.setRefreshing(false);
                }
            });
            refreshLayout.setRefreshing(true);
            receivePushAsync.execute();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_list);

        this.api = new PushjetApi(getApplicationContext(), SettingsActivity.getRegisterUrl(this));
        this.db = new DatabaseHandler(getApplicationContext());
        this.refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        this.refreshLayout.setEnabled(true);
        this.refreshLayout.setOnRefreshListener(refreshListener);
        this.recyclerView = (RecyclerView) findViewById(R.id.push_list);
        this.recyclerView.setHasFixedSize(true);
        this.layoutManager = new LinearLayoutManager(this);
        this.recyclerView.setLayoutManager(this.layoutManager);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean firstLaunch = preferences.getBoolean("first_launch", true);
        if(firstLaunch) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("first_launch", false);
            editor.apply();

            new FirstLaunchAsync().execute(getApplicationContext());
        }

        adapter = new PushListAdapter(
                this, new ArrayList<PushjetMessage>(Arrays.asList(db.getAllMessages())));
        recyclerView.setAdapter(adapter);

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(
                recyclerView.getContext(), LinearLayoutManager.VERTICAL);
        recyclerView.addItemDecoration(mDividerItemDecoration);


        GCMRegistrar mGCMRegistrar = new GCMRegistrar(getApplicationContext());
        if (firstLaunch || mGCMRegistrar.shouldRegister()) {
            if (mGCMRegistrar.checkPlayServices(this)) {
                mGCMRegistrar.registerInBackground(firstLaunch);
            } else {
                finish();
            }
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updatePushList();
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(receiver, new IntentFilter("PushjetMessageRefresh"));
        registerReceiver(receiver, new IntentFilter("PushjetIconDownloaded"));
    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePushList();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void updatePushList() {
        adapter.upDateEntries(new ArrayList<PushjetMessage>(Arrays.asList(db.getAllMessages())));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.push_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        Intent intent;
        switch (id) {
            case R.id.action_subscriptions:
                intent = new Intent(getApplicationContext(), SubscriptionsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_settings:
                intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
