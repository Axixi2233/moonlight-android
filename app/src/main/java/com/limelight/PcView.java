package com.limelight;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.PcGridAdapter;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.AppDialog;
import com.limelight.ui.PcActionsDialog;
import com.limelight.ui.home.HomeControllerCarouselView;
import com.limelight.ui.home.HomeHostCarouselView;
import com.limelight.ui.home.HomePageIndicatorView;
import com.limelight.utils.DeviceUtils;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;
import com.limelight.utils.UpdateChecker;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.VibratorManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.axi.gamepad.an.AxiGamePadIndexActivity;
import cn.axi.gamepad.an.GamePadUIActivity;

import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

public class PcView extends Activity {
    private static final String STATE_SELECTED_HOST = "selectedHost";
    private static final String STATE_HOST_LIST_MODE = "hostListMode";
    private static final String STATE_HOST_LANDSCAPE_LIST_MODE = "hostLandscapeListMode";
    private static final String PREF_HOST_LIST_MODE = "home_host_list_mode";
    private static final String PREF_HOST_LANDSCAPE_LIST_MODE =
            "home_host_landscape_list_mode";
    private static final String ADD_HOST_SELECTION = "__moonlight_add_host__";

    private AlertDialog pairingDialog;
    private PcGridAdapter pcGridAdapter;
    private HomeHostCarouselView hostCarousel;
    private HomePageIndicatorView pageIndicator;
    private TextView hostCounter;
    private TextView hostHint;
    private ImageButton hostLayoutToggle;
    private ImageButton hostAddButton;
    private TextView selectedHostSummary;
    private HomeControllerCarouselView controllerCarousel;
    private InputManager inputManager;
    private boolean inputListenerRegistered;
    private String selectedControllerKey;
    private String selectedHostKey = ADD_HOST_SELECTION;
    private boolean hostSelectionExplicit;
    private boolean hostListMode;
    private boolean hostLandscapeListMode;
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground, completeOnCreateCalled;
    private boolean autoUpdateCheckStarted;
    private boolean redirectingToBackgroundStream;
    private final InputManager.InputDeviceListener inputDeviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            updateControllerCard();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            updateControllerCard();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            updateControllerCard();
        }
    };
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

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Start updates
                    startComputerUpdates();

                    // Force a keypair to be generated early to avoid discovery delays
                    new AndroidCryptoProvider(PcView.this).getClientCertificate();
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

        // Only reinitialize views if completeOnCreate() was called
        // before this callback. If it was not, completeOnCreate() will
        // handle initializing views with the config change accounted for.
        // This is not prone to races because both callbacks are invoked
        // in the main thread.
        if (completeOnCreateCalled) {
            // Reinitialize views just in case orientation changed
            initializeViews();
        }
    }

    private void initializeViews() {
        setContentView(R.layout.activity_pc_view_new);
        UiHelper.notifyNewRootViewImmersive(this);
        // Allow floating expanded PiP overlays while browsing PCs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setShouldDockBigOverlays(false);
        }
        // Set default preferences if we've never been run
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        ImageView imageView=findViewById(R.id.iv_root_view);

        PreferenceConfiguration pref=PreferenceConfiguration.readPreferences(this);

        if(pref.enableScreenBg&&Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            String fileName=PreferenceManager.getDefaultSharedPreferences(this).getString("screen_bg_file_name","axi_screen_bg.png");
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
        TextView tx_label=findViewById(R.id.tx_label);
        if(!TextUtils.isEmpty(pref.screenLabel)){
            tx_label.setText(pref.screenLabel);
        }
        ImageButton settingsButton = findViewById(R.id.settingsButton);
        ImageButton helpButton = findViewById(R.id.helpButton);
        ImageButton axButton = findViewById(R.id.axiButton);
        axButton.setVisibility(View.VISIBLE);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PcView.this, StreamSettings.class));
            }
        });
        helpButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PcView.this,AboutActivity.class));
