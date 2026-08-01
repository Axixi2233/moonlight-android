package com.limelight;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.AppGridAdapter;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.AdapterFragment;
import com.limelight.ui.AdapterFragmentCallbacks;
import com.limelight.ui.AppActionsDialog;
import com.limelight.ui.AppDialog;
import com.limelight.ui.gamemenu.GameDisplayFragment;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

public class AppView extends Activity implements AdapterFragmentCallbacks {
    private AppGridAdapter appGridAdapter;
    private String uuidString;
    private ShortcutHelper shortcutHelper;

    private ComputerDetails computer;
    private ComputerManagerService.ApplistPoller poller;
    private AlertDialog blockingLoadDialog;
    private String lastRawApplist;
    private int lastRunningAppId;
    private boolean suspendGridUpdates;
    private boolean inForeground;
    private boolean showHiddenApps;
    private HashSet<Integer> hiddenAppIds = new HashSet<>();

    public final static String HIDDEN_APPS_PREF_FILENAME = "HiddenApps";

    public final static String NAME_EXTRA = "Name";
    public final static String UUID_EXTRA = "UUID";
    public final static String NEW_PAIR_EXTRA = "NewPair";
    public final static String SHOW_HIDDEN_APPS_EXTRA = "ShowHiddenApps";

    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Get the computer object
                    computer = localBinder.getComputer(uuidString);
                    if (computer == null) {
                        finish();
                        return;
                    }

                    // Add a launcher shortcut for this PC (forced, since this is user interaction)
                    shortcutHelper.createAppViewShortcut(computer, true, getIntent().getBooleanExtra(NEW_PAIR_EXTRA, false));
                    shortcutHelper.reportComputerShortcutUsed(computer);

                    try {
                        appGridAdapter = new AppGridAdapter(AppView.this,
                                PreferenceConfiguration.readPreferences(AppView.this),
                                computer, localBinder.getUniqueId(),
                                showHiddenApps);
                    } catch (Exception e) {
                        e.printStackTrace();
                        finish();
                        return;
                    }

                    appGridAdapter.updateHiddenApps(hiddenAppIds, true);

                    // Now make the binder visible. We must do this after appGridAdapter
                    // is set to prevent us from reaching updateUiWithServerinfo() and
                    // touching the appGridAdapter prior to initialization.
                    managerBinder = localBinder;

                    // Load the app grid with cached data (if possible).
                    // This must be done _before_ startComputerUpdates()
                    // so the initial serverinfo response can update the running
                    // icon.
                    populateAppGridWithCache();

                    // Start updates
                    startComputerUpdates();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (isFinishing() || isChangingConfigurations()) {
                                return;
                            }

