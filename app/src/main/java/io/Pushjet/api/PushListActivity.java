package io.Pushjet.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.Pushjet.api.Async.FirstLaunchAsync;
import io.Pushjet.api.Async.GCMRegistrar;
import io.Pushjet.api.Async.ReceivePushAsync;
import io.Pushjet.api.Async.ReceivePushCallback;
import io.Pushjet.api.PushjetApi.PushjetApi;
import io.Pushjet.api.PushjetApi.PushjetMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;

public class PushListActivity extends AppCompatActivity {
    private PushjetApi api;
    private DatabaseHandler db;
    private PushListAdapter adapter;
    private BroadcastReceiver receiver;
    private SwipeRefreshLayout refreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private Hashtable<Integer, String> viewSections;

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

        adapter = new PushListAdapter(this);
        recyclerView.setAdapter(adapter);

        viewSections = new Hashtable<>();
        SectionDividerDecoration sectionDivider =
                new SectionDividerDecoration(recyclerView, viewSections);
        recyclerView.addItemDecoration(sectionDivider);

        updatePushList();

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

    private void updatePushList() {
        ArrayList<PushjetMessage> allMessages = new ArrayList<>(Arrays.asList(db.getAllMessages()));
        Collections.reverse(allMessages);

        String sectionName;

        for (PushjetMessage msg : allMessages) {
            long diff = MiscUtil.timeDiffInDays(msg.getTimestamp());
            sectionName = getResources().getString(R.string.time_older);
            if (diff < 1)
                sectionName = getResources().getString(R.string.time_today);
            else if (diff < 2)
                sectionName = getResources().getString(R.string.time_yesterday);
            else if (diff < 7)
                sectionName = getResources().getString(R.string.time_this_week);
            else if (diff < 30)
                sectionName = getResources().getString(R.string.time_this_month);

            if (!viewSections.contains(sectionName))
                viewSections.put(allMessages.indexOf(msg), sectionName);
        }
        adapter.upDateEntries(allMessages);
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

    class SectionDividerDecoration extends RecyclerView.ItemDecoration {
        private final View header;
        private final TextView headerText;

        private Drawable mDivider;
        private final Rect mBounds = new Rect();

        private Hashtable<Integer, String> sections;

        /**
         * Creates vertical dividers {@link RecyclerView.ItemDecoration} that can be used to group a
         * {@link LinearLayoutManager} into sections with headers.
         *
         * @param parent   Current view, it will be used to access resources.
         * @param sections Hashtable containing index of the section's first element and the
         *                 section name for the header.
         */
        SectionDividerDecoration(RecyclerView parent, Hashtable<Integer, String> sections) {
            header = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.text_section_header, parent, false);
            headerText = (TextView) header.findViewById(R.id.list_section_header);
            fixLayoutSize(header, parent);

            final TypedArray a = parent.getContext()
                    .obtainStyledAttributes(new int[]{android.R.attr.listDivider});
            mDivider = a.getDrawable(0);
            a.recycle();

            this.sections = sections;
        }

        @Override
        public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
            if (parent.getLayoutManager() == null) {
                return;
            }

            final int left;
            final int right;
            final Resources res = parent.getContext().getResources();
            final int offset = res.getDimensionPixelOffset(R.dimen.icon_bounds) +
                    2 * res.getDimensionPixelOffset(R.dimen.icon_margin);

            if (parent.getClipToPadding()) {
                left = parent.getPaddingLeft() + offset;
                right = parent.getWidth() - parent.getPaddingRight();
            } else {
                left = offset;
                right = parent.getWidth();
            }

            fixLayoutSize(header, parent);
            final int childCount = parent.getChildCount();

            for (int i = 0; i < childCount; i++) {
                final View child = parent.getChildAt(i);
                final int position = parent.getChildAdapterPosition(child);

                if (sections.containsKey(position)) {
                    c.save();
                    c.translate(0, child.getTop() - header.getHeight());
                    headerText.setText(sections.get(position));
                    header.draw(c);
                    c.restore();

                } else {
                    c.save();
                    parent.getDecoratedBoundsWithMargins(child, mBounds);
                    final int bottom = mBounds.top;
                    final int top = bottom - mDivider.getIntrinsicHeight();
                    mDivider.setBounds(left, top, right, bottom);
                    mDivider.draw(c);
                    c.restore();
                }
            }
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                                   RecyclerView.State state) {
            if (sections.containsKey(parent.getChildAdapterPosition(view)))
                outRect.top = header.getHeight();
            else
                outRect.top = mDivider.getIntrinsicHeight();
        }

        /**
         * Measure and layout {@link View} to measures of a {@link ViewGroup} to make sure its size
         * is larger than 0.
         *
         * @param view   View to layout.
         * @param parent ViewGroup to layout to.
         *
         * @see "https://yoda.entelect.co.za/view/9627/how-to-android-recyclerview-item-decorations"
         */
        void fixLayoutSize(View view, ViewGroup parent) {
            // Check if the view has a layout parameter, if not create one.
            if (view.getLayoutParams() == null) {
                view.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            int widthSpec = View.MeasureSpec.makeMeasureSpec(parent.getWidth(),
                    View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(parent.getHeight(),
                    View.MeasureSpec.UNSPECIFIED);

            int childWidth = ViewGroup.getChildMeasureSpec(widthSpec,
                    parent.getPaddingLeft() + parent.getPaddingRight(),
                    view.getLayoutParams().width);
            int childHeight = ViewGroup.getChildMeasureSpec(heightSpec,
                    parent.getPaddingTop() + parent.getPaddingBottom(),
                    view.getLayoutParams().height);

            view.measure(childWidth, childHeight);
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        }
    }
}