//                HelpLauncher.launchSetupGuide(PcView.this);
            }
        });

        axButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PcView.this, AxiGamePadIndexActivity.class);
                startActivity(i);
            }
        });

        hostCarousel = findViewById(R.id.hostCarousel);
        pageIndicator = findViewById(R.id.homePageIndicator);
        hostCounter = findViewById(R.id.homeHostCounter);
        hostHint = findViewById(R.id.homeHostHint);
        hostLayoutToggle = findViewById(R.id.homeHostLayoutToggle);
        hostAddButton = findViewById(R.id.homeHostAddButton);
        selectedHostSummary = findViewById(R.id.homeSelectedSummary);
        controllerCarousel = findViewById(R.id.homeControllerCarousel);
        controllerCarousel.setOnSelectionChangedListener(
                controllerKey -> selectedControllerKey = controllerKey);
        controllerCarousel.setOnControllerClickListener(() ->
                startActivity(new Intent(PcView.this, GamePadUIActivity.class)));

        hostLayoutToggle.setOnClickListener(view -> {
            boolean portrait = getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(PcView.this)
                    .edit();
            if (portrait) {
                hostListMode = !hostListMode;
                editor.putBoolean(PREF_HOST_LIST_MODE, hostListMode);
            }
            else if (hostCarousel.getHostCount() > 2) {
                hostLandscapeListMode = !hostLandscapeListMode;
                editor.putBoolean(
                        PREF_HOST_LANDSCAPE_LIST_MODE, hostLandscapeListMode);
            }
            editor.apply();
            applyHostLayoutMode();
            configureHomeLayout();
        });
        hostAddButton.setOnClickListener(view -> launchAddHost());

        hostCarousel.setListener(new HomeHostCarouselView.Listener() {
            @Override
            public void onSelectionChanged(String selectionKey, int position, int hostCount,
                                           ComputerObject computer, boolean addCard,
                                           boolean userInitiated) {
                selectedHostKey = selectionKey;
                hostSelectionExplicit |= userInitiated;
                pageIndicator.setPageState(hostCarousel.getDisplayItemCount(), position);
                if (hostCarousel.isListMode()) {
                    pageIndicator.setVisibility(View.GONE);
                }
                updateHostSelectionSummary(position, hostCount, computer, addCard);
                updateHostHeaderActions();
            }

            @Override
            public void onHostActivated(ComputerObject computer, View sourceView) {
                activateComputer(computer, sourceView);
            }

            @Override
            public void onHostActions(ComputerObject computer, View sourceView, int position) {
                showComputerActions(sourceView, position, computer);
            }

            @Override
            public void onAddHost() {
                launchAddHost();
            }
        });

        applyHostLayoutMode();
        configureHomeLayout();
        configureHomeSystemBars();
        updateDeviceCard();
        updateControllerCard();
        syncHomeHosts();
        if (isTelevision()) {
            hostCarousel.post(hostCarousel::requestFocusOnSelectedCard);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Game.resumeBackgroundStreamIfPresent(this)) {
            redirectingToBackgroundStream = true;
            finish();
            return;
        }

        if (savedInstanceState != null) {
            selectedHostKey = savedInstanceState.getString(
                    STATE_SELECTED_HOST, ADD_HOST_SELECTION);
            hostListMode = savedInstanceState.getBoolean(STATE_HOST_LIST_MODE, false);
            hostLandscapeListMode = savedInstanceState.getBoolean(
                    STATE_HOST_LANDSCAPE_LIST_MODE, false);
            hostSelectionExplicit = true;
        }
        else {
            SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(this);
            hostListMode = preferences.getBoolean(PREF_HOST_LIST_MODE, false);
            hostLandscapeListMode = preferences.getBoolean(
                    PREF_HOST_LANDSCAPE_LIST_MODE, false);
        }

        // Assume we're in the foreground when created to avoid a race
        // between binding to CMS and onResume()
        inForeground = true;

        // Create a GLSurfaceView to fetch GLRenderer unless we have
        // a cached result already.
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    // Save the GLRenderer string so we don't need to do this next time
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();

                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            completeOnCreate();
                        }
                    });
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int i, int i1) {
                }

                @Override
                public void onDrawFrame(GL10 gl10) {
                }
            });
            setContentView(surfaceView);
        }
        else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        completeOnCreateCalled = true;

        shortcutHelper = new ShortcutHelper(this);
        inputManager = (InputManager) getSystemService(INPUT_SERVICE);

        UiHelper.setLocale(this);

        // Bind to the computer manager service
        bindService(new Intent(PcView.this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        pcGridAdapter = new PcGridAdapter(this, PreferenceConfiguration.readPreferences(this));

        initializeViews();

        if (!autoUpdateCheckStarted) {
            autoUpdateCheckStarted = true;
            UpdateChecker.checkForUpdates(this, false);
        }
    }

    private void startComputerUpdates() {
        // Only allow polling to start if we're bound to CMS, polling is not already running,
        // and our activity is in the foreground.
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        PcView.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateComputer(details);
                            }
                        });

                        // Add a launcher shortcut for this PC (off the main thread to prevent ANRs)
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) {
                return;
            }

            freezeUpdates = true;

            managerBinder.stopPolling();

            if (wait) {
                managerBinder.waitForPollingStopped();
            }

            runningPolling = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterInputDeviceListener();

        if (managerBinder != null) {
            unbindService(serviceConnection);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!redirectingToBackgroundStream && Game.resumeBackgroundStreamIfPresent(this)) {
            redirectingToBackgroundStream = true;
            finish();
            return;
        }

        // Display a decoder crash notification if we've returned after a crash
        UiHelper.showDecoderCrashDialog(this);

        inForeground = true;
        startComputerUpdates();
        registerInputDeviceListener();
        updateControllerCard();
    }

    @Override
    protected void onPause() {
        super.onPause();

        inForeground = false;
        stopComputerUpdates(false);
        unregisterInputDeviceListener();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (hostCarousel != null) {
            selectedHostKey = hostCarousel.getSelectedKey();
        }
        outState.putString(STATE_SELECTED_HOST, selectedHostKey);
        outState.putBoolean(STATE_HOST_LIST_MODE, hostListMode);
        outState.putBoolean(
                STATE_HOST_LANDSCAPE_LIST_MODE, hostLandscapeListMode);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                onBackPressed();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onStop() {
        super.onStop();

        dismissPairingDialog();
        Dialog.closeDialogs();
    }

    private void showComputerActions(View sourceView, int position, ComputerObject computer) {
        if (computer == null || computer.details == null) {
            return;
        }

        stopComputerUpdates(false);
        ComputerDetails details = computer.details;
        PcActionsDialog dialog = new PcActionsDialog();
        dialog.setFragmentManager(getFragmentManager());
        dialog.setWidth(UiHelper.dpToPx(this, 364));
        dialog.setDimAmount(0.46f);
        dialog.setCancelOutside(true);
        dialog.setComputer(details);
        dialog.setListener(new PcActionsDialog.Listener() {
            @Override
            public void onResumeStream(ComputerDetails selectedComputer) {
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
                    return;
                }
                ServerHelper.doStart(PcView.this,
                        new NvApp("app", selectedComputer.runningGameId, false), selectedComputer, managerBinder);
            }

            @Override
            public void onQuitStream(ComputerDetails selectedComputer) {
                if (managerBinder == null) {
                    Toast.makeText(PcView.this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
                    return;
                }
                UiHelper.displayQuitConfirmationDialog(PcView.this, () -> ServerHelper.doQuit(
                        PcView.this, selectedComputer, new NvApp("app", 0, false), managerBinder, null), null);
            }

            @Override
            public void onOpenAppList(ComputerDetails selectedComputer) {
                doAppList(selectedComputer, false, true);
            }

            @Override
            public void onPairComputer(ComputerDetails selectedComputer) {
                doPair(selectedComputer);
            }

            @Override
            public void onWakeComputer(ComputerDetails selectedComputer) {
                doWakeOnLan(selectedComputer);
            }

            @Override
            public void onShowGameStreamEol() {
                HelpLauncher.launchGameStreamEolFaq(PcView.this);
            }

            @Override
            public void onTestNetwork() {
                ServerHelper.doNetworkTest(PcView.this);
            }

            @Override
            public void onDeleteComputer(ComputerDetails selectedComputer) {
                showDeleteComputerConfirmation(selectedComputer);
            }

            @Override
            public void onDismiss() {
                startComputerUpdates();
                restoreHostFocus(sourceView);
            }
        });
        dialog.show();
    }

    private void showDeleteComputerConfirmation(ComputerDetails details) {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }
        AppDialog.showConfirm(
                this,
                details.name,
                getString(R.string.delete_pc_msg),
                getString(R.string.yes),
                true,
                () -> {
                    if (managerBinder == null) {
                        Toast.makeText(PcView.this, R.string.error_manager_not_running, Toast.LENGTH_LONG).show();
                        return;
                    }
                    removeComputer(details);
                },
                null);
    }

    private void restoreHostFocus(View sourceView) {
        if (sourceView != null && sourceView.isAttachedToWindow()) {
            sourceView.requestFocus();
            return;
        }
        if (hostCarousel != null) {
            hostCarousel.post(hostCarousel::requestFocusOnSelectedCard);
        }
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.pairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                boolean success = false;
                try {
                    // Stop updates and wait while pairing
                    stopComputerUpdates(true);

                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    httpConn.setClientName(DeviceUtils.getManufacturer()+"-"+DeviceUtils.getModel());
                    if (httpConn.getPairState() == PairState.PAIRED) {
                        // Don't display any toast, but open the app list
                        message = null;
                        success = true;
                    }
                    else {
                        final String pinStr = PairingManager.generatePinString();

                        runOnUiThread(() -> pairingDialog = AppDialog.showMessage(
                                PcView.this,
                                getResources().getString(R.string.pair_pairing_title),
                                getResources().getString(R.string.pair_pairing_msg) + " " + pinStr + "\n\n" +
                                        getResources().getString(R.string.pair_pairing_help),
                                getResources().getString(R.string.dialog_action_close),
                                null,
                                getResources().getString(R.string.help),
                                () -> HelpLauncher.launchTroubleshooting(PcView.this),
                                false));

                        PairingManager pm = httpConn.getPairingManager();

                        PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                        if (pairState == PairState.PIN_WRONG) {
                            message = getResources().getString(R.string.pair_incorrect_pin);
                        }
                        else if (pairState == PairState.FAILED) {
                            if (computer.runningGameId != 0) {
                                message = getResources().getString(R.string.pair_pc_ingame);
                            }
                            else {
                                message = getResources().getString(R.string.pair_fail);
                            }
                        }
                        else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                            message = getResources().getString(R.string.pair_already_in_progress);
                        }
                        else if (pairState == PairState.PAIRED) {
                            // Just navigate to the app view without displaying a toast
                            message = null;
                            success = true;

                            // Pin this certificate for later HTTPS use
                            managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();

                            // Invalidate reachability information after pairing to force
                            // a refresh before reading pair state again
                            managerBinder.invalidateStateForComputer(computer.uuid);
                        }
                        else {
                            // Should be no other values
                            message = null;
                        }
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                    message = e.getMessage();
                }

                final String toastMessage = message;
                final boolean toastSuccess = success;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dismissPairingDialog();
                        if (toastMessage != null) {
                            Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                        }

                        if (toastSuccess) {
                            // Open the app list after a successful pairing attempt
                            doAppList(computer, true, false);
                        }
                        else {
                            // Start polling again if we're still in the foreground
                            startComputerUpdates();
                        }
                    }
                });
            }
        }).start();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_pc_online), Toast.LENGTH_SHORT).show();
            return;
        }

        if (computer.macAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.wol_no_mac), Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String message;
                try {
                    WakeOnLanSender.sendWolPacket(computer);
                    message = getResources().getString(R.string.wol_waking_msg);
                } catch (IOException e) {
                    message = getResources().getString(R.string.wol_fail);
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doUnpair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(PcView.this, getResources().getString(R.string.unpairing), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                            computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                            PlatformBinding.getCryptoProvider(PcView.this));
                    httpConn.setClientName(DeviceUtils.getManufacturer()+"-"+DeviceUtils.getModel());
                    if (httpConn.getPairState() == PairingManager.PairState.PAIRED) {
                        httpConn.unpair();
                        if (httpConn.getPairState() == PairingManager.PairState.NOT_PAIRED) {
                            message = getResources().getString(R.string.unpair_success);
                        }
                        else {
                            message = getResources().getString(R.string.unpair_fail);
                        }
                    }
                    else {
                        message = getResources().getString(R.string.unpair_error);
                    }
                } catch (UnknownHostException e) {
                    message = getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = getResources().getString(R.string.error_404);
                } catch (XmlPullParserException | IOException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                }

                final String toastMessage = message;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(PcView.this, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        if (managerBinder == null) {
            Toast.makeText(PcView.this, getResources().getString(R.string.error_manager_not_running), Toast.LENGTH_LONG).show();
            return;
        }

        Intent i = new Intent(this, AppView.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        i.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(i);
    }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);

        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);

        // Delete hidden games preference value
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit()
                .remove(details.uuid)
                .apply();

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            if (details.equals(computer.details)) {
                // Disable or delete shortcuts referencing this PC
                shortcutHelper.disableComputerShortcut(details,
                        getResources().getString(R.string.scut_deleted_pc));

                pcGridAdapter.removeComputer(computer);
                pcGridAdapter.notifyDataSetChanged();
                if (details.uuid != null && details.uuid.equals(selectedHostKey)) {
                    selectedHostKey = pcGridAdapter.getCount() == 0
                            ? ADD_HOST_SELECTION
                            : ((ComputerObject) pcGridAdapter.getItem(
                                    Math.min(i, pcGridAdapter.getCount() - 1))).details.uuid;
                }
                syncHomeHosts();

                break;
            }
        }
    }
    
    private void updateComputer(ComputerDetails details) {
        ComputerObject existingEntry = null;

        for (int i = 0; i < pcGridAdapter.getCount(); i++) {
            ComputerObject computer = (ComputerObject) pcGridAdapter.getItem(i);

            // Check if this is the same computer
            if (details.uuid.equals(computer.details.uuid)) {
                existingEntry = computer;
                break;
            }
        }

        if (existingEntry != null) {
            // Replace the information in the existing entry
            existingEntry.details = details;
        }
        else {
            // Add a new entry
            pcGridAdapter.addComputer(new ComputerObject(details));
        }

        // Notify the view that the data has changed
        pcGridAdapter.notifyDataSetChanged();
        syncHomeHosts();
    }

    private void activateComputer(ComputerObject computer, View sourceView) {
        if (computer == null || computer.details == null) {
            return;
        }

        ComputerDetails details = computer.details;
        if (details.state == ComputerDetails.State.UNKNOWN
                || details.state == ComputerDetails.State.OFFLINE) {
            showComputerActions(sourceView, 0, computer);
        }
        else if (details.pairState != PairState.PAIRED) {
            doPair(details);
        }
        else {
            doAppList(details, false, false);
        }
    }

    private void syncHomeHosts() {
        if (hostCarousel == null || pcGridAdapter == null) {
            return;
        }

        List<ComputerObject> computers = new ArrayList<>();
        for (int index = 0; index < pcGridAdapter.getCount(); index++) {
            computers.add((ComputerObject) pcGridAdapter.getItem(index));
        }
        Collections.sort(computers, (left, right) -> {
            int stateComparison = Integer.compare(
                    getHostSortRank(left), getHostSortRank(right));
            if (stateComparison != 0) {
                return stateComparison;
            }
            String leftName = left == null || left.details == null || left.details.name == null
                    ? "" : left.details.name;
            String rightName = right == null || right.details == null || right.details.name == null
                    ? "" : right.details.name;
            return leftName.compareToIgnoreCase(rightName);
        });
        if (!computers.isEmpty() && !hostSelectionExplicit) {
            selectedHostKey = computers.get(0).details.uuid;
        }
        hostCarousel.setComputers(computers, selectedHostKey);
        applyHostLayoutMode();
        configureHomeLayout();
    }

    private static int getHostSortRank(ComputerObject computer) {
        if (computer == null || computer.details == null
                || computer.details.state == ComputerDetails.State.UNKNOWN) {
            return 1;
        }
        return computer.details.state == ComputerDetails.State.ONLINE ? 0 : 2;
    }

    private void launchAddHost() {
        startActivity(new Intent(PcView.this, AddComputerManually.class));
    }

    private void applyHostLayoutMode() {
        if (hostCarousel == null || hostLayoutToggle == null || hostAddButton == null) {
            return;
        }

        boolean portrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        boolean landscapeLayoutAvailable = !portrait && hostCarousel.getHostCount() > 2;
        boolean activeListMode = portrait
                ? hostListMode
                : landscapeLayoutAvailable && hostLandscapeListMode;
        View hostSectionTitle = findViewById(R.id.homeHostSectionTitle);
        if (hostSectionTitle != null) {
            hostSectionTitle.setVisibility(portrait ? View.VISIBLE : View.GONE);
        }
        hostLayoutToggle.setVisibility(
                portrait || landscapeLayoutAvailable ? View.VISIBLE : View.GONE);
        hostCarousel.setHeaderAddAvailable(portrait || landscapeLayoutAvailable);
        hostCarousel.setListMode(activeListMode);
        hostLayoutToggle.setImageResource(activeListMode
                ? R.drawable.ic_home_layout_cards
                : R.drawable.ic_home_layout_list);
        hostLayoutToggle.setContentDescription(getString(activeListMode
                ? R.string.home_switch_to_cards
                : R.string.home_switch_to_list));
        if (hostHint != null) {
            hostHint.setVisibility(portrait ? View.VISIBLE : View.GONE);
            if (portrait) {
                hostHint.setText(activeListMode
                        ? R.string.home_select_host_list_hint
                        : R.string.home_select_host_hint);
            }
        }
        if (hostCounter != null) {
            int counterVisibility;
            if (portrait) {
                counterVisibility = activeListMode ? View.GONE : View.VISIBLE;
            }
            else {
                counterVisibility = landscapeLayoutAvailable ? View.VISIBLE : View.GONE;
            }
            hostCounter.setVisibility(counterVisibility);
        }
        if (pageIndicator != null) {
            pageIndicator.setVisibility(activeListMode ? View.GONE : View.VISIBLE);
        }
        updateHostHeaderActions();
    }

    private void updateHostHeaderActions() {
        if (hostCarousel == null || hostAddButton == null) {
            return;
        }
        hostAddButton.setVisibility(hostCarousel.shouldShowHeaderAddButton()
                ? View.VISIBLE : View.GONE);
    }

    private void updateHostSelectionSummary(int position, int hostCount,
                                             ComputerObject computer, boolean addCard) {
        if (hostCounter != null) {
            if (addCard) {
                hostCounter.setText(R.string.home_add_host);
            }
            else if (hostCount == 0) {
                hostCounter.setText(getString(R.string.home_host_count_format, 0, 0));
            }
            else {
                hostCounter.setText(getString(
                        R.string.home_host_count_format, position + 1, hostCount));
            }
        }
        if (selectedHostSummary == null) {
            return;
        }
        if (addCard || computer == null || computer.details == null) {
            selectedHostSummary.setText(getString(R.string.home_discovery_active));
            return;
        }

        ComputerDetails details = computer.details;
        int statusRes;
        if (details.state == ComputerDetails.State.UNKNOWN) {
            statusRes = R.string.home_host_checking;
        }
        else if (details.state == ComputerDetails.State.OFFLINE) {
            statusRes = R.string.home_host_offline;
        }
        else if (details.pairState != PairState.PAIRED) {
            statusRes = R.string.home_host_pair_required;
        }
        else if (details.runningGameId != 0) {
            statusRes = R.string.home_host_running;
        }
        else {
            statusRes = R.string.home_host_ready;
        }
        selectedHostSummary.setText(details.name + " · " + getString(statusRes));
    }

    private void configureHomeLayout() {
        if (hostCarousel == null) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        int heightDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        View sidebar = findViewById(R.id.homeSidebar);
        ViewGroup.LayoutParams carouselParams = hostCarousel.getLayoutParams();
        if (sidebar != null) {
            boolean compactPhoneLandscape = isCompactPhoneLandscape();
            View wideContent = findViewById(R.id.homeWideContent);
            if (wideContent != null && isTelevision()) {
                int horizontal = UiHelper.dpToPx(this, 36);
                int vertical = UiHelper.dpToPx(this, 20);
                wideContent.setPadding(horizontal, vertical, horizontal, vertical);
            }
            int widthDp = Math.round(getResources().getDisplayMetrics().widthPixels / density);
            int sidebarWidthDp = compactPhoneLandscape
                    ? Math.min(288, Math.max(248, Math.round(widthDp * 0.34f)))
                    : Math.min(320, Math.max(260, Math.round(widthDp * 0.32f)));
            ViewGroup.LayoutParams sidebarParams = sidebar.getLayoutParams();
            sidebarParams.width = UiHelper.dpToPx(this, sidebarWidthDp);
            sidebar.setLayoutParams(sidebarParams);
            if (hostCarousel.isListMode()) {
                carouselParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            }
            else {
                int headerAndGapPx = getResources().getDimensionPixelSize(
                        R.dimen.home_top_bar_height)
                        + getResources().getDimensionPixelSize(R.dimen.home_land_info_gap);
                int contentHeightDp = heightDp - Math.round(headerAndGapPx / density);
                int carouselHeightDp = compactPhoneLandscape
                        ? Math.min(240, Math.max(210, contentHeightDp - 112))
                        : Math.min(286, Math.max(230, contentHeightDp - 50));
                carouselParams.height = UiHelper.dpToPx(this, carouselHeightDp);
            }
            if (compactPhoneLandscape) {
                if (selectedHostSummary != null) {
                    selectedHostSummary.setVisibility(View.GONE);
                }
            }
        }
        else {
            if (carouselParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams marginParams =
                        (ViewGroup.MarginLayoutParams) carouselParams;
                marginParams.topMargin = getResources().getDimensionPixelSize(
                        hostCarousel.isListMode()
                                ? R.dimen.home_host_carousel_margin_top
                                : R.dimen.home_host_carousel_portrait_card_margin_top);
            }
            boolean phonePortrait = getResources().getConfiguration().smallestScreenWidthDp < 600;
            int carouselHeightDp;
            if (hostCarousel.isListMode()) {
                int maxVisibleRows = heightDp < 720 ? 3 : heightDp < 840 ? 4 : 5;
                int visibleRows = Math.min(hostCarousel.getHostCount(), maxVisibleRows);
                carouselHeightDp = visibleRows == 0 ? 0 : visibleRows * 88;
            }
            else if (phonePortrait) {
                if (heightDp < 720) {
                    carouselHeightDp = 310;
                }
                else if (heightDp < 840) {
                    carouselHeightDp = 340;
                }
                else {
                    carouselHeightDp = 360;
                }
            }
            else if (heightDp < 720) {
                carouselHeightDp = 340;
            }
            else if (heightDp < 790) {
                carouselHeightDp = 370;
            }
            else {
                carouselHeightDp = 390;
            }
            carouselParams.height = UiHelper.dpToPx(this, carouselHeightDp);
        }
        hostCarousel.setLayoutParams(carouselParams);
    }

    private void configureHomeSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }

        View decorView = window.getDecorView();
        decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                | SYSTEM_UI_FLAG_LAYOUT_STABLE
                | SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        View root = findViewById(R.id.homeRoot);
        View safeContent = findViewById(R.id.homeWideContent);
        if (safeContent == null) {
            safeContent = findViewById(R.id.homeScroll);
        }
        if (root == null || safeContent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
            return;
        }

        final View insetContent = safeContent;
        final int baseLeft = safeContent.getPaddingLeft();
        final int baseTop = safeContent.getPaddingTop();
        final int baseRight = safeContent.getPaddingRight();
        final int baseBottom = safeContent.getPaddingBottom();
        final boolean ignoreHorizontalInsets = isCompactPhoneLandscape();
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Keep the home layout stable while an immersive dialog temporarily hides
                // the bars. The background still draws edge-to-edge, but safe content keeps
                // the same padding before, during, and after the dialog transition.
                android.graphics.Insets insets = windowInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetLeft = insets.left;
                insetTop = insets.top;
                insetRight = insets.right;
                insetBottom = insets.bottom;
            }
            else {
                insetLeft = windowInsets.getStableInsetLeft();
                insetTop = windowInsets.getStableInsetTop();
                insetRight = windowInsets.getStableInsetRight();
                insetBottom = windowInsets.getStableInsetBottom();
            }
            if (ignoreHorizontalInsets) {
                insetLeft = 0;
                insetRight = 0;
            }
            insetContent.setPadding(
                    baseLeft + insetLeft,
                    baseTop + insetTop,
                    baseRight + insetRight,
                    baseBottom + insetBottom);
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    private boolean isTelevision() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private boolean isCompactPhoneLandscape() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && configuration.smallestScreenWidthDp < 600
                && !isTelevision();
    }

    private void updateDeviceCard() {
        TextView title = findViewById(R.id.homeDeviceTitle);
        TextView subtitle = findViewById(R.id.homeDeviceSubtitle);
        if (title == null || subtitle == null) {
            return;
        }

        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim();
        String model = Build.MODEL == null ? Build.DEVICE : Build.MODEL.trim();
        if (!manufacturer.isEmpty()
                && !model.toLowerCase(Locale.ROOT).startsWith(manufacturer.toLowerCase(Locale.ROOT))) {
            model = Character.toUpperCase(manufacturer.charAt(0)) + manufacturer.substring(1) + " " + model;
        }
        title.setText(model);
        subtitle.setText(getString(
                R.string.home_device_version_format,
                Build.VERSION.RELEASE,
                getKernelVersion()));
    }

    private String getKernelVersion() {
        String kernelVersion = System.getProperty("os.version", "-").trim();
        if (kernelVersion.isEmpty()) {
            return "-";
        }

        int suffixStart = kernelVersion.indexOf('-');
        if (suffixStart > 0) {
            kernelVersion = kernelVersion.substring(0, suffixStart);
        }
        return kernelVersion;
    }

    private void registerInputDeviceListener() {
        if (inputManager != null && !inputListenerRegistered) {
            inputManager.registerInputDeviceListener(inputDeviceListener, null);
            inputListenerRegistered = true;
        }
    }

    private void unregisterInputDeviceListener() {
        if (inputManager != null && inputListenerRegistered) {
            inputManager.unregisterInputDeviceListener(inputDeviceListener);
            inputListenerRegistered = false;
        }
    }

    private void updateControllerCard() {
        if (controllerCarousel == null) {
            return;
        }

        List<HomeControllerCarouselView.ControllerItem> controllers = new ArrayList<>();
        Set<String> descriptors = new HashSet<>();
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null || device.isVirtual()
                    || !ControllerHandler.isGamepadWithJoystickAxes(device)) {
                continue;
            }
            String descriptor = device.getDescriptor();
            String key = descriptor == null ? String.valueOf(deviceId) : descriptor;
            if (descriptors.add(key)) {
                controllers.add(new HomeControllerCarouselView.ControllerItem(
                        key,
                        getControllerDisplayName(device),
                        getString(
                                R.string.home_controller_device_detail,
                                formatUsbId(device.getVendorId()),
                                formatUsbId(device.getProductId()),
                                getControllerTypeDisplayName(device)),
                        supportsControllerVibration(device)));
            }
        }
        controllerCarousel.setControllers(controllers, selectedControllerKey);
    }

    private static boolean supportsControllerVibration(InputDevice device) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = device.getVibratorManager();
                int[] vibratorIds = vibratorManager == null
                        ? new int[0] : vibratorManager.getVibratorIds();
                if (vibratorManager != null
                        && (vibratorIds.length == 2 || vibratorIds.length == 4)) {
                    boolean amplitudeControlled = true;
                    for (int vibratorId : vibratorIds) {
                        if (!vibratorManager.getVibrator(vibratorId).hasAmplitudeControl()) {
                            amplitudeControlled = false;
                            break;
                        }
                    }
                    if (amplitudeControlled) {
                        return true;
                    }
                }
            }
            return device.getVibrator() != null && device.getVibrator().hasVibrator();
        }
        catch (RuntimeException ignored) {
            // Some vendor input services throw while querying vibrator capabilities.
            return false;
        }
    }

    private String getControllerDisplayName(InputDevice inputDevice) {
        String displayName = inputDevice.getName();
        UsbManager usbManager = (UsbManager) getSystemService(USB_SERVICE);
        if (usbManager != null) {
            try {
                for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
                    if (usbDevice.getVendorId() == inputDevice.getVendorId()
                            && usbDevice.getProductId() == inputDevice.getProductId()
                            && !TextUtils.isEmpty(usbDevice.getProductName())) {
                        displayName = usbDevice.getProductName();
                        break;
                    }
                }
            }
            catch (SecurityException ignored) {
                // Some devices hide USB string descriptors until permission is granted.
            }
        }

        return removeDuplicatedControllerBrand(displayName);
    }

    private static String removeDuplicatedControllerBrand(String rawName) {
        if (TextUtils.isEmpty(rawName)) {
            return "Gamepad";
        }

        String cleanedName = rawName.trim().replaceAll("\\s+", " ");
        String[] words = cleanedName.split(" ");
        for (int prefixLength = words.length / 2; prefixLength >= 1; prefixLength--) {
            boolean duplicated = true;
            for (int index = 0; index < prefixLength; index++) {
                if (!words[index].equalsIgnoreCase(words[index + prefixLength])) {
                    duplicated = false;
                    break;
                }
            }
            if (duplicated) {
                return TextUtils.join(
                        " ", Arrays.copyOfRange(words, prefixLength, words.length));
            }
        }
        return cleanedName;
    }

    private static String formatUsbId(int id) {
        return String.format(Locale.ROOT, "%04X", id & 0xFFFF);
    }

    private static String getControllerTypeDisplayName(InputDevice inputDevice) {
        int vendorId = inputDevice.getVendorId();
        int productId = inputDevice.getProductId();
        byte type;
        switch (vendorId) {
            case 0x045e:
                type = MoonBridge.LI_CTYPE_XBOX;
                break;
            case 0x054c:
                type = MoonBridge.LI_CTYPE_PS;
                break;
            case 0x057e:
                type = MoonBridge.LI_CTYPE_NINTENDO;
                break;
            default:
                type = MoonBridge.guessControllerType(vendorId, productId);
                break;
        }

        if (type == MoonBridge.LI_CTYPE_XBOX) {
            return "Xbox";
        }
        if (type == MoonBridge.LI_CTYPE_PS) {
            return "DS";
        }
        if (type == MoonBridge.LI_CTYPE_NINTENDO) {
            return "NS";
        }

        String deviceName = inputDevice.getName() == null
                ? "" : inputDevice.getName().toLowerCase(Locale.ROOT);
        if (deviceName.contains("xbox") || deviceName.contains("xinput")) {
            return "Xbox";
        }
        if (deviceName.contains("dualsense") || deviceName.contains("dualshock")
                || deviceName.contains("playstation")) {
            return "DS";
        }
        if (deviceName.contains("nintendo") || deviceName.contains("switch")
                || deviceName.contains("joy-con")) {
            return "NS";
        }
        return "HID";
    }

    private void dismissPairingDialog() {
        if (pairingDialog != null) {
            pairingDialog.dismiss();
            pairingDialog = null;
        }
    }

    public static class ComputerObject {
        public ComputerDetails details;

        public ComputerObject(ComputerDetails details) {
            if (details == null) {
                throw new IllegalArgumentException("details must not be null");
            }
            this.details = details;
        }

        @Override
        public String toString() {
            return details.name;
        }
    }

}
