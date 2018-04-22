package io.Pushjet.api;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import io.Pushjet.api.Async.AddServiceAsync;
import io.Pushjet.api.Async.DeleteServiceAsync;
import io.Pushjet.api.Async.GenericAsyncCallback;
import io.Pushjet.api.Async.RefreshServiceAsync;
import io.Pushjet.api.Async.RefreshServiceCallback;
import io.Pushjet.api.PushjetApi.PushjetApi;
import io.Pushjet.api.PushjetApi.PushjetException;
import io.Pushjet.api.PushjetApi.PushjetService;
import io.Pushjet.api.PushjetApi.PushjetUri;

import java.util.ArrayList;
import java.util.Arrays;

@SuppressWarnings("FieldCanBeLocal")
public class SubscriptionsActivity extends AppCompatActivity {
    private PushjetApi api;
    private DatabaseHandler db;
    private SubscriptionsAdapter adapter;
    private BroadcastReceiver receiver;
    private SwipeRefreshLayout refreshLayout;
    private SwipeRefreshLayout.OnRefreshListener refreshListener = new SwipeRefreshLayout.OnRefreshListener() {
        @Override
        public void onRefresh() {
            refreshServices();
        }
    };
    private RecyclerView recyclerView;
    private RecyclerView.LayoutManager layoutManager;
    private FloatingActionButton fabMain;
    private LinearLayout fabText;
    private LinearLayout fabQr;
    private boolean fabMenuOpen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subscriptions);
        this.refreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_container);
        this.refreshLayout.setEnabled(true);
        this.refreshLayout.setOnRefreshListener(refreshListener);
        this.recyclerView = (RecyclerView) findViewById(R.id.subscriptions);
        this.recyclerView.setHasFixedSize(true);
        this.layoutManager = new LinearLayoutManager(this);
        this.recyclerView.setLayoutManager(this.layoutManager);

        this.api = new PushjetApi(getApplicationContext(), SettingsActivity.getRegisterUrl(this));
        this.db = new DatabaseHandler(getApplicationContext());

        adapter = new SubscriptionsAdapter(this);
        recyclerView.setAdapter(adapter);

        adapter.upDateEntries(new ArrayList<>(Arrays.asList(db.getAllServices())));
        registerForContextMenu(findViewById(R.id.subscriptions));

        DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(
                recyclerView.getContext(), LinearLayoutManager.VERTICAL);
        recyclerView.addItemDecoration(mDividerItemDecoration);

        Uri pushjetUri = getIntent().getData();
        if (pushjetUri != null) {
            try {
                String host = pushjetUri.getHost();
                Log.d("PushjetService", "Host: " + host);
                parseTokenOrUri(host);
            } catch (PushjetException e) {
                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (NullPointerException ignore) {
            }
        }

        if (adapter.getItemCount() == 0 && !this.refreshLayout.isRefreshing()) {
            refreshServices();
        }

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                adapter.upDateEntries(new ArrayList<>(Arrays.asList(db.getAllServices())));
            }
        };

        /* Prepare FloatingActionButtons and add OnClickListeners */

        this.fabMain = (FloatingActionButton) findViewById(R.id.fab);
        this.fabText = (LinearLayout) findViewById(R.id.fab_text_container);
        this.fabQr = (LinearLayout) findViewById(R.id.fab_qr_container);

        fabMenuOpen = false;
        final Activity thisActivity = this;

        fabMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFabMenu();
            }
        });
        fabText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(thisActivity);
                builder.setTitle("Public token");
                final EditText input = new EditText(thisActivity);

                input.setInputType(InputType.TYPE_CLASS_TEXT);
                builder.setView(input);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            parseTokenOrUri(input.getText().toString());
                        } catch (PushjetException e) {
                            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        } catch (NullPointerException ignore) {
                        }

                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
                toggleFabMenu();
            }
        });
        fabQr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IntentIntegrator integrator = new IntentIntegrator(thisActivity);
                integrator.setOrientationLocked(false);
                integrator.initiateScan(IntentIntegrator.QR_CODE_TYPES);
                toggleFabMenu();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(receiver, new IntentFilter("PushjetIconDownloaded"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(receiver);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.subscriptions_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        PushjetService service = (PushjetService) adapter.getSelectedItem();
        switch (item.getItemId()) {
            case R.id.action_copy_token:
                MiscUtil.WriteToClipboard(service.getToken(), "Pushjet Token", this);
                Toast.makeText(this, "Copied token to clipboard", Toast.LENGTH_SHORT).show();
                return true;
            case R.id.action_delete:
                DeleteServiceAsync deleteServiceAsync = new DeleteServiceAsync(api, db);
                deleteServiceAsync.setCallback(new GenericAsyncCallback() {
                    @Override
                    public void onComplete(Object... objects) {
                        adapter.upDateEntries(new ArrayList<>(Arrays.asList(db.getAllServices())));
                    }
                });
                deleteServiceAsync.execute(service);
                return true;
            case R.id.action_clear_notifications:
                db.cleanService(service);
                sendBroadcast(new Intent("PushjetMessageRefresh"));
                return true;
            case R.id.action_share:
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT, "pjet://" + service.getToken() + "/");
                startActivity(Intent.createChooser(share, "Share service"));
                return true;
            case R.id.action_show_qr:
                try {
                    BitMatrix matrix = new QRCodeWriter().encode(service.getToken(), BarcodeFormat.QR_CODE, 512, 512);

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    View view = getLayoutInflater().inflate(R.layout.dialog_display_qr, null);

                    ImageView image = (ImageView) view.findViewById(R.id.image_qr);
                    image.setImageBitmap(new BarcodeEncoder().createBitmap(matrix));

                    builder.setView(view).show();
                } catch (WriterException e) {
                    Toast.makeText(getApplicationContext(), "Couldn't generate qr code", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void parseTokenOrUri(String token) throws PushjetException {
        token = token.trim();
        if (PushjetUri.isValidUri(token)) {
            try {
                token = PushjetUri.tokenFromUri(token);
            } catch (PushjetException ignore) {
            }
        }
        if (!PushjetUri.isValidToken(token))
            throw new PushjetException("Invalid service id", 2);

        AddServiceAsync addServiceAsync = new AddServiceAsync(api, db, adapter);
        addServiceAsync.execute(token);
    }

    // Used for parsing the QR code scanner result
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        try {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult == null)
                return;
            parseTokenOrUri(scanResult.getContents().trim());
        } catch (PushjetException e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
        } catch (NullPointerException ignore) {
        }
    }

    private void refreshServices() {
        RefreshServiceCallback callback = new RefreshServiceCallback() {
            @Override
            public void onComplete(PushjetService[] services) {
                adapter.upDateEntries(new ArrayList<>(Arrays.asList(services)));
                refreshLayout.setRefreshing(false);
            }
        };
        RefreshServiceAsync refresh = new RefreshServiceAsync(api, db);
        refresh.setCallback(callback);
        refreshLayout.setRefreshing(true);
        refresh.execute();
    }

    private void toggleFabMenu() {
        final Resources res = recyclerView.getResources();
        final int animDuration = 180;
        final float fabElevation = res.getDimension(R.dimen.fab_elevation);
        final float fabHeightNormal = res.getDimension(R.dimen.fab_size);
        final float fabHeightMini = res.getDimension(R.dimen.fab_size_mini);
        final float fabMargin = recyclerView.getResources().getDimension(R.dimen.padding_normal);
        final float fabOffsetText = 0 - (fabHeightNormal + fabHeightMini) / 2 - fabMargin;
        final float fabOffsetQr = fabOffsetText - fabHeightMini - fabMargin;

        if (!fabMenuOpen) {
            int test = fabMain.getHeight();
            fabText.setVisibility(View.VISIBLE);
            fabQr.setVisibility(View.VISIBLE);

            ArrayList<Animator> animatorList = new ArrayList<>();
            ObjectAnimator animFabMainRot = ObjectAnimator.ofFloat(fabMain, "rotation", 135f);
            animatorList.add(animFabMainRot);
            ObjectAnimator animFabMainElev = ObjectAnimator.ofFloat(fabMain, "elevation", fabElevation * 2);
            animatorList.add(animFabMainElev);
            ObjectAnimator animFabTextTrans = ObjectAnimator.ofFloat(fabText, "translationY", fabOffsetText);
            animatorList.add(animFabTextTrans);
            ObjectAnimator animFabTextFade = ObjectAnimator.ofFloat(fabText, "alpha", 0f, 1f);
            animatorList.add(animFabTextFade);
            ObjectAnimator animFabQrTrans = ObjectAnimator.ofFloat(fabQr, "translationY", fabOffsetQr);
            animatorList.add(animFabQrTrans);
            ObjectAnimator animFabQrFade = ObjectAnimator.ofFloat(fabQr, "alpha", 0f, 1f);
            animatorList.add(animFabQrFade);

            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(animatorList);
            animSet.setDuration(animDuration);
            animSet.setInterpolator(new LinearOutSlowInInterpolator());
            animSet.start();

            fabMenuOpen = true;
        } else {
            ArrayList<Animator> animatorList = new ArrayList<>();
            ObjectAnimator animFabMainRot = ObjectAnimator.ofFloat(fabMain, "rotation", 0f);
            animatorList.add(animFabMainRot);
            ObjectAnimator animFabMainElev = ObjectAnimator.ofFloat(fabMain, "elevation", fabElevation);
            animatorList.add(animFabMainElev);
            ObjectAnimator animFabTextTrans = ObjectAnimator.ofFloat(fabText, "translationY", 0f);
            animatorList.add(animFabTextTrans);
            ObjectAnimator animFabTextFade = ObjectAnimator.ofFloat(fabText, "alpha", 1f, 0f);
            animatorList.add(animFabTextFade);
            ObjectAnimator animFabQrTrans = ObjectAnimator.ofFloat(fabQr, "translationY", 0f);
            animatorList.add(animFabQrTrans);
            ObjectAnimator animFabQrFade = ObjectAnimator.ofFloat(fabQr, "alpha", 1f, 0f);
            animatorList.add(animFabQrFade);

            AnimatorSet animSet = new AnimatorSet();
            animSet.playTogether(animatorList);
            animSet.setDuration(animDuration);
            animSet.setInterpolator(new FastOutLinearInInterpolator());
            animSet.start();

            animFabQrTrans.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    fabMain.setCompatElevation(fabElevation);
                    fabText.setVisibility(View.GONE);
                    fabQr.setVisibility(View.GONE);
                    fabMenuOpen = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }
    }
}