                            // Despite my best efforts to catch all conditions that could
                            // cause the activity to be destroyed when we try to commit
                            // I haven't been able to, so we have this try-catch block.
                            try {
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                                        .commitAllowingStateLoss();
                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // If appGridAdapter is initialized, let it know about the configuration change.
        // If not, it will pick it up when it initializes.
        if (appGridAdapter != null) {
            // Update the app grid adapter to create grid items with the correct layout
            appGridAdapter.updateLayoutWithPreferences(this, PreferenceConfiguration.readPreferences(this));

            try {
                // Reinflate the app grid itself to pick up the layout change
                getFragmentManager().beginTransaction()
                        .replace(R.id.appFragmentContainer, new AdapterFragment())
                        .commitAllowingStateLoss();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if(dialogFragment!=null) {
            dialogFragment.dismiss();
        }
    }

    private void startComputerUpdates() {
        // Don't start polling if we're not bound or in the foreground
        if (managerBinder == null || !inForeground) {
            return;
        }

        managerBinder.startPolling(new ComputerManagerListener() {
            @Override
            public void notifyComputerUpdated(final ComputerDetails details) {
                // Do nothing if updates are suspended
                if (suspendGridUpdates) {
                    return;
                }

                // Don't care about other computers
                if (!details.uuid.equalsIgnoreCase(uuidString)) {
                    return;
                }

                if (details.state == ComputerDetails.State.OFFLINE) {
                    // The PC is unreachable now
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.lost_connection), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // Close immediately if the PC is no longer paired
                if (details.state == ComputerDetails.State.ONLINE && details.pairState != PairingManager.PairState.PAIRED) {
                    AppView.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Disable shortcuts referencing this PC for now
                            shortcutHelper.disableComputerShortcut(details,
                                    getResources().getString(R.string.scut_not_paired));

                            // Display a toast to the user and quit the activity
                            Toast.makeText(AppView.this, getResources().getText(R.string.scut_not_paired), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });

                    return;
                }

                // App list is the same or empty
                if (details.rawAppList == null || details.rawAppList.equals(lastRawApplist)) {

                    // Let's check if the running app ID changed
                    if (details.runningGameId != lastRunningAppId) {
                        // Update the currently running game using the app ID
                        lastRunningAppId = details.runningGameId;
                        updateUiWithServerinfo(details);
                    }

                    return;
                }

                lastRunningAppId = details.runningGameId;
                lastRawApplist = details.rawAppList;

                try {
                    updateUiWithAppList(NvHTTP.getAppListByReader(new StringReader(details.rawAppList)));
                    updateUiWithServerinfo(details);

                    dismissBlockingLoadDialog();
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });

        if (poller == null) {
            poller = managerBinder.createAppListPoller(computer);
        }
        poller.start();
    }

    private void stopComputerUpdates() {
        if (poller != null) {
            poller.stop();
        }

        if (managerBinder != null) {
            managerBinder.stopPolling();
        }

        if (appGridAdapter != null) {
            appGridAdapter.cancelQueuedOperations();
        }
    }

    private GameDisplayFragment dialogFragment;

    private PreferenceConfiguration pref;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        shortcutHelper = new ShortcutHelper(this);

        UiHelper.setLocale(this);

        setContentView(R.layout.activity_app_view_new);

        // Allow floating expanded PiP overlays while browsing apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }

        UiHelper.notifyNewRootViewImmersive(this);

        showHiddenApps = getIntent().getBooleanExtra(SHOW_HIDDEN_APPS_EXTRA, false);
        uuidString = getIntent().getStringExtra(UUID_EXTRA);

        SharedPreferences hiddenAppsPrefs = getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE);
        for (String hiddenAppIdStr : hiddenAppsPrefs.getStringSet(uuidString, new HashSet<String>())) {
            hiddenAppIds.add(Integer.parseInt(hiddenAppIdStr));
        }

        String computerName = getIntent().getStringExtra(NAME_EXTRA);

        TextView label = findViewById(R.id.appListText);
        setTitle(computerName);
        label.setText(computerName);

        ImageView imageView=findViewById(R.id.iv_root_view);

        pref=PreferenceConfiguration.readPreferences(this);

        if(pref.enableScreenBg&&Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            String fileName= PreferenceManager.getDefaultSharedPreferences(this).getString("screen_bg_file_name","axi_screen_bg.png");
            File imageFile=new File(getFilesDir().getAbsolutePath(),fileName);
            if(imageFile.exists()){
                try{
                    Glide.with(this)
                            .load(imageFile)
                            .skipMemoryCache(true)
                            .diskCacheStrategy( DiskCacheStrategy.ALL )
                            .into(imageView);
                    imageView.setVisibility(View.VISIBLE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S&&pref.enableScreenObscure) {
                        findViewById(R.id.iv_root_view).setRenderEffect(RenderEffect.createBlurEffect(25, 25, Shader.TileMode.CLAMP));
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }else{
                imageView.setVisibility(View.GONE);
            }
        }else{
            imageView.setVisibility(View.GONE);
        }

        findViewById(R.id.settingsButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(dialogFragment!=null){
                    dialogFragment.dismiss();
                    dialogFragment=null;
                }
                dialogFragment=new GameDisplayFragment();
                dialogFragment.setWidth(UiHelper.dpToPx(AppView.this,364));
                dialogFragment.setTitle("显示");
                dialogFragment.setOnClick(new GameDisplayFragment.onClick() {
                    @Override
                    public void click() {
                        Toast.makeText(AppView.this,"修改成功！",Toast.LENGTH_SHORT).show();
                    }
                });
                dialogFragment.setPrefConfig(pref);
                dialogFragment.show(getFragmentManager());
            }
        });

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
    }

    private void updateHiddenApps(boolean hideImmediately) {
        HashSet<String> hiddenAppIdStringSet = new HashSet<>();

        for (Integer hiddenAppId : hiddenAppIds) {
            hiddenAppIdStringSet.add(hiddenAppId.toString());
        }

        getSharedPreferences(HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .putStringSet(uuidString, hiddenAppIdStringSet)
                .apply();

        appGridAdapter.updateHiddenApps(hiddenAppIds, hideImmediately);
    }

    private void populateAppGridWithCache() {
        try {
            // Try to load from cache
            lastRawApplist = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));
            List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(lastRawApplist));
            updateUiWithAppList(applist);
            LimeLog.info("Loaded applist from cache");
        } catch (IOException | XmlPullParserException e) {
            if (lastRawApplist != null) {
                LimeLog.warning("Saved applist corrupted: "+lastRawApplist);
                e.printStackTrace();
            }
            LimeLog.info("Loading applist from the network");
            // We'll need to load from the network
            loadAppsBlocking();
        }
    }

    private void loadAppsBlocking() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || blockingLoadDialog != null) {
                return;
            }
            blockingLoadDialog = AppDialog.showProgress(
                    this,
                    getResources().getString(R.string.applist_refresh_title),
                    getResources().getString(R.string.applist_refresh_msg),
                    this::finish);
        });
    }

    private void dismissBlockingLoadDialog() {
        runOnUiThread(() -> {
            if (blockingLoadDialog != null) {
                blockingLoadDialog.dismiss();
                blockingLoadDialog = null;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (blockingLoadDialog != null) {
            blockingLoadDialog.dismiss();
            blockingLoadDialog = null;
        }
        Dialog.closeDialogs();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates();
    }

    private void showApplicationActions(AbsListView listView, View sourceView, int position, AppObject selectedApp) {
        if (selectedApp == null || selectedApp.app == null) {
            return;
        }

        Bitmap shortcutBitmap = extractAppBitmap(sourceView);
        AppActionsDialog dialog = new AppActionsDialog();
        dialog.setFragmentManager(getFragmentManager());
        dialog.setWidth(UiHelper.dpToPx(this, 364));
        dialog.setDimAmount(0.46f);
        dialog.setCancelOutside(true);
        dialog.setApp(
                selectedApp.app,
                lastRunningAppId,
                selectedApp.isHidden,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && shortcutBitmap != null);
        dialog.setListener(new AppActionsDialog.Listener() {
            @Override
            public void onStart() {
                ServerHelper.doStart(AppView.this, selectedApp.app, computer, managerBinder);
            }

            @Override
            public void onResume() {
                ServerHelper.doStart(AppView.this, selectedApp.app, computer, managerBinder);
            }

            @Override
            public void onQuit() {
                showQuitApplicationConfirmation(selectedApp);
            }

            @Override
            public void onQuitAndStart() {
                showStartWithQuitConfirmation(selectedApp);
            }

            @Override
            public void onToggleVisibility() {
                toggleApplicationHidden(selectedApp);
            }

            @Override
            public void onCreateShortcut() {
                createApplicationShortcut(selectedApp, shortcutBitmap);
            }

            @Override
            public void onDismiss() {
                restoreListFocus(listView, sourceView, position);
            }
        });
        dialog.show();
    }

    private void showStartWithQuitConfirmation(AppObject selectedApp) {
        AppDialog.showConfirm(
                this,
                selectedApp.app.getAppName(),
                getString(R.string.applist_quit_confirmation),
                getString(R.string.applist_menu_quit_and_start),
                false,
                () -> ServerHelper.doStart(AppView.this, selectedApp.app, computer, managerBinder),
                null);
    }

    private void showQuitApplicationConfirmation(AppObject selectedApp) {
        AppDialog.showConfirm(
                this,
                selectedApp.app.getAppName(),
                getString(R.string.applist_quit_confirmation),
                getString(R.string.applist_menu_quit),
                true,
                () -> {
                    suspendGridUpdates = true;
                    ServerHelper.doQuit(AppView.this, computer, selectedApp.app, managerBinder, () -> {
                        suspendGridUpdates = false;
                        if (poller != null) {
                            poller.pollNow();
                        }
                    });
                },
                null);
    }

    private void toggleApplicationHidden(AppObject selectedApp) {
        if (selectedApp.isHidden) {
            hiddenAppIds.remove(selectedApp.app.getAppId());
        } else {
            hiddenAppIds.add(selectedApp.app.getAppId());
        }
        updateHiddenApps(false);
    }

    private Bitmap extractAppBitmap(View sourceView) {
        if (sourceView == null) {
            return null;
        }
        ImageView appImageView = sourceView.findViewById(R.id.grid_image);
        if (appImageView == null) {
            return null;
        }
        if (appImageView.getDrawable() instanceof BitmapDrawable) {
            return ((BitmapDrawable) appImageView.getDrawable()).getBitmap();
        }
        if (appImageView.getDrawable() instanceof GlideBitmapDrawable) {
            return ((GlideBitmapDrawable) appImageView.getDrawable()).getBitmap();
        }
        return null;
    }

    private void createApplicationShortcut(AppObject selectedApp, Bitmap appBits) {
        if (!shortcutHelper.createPinnedGameShortcut(computer, selectedApp.app, appBits)) {
            Toast.makeText(AppView.this, R.string.unable_to_pin_shortcut, Toast.LENGTH_LONG).show();
        }
    }

    private void restoreListFocus(AbsListView listView, View sourceView, int position) {
        if (sourceView != null && sourceView.isAttachedToWindow()) {
            sourceView.requestFocus();
            return;
        }
        listView.setSelection(position);
        listView.post(listView::requestFocus);
    }

    private void updateUiWithServerinfo(final ComputerDetails details) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                    // Look through our current app list to tag the running app
                for (int i = 0; i < appGridAdapter.getCount(); i++) {
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // There can only be one or zero apps running.
                    if (existingApp.isRunning &&
                            existingApp.app.getAppId() == details.runningGameId) {
                        // This app was running and still is, so we're done now
                        return;
                    }
                    else if (existingApp.app.getAppId() == details.runningGameId) {
                        // This app wasn't running but now is
                        existingApp.isRunning = true;
                        updated = true;
                    }
                    else if (existingApp.isRunning) {
                        // This app was running but now isn't
                        existingApp.isRunning = false;
                        updated = true;
                    }
                    else {
                        // This app wasn't running and still isn't
                    }
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    private void updateUiWithAppList(final List<NvApp> appList) {
        AppView.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean updated = false;

                // First handle app updates and additions
                for (NvApp app : appList) {
                    boolean foundExistingApp = false;

                    // Try to update an existing app in the list first
                    for (int i = 0; i < appGridAdapter.getCount(); i++) {
                        AppObject existingApp = (AppObject) appGridAdapter.getItem(i);
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            // Found the app; update its properties
                            if (!existingApp.app.getAppName().equals(app.getAppName())) {
                                existingApp.app.setAppName(app.getAppName());
                                updated = true;
                            }

                            foundExistingApp = true;
                            break;
                        }
                    }

                    if (!foundExistingApp) {
                        // This app must be new
                        appGridAdapter.addApp(new AppObject(app));

                        // We could have a leftover shortcut from last time this PC was paired
                        // or if this app was removed then added again. Enable those shortcuts
                        // again if present.
                        shortcutHelper.enableAppShortcut(computer, app);

                        updated = true;
                    }
                }

                // Next handle app removals
                int i = 0;
                while (i < appGridAdapter.getCount()) {
                    boolean foundExistingApp = false;
                    AppObject existingApp = (AppObject) appGridAdapter.getItem(i);

                    // Check if this app is in the latest list
                    for (NvApp app : appList) {
                        if (existingApp.app.getAppId() == app.getAppId()) {
                            foundExistingApp = true;
                            break;
                        }
                    }

                    // This app was removed in the latest app list
                    if (!foundExistingApp) {
                        shortcutHelper.disableAppShortcut(computer, existingApp.app, "App removed from PC");
                        appGridAdapter.removeApp(existingApp);
                        updated = true;

                        // Check this same index again because the item at i+1 is now at i after
                        // the removal
                        continue;
                    }

                    // Move on to the next item
                    i++;
                }

                if (updated) {
                    appGridAdapter.notifyDataSetChanged();
                }
            }
        });
    }

    @Override
    public int getAdapterFragmentLayoutId() {
//        return PreferenceConfiguration.readPreferences(AppView.this).smallIconMode ?
//                    R.layout.app_grid_view_small : R.layout.app_grid_view;
        return R.layout.app_grid_view_new;
    }

    @Override
    public void receiveAbsListView(AbsListView listView) {
        listView.setAdapter(appGridAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long id) {
                AppObject app = (AppObject) appGridAdapter.getItem(pos);

                // Only open the context menu if something is running, otherwise start it
                if (lastRunningAppId != 0&&!pref.passAppMenu) {
                    showApplicationActions(listView, arg1, pos, app);
                } else {
                    ServerHelper.doStart(AppView.this, app.app, computer, managerBinder);
                }
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            AppObject app = (AppObject) appGridAdapter.getItem(position);
            showApplicationActions(listView, view, position, app);
            return true;
        });
        UiHelper.applyStatusBarPadding(listView);
        listView.requestFocus();
    }

    public static class AppObject {
        public final NvApp app;
        public boolean isRunning;
        public boolean isHidden;

        public AppObject(NvApp app) {
            if (app == null) {
                throw new IllegalArgumentException("app must not be null");
            }
            this.app = app;
        }

        @Override
        public String toString() {
            return app.getAppName();
        }
    }

}
