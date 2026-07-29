package com.limelight;


import android.Manifest;
import com.limelight.binding.PlatformBinding;
import com.limelight.binding.audio.AndroidAudioRenderer;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.GameInputDevice;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.binding.input.haptics.OptionalPcmHapticsLoader;
import com.limelight.binding.input.capture.InputCaptureManager;
import com.limelight.binding.input.capture.InputCaptureProvider;
import com.limelight.binding.input.touch.AbsoluteTouchContext;
import com.limelight.binding.input.touch.AbsoluteTouchSwitchContext;
import com.limelight.binding.input.touch.RelativeTouchContext;
import com.limelight.binding.input.driver.UsbDriverService;
import com.limelight.binding.input.evdev.EvdevListener;
import com.limelight.binding.input.touch.RelativeTouchSwitchContext;
import com.limelight.binding.input.touch.TouchContext;
import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardController;
import com.limelight.binding.input.virtual_controller.keyboard.KeyBoardLayoutController;
import com.limelight.binding.video.CrashListener;
import com.limelight.binding.video.MediaCodecDecoderRenderer;
import com.limelight.binding.video.MediaCodecHelper;
import com.limelight.binding.video.PerfOverlayListener;
import com.limelight.binding.video.PerfOverlayStats;
import com.limelight.fsr.FsrVideoProcessor;
import com.limelight.fsr.VideoProcessingGLSurfaceView;
import com.limelight.nvstream.MicUplinkConnection;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.haptics.PcmHapticsBackend;
import com.limelight.log.StreamSessionLogger;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.gamemenu.GameMenuFragment;
import com.limelight.ui.AppDialog;
import com.limelight.ui.GameGestures;
import com.limelight.ui.StreamView;
import com.limelight.ui.StreamLoadingOverlayController;
import com.limelight.ui.virtualmouse.RemoteMouseSink;
import com.limelight.ui.virtualmouse.VirtualMouseController;
import com.limelight.ui.virtualmouse.VirtualMouseOverlay;
import com.limelight.ui.video.VideoZoomController;
import com.limelight.ui.video.VideoZoomGestureOverlay;
import com.limelight.ui.floatingview.AXFloatingMagnetView;
import com.limelight.ui.floatingview.AXFloatingView;
import com.limelight.ui.floatingview.AXFloatingViewListener;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.RazerUtils;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Rational;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnGenericMotionListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class Game extends Activity implements SurfaceHolder.Callback,
        OnGenericMotionListener, OnTouchListener, NvConnectionListener, EvdevListener,
        OnSystemUiVisibilityChangeListener, GameGestures, StreamView.InputCallbacks,
        PerfOverlayListener, UsbDriverService.UsbDriverStateListener, View.OnKeyListener{
    private static final float EXTERNAL_TOUCHPAD_SCROLL_FACTOR = 0.15f;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 1001;
    public static Game instance;

    private int lastButtonState = 0;
    private float externalTouchpadScrollRemainderX = 0f;
    private float externalTouchpadScrollRemainderY = 0f;

    // Only 2 touches are supported
    private final TouchContext[] touchContextMap = new TouchContext[2];
    private long threeFingerDownTime = 0;

    private static final int REFERENCE_HORIZ_RES = 1280;
    private static final int REFERENCE_VERT_RES = 720;

    private static final int STYLUS_DOWN_DEAD_ZONE_DELAY = 100;
    private static final int STYLUS_DOWN_DEAD_ZONE_RADIUS = 20;

    private static final int STYLUS_UP_DEAD_ZONE_DELAY = 150;
    private static final int STYLUS_UP_DEAD_ZONE_RADIUS = 50;

    private static final int THREE_FINGER_TAP_THRESHOLD = 300;

    private ControllerHandler controllerHandler;
    private KeyboardTranslator keyboardTranslator;
    private KeyBoardController virtualController;

    private KeyBoardController keyBoardController;

    private KeyBoardLayoutController keyBoardLayoutController;

    public PreferenceConfiguration prefConfig;
    private SharedPreferences tombstonePrefs;

    private NvConnection conn;
    private StreamLoadingOverlayController streamLoadingController;
    private boolean displayedFailureDialog = false;
    private boolean connecting = false;
    public boolean connected = false;
    private boolean awaitingRecordAudioPermission = false;
    private boolean autoEnterPip = false;
    private boolean surfaceCreated = false;
    private boolean attemptedConnection = false;
    private int suppressPipRefCount = 0;
    private String pcName;
    private String appName;
    private String streamHost;
    private long streamStartElapsedMs;
    private StreamSessionLogger streamSessionLogger;
    private long lastSessionPerfLogElapsedMs;
    private int lastLoggedConnectionStatus = Integer.MIN_VALUE;
    private NvApp app;
    private float desiredRefreshRate;

    private InputCaptureProvider inputCaptureProvider;
    private int modifierFlags = 0;
    private boolean grabbedInput = true;
    private boolean cursorVisible = false;
    private boolean waitingForAllModifiersUp = false;
    private int specialKeyCode = KeyEvent.KEYCODE_UNKNOWN;
    private StreamView streamView;
    private VideoProcessingGLSurfaceView fsrView;
    private FsrVideoProcessor fsrVideoProcessor;
    private VirtualMouseOverlay virtualMouseOverlay;
    private VideoZoomController videoZoomController;
    private VideoZoomGestureOverlay videoZoomGestureOverlay;
    private final PointF videoZoomTapPoint = new PointF();
    private final RectF videoZoomBaseRect = new RectF();
    private long lastAbsTouchUpTime = 0;
    private long lastAbsTouchDownTime = 0;
    private float lastAbsTouchUpX, lastAbsTouchUpY;
    private float lastAbsTouchDownX, lastAbsTouchDownY;

    private boolean isHidingOverlays;
    private TextView notificationOverlayView;
    private int requestedNotificationOverlayVisibility = View.GONE;
    private View performanceOverlayView;

    private TextView performanceOverlayLite;

    private LinearLayout performanceOverlayBig;
    private LinearLayout performanceOverlayBigContent;

    private MediaCodecDecoderRenderer decoderRenderer;
    private AndroidAudioRenderer audioRenderer;
    private boolean reportedCrash;
    private boolean micToggleInFlight;
    private boolean pendingMicToggleAfterPermission;

    private WifiManager.WifiLock highPerfWifiLock;
    private WifiManager.WifiLock lowLatencyWifiLock;

    private boolean connectedToUsbDriverService = false;
    private ServiceConnection usbDriverServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            UsbDriverService.UsbDriverBinder binder = (UsbDriverService.UsbDriverBinder) iBinder;
            binder.setListener(controllerHandler);
            binder.setStateListener(Game.this);
            binder.start();
            connectedToUsbDriverService = true;
            logSessionInfo("USB", "USB 手柄服务已连接");
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            connectedToUsbDriverService = false;
            logSessionWarn("USB", "USB 手柄服务意外断开");
        }
    };

    public static final String EXTRA_HOST = "Host";
    public static final String EXTRA_PORT = "Port";
    public static final String EXTRA_HTTPS_PORT = "HttpsPort";
    public static final String EXTRA_APP_NAME = "AppName";
    public static final String EXTRA_APP_ID = "AppId";
    public static final String EXTRA_UNIQUEID = "UniqueId";
    public static final String EXTRA_PC_UUID = "UUID";
    public static final String EXTRA_PC_NAME = "PcName";
    public static final String EXTRA_APP_HDR = "HDR";
    public static final String EXTRA_SERVER_CERT = "ServerCert";

    private ViewParent rootView;

    private StreamReqBean streamReqBean;
    private ConnectivityManager connManager;

    private TextView performanceRumble;
    private boolean glesRenderingEnabled;
    private boolean fsrEnabled;
    private boolean stereo3dEnabled;
    private boolean fsrInputSurfaceReady;
    private boolean fsrDisplaySurfaceCreated;
    private Surface fsrInputSurface;
    private boolean usbPermissionPromptVisible;
    private boolean fsrViewLifecyclePaused;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance=this;

        UiHelper.setLocale(this);

        // We don't want a title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Full-screen
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // If we're going to use immersive mode, we want to have
        // the entire screen
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);

        // Listen for UI visibility events
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(this);

        // Change volume button behavior
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Inflate the content
        setContentView(R.layout.activity_game);

        streamLoadingController = new StreamLoadingOverlayController(
                this,
                () -> {
                    if (!usbPermissionPromptVisible) {
                        finish();
                    }
                },
                () -> {
                    HelpLauncher.launchTroubleshooting(this);
                    finish();
                });

        connManager=(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // Read the stream preferences
        prefConfig = PreferenceConfiguration.readPreferences(this);
        appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        pcName = getIntent().getStringExtra(EXTRA_PC_NAME);
        streamSessionLogger = StreamSessionLogger.create(this, prefConfig, pcName, appName);
        logSessionInfo("LIFECYCLE", "串流页面已创建");
        tombstonePrefs = Game.this.getSharedPreferences("DecoderTombstone", 0);

        // Enter landscape unless we're on a square screen
        setPreferredOrientationForCurrentDisplay();

        if (prefConfig.stretchVideo || prefConfig.enableCutoutModeVideo || shouldIgnoreInsetsForResolution(prefConfig.width, prefConfig.height)) {
            // Allow the activity to layout under notches if the fill-screen option
            // was turned on by the user or it's a full-screen native resolution
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
        }
        // Listen for non-touch events on the game surface
        streamView = findViewById(R.id.surfaceView);
        streamView.setOnGenericMotionListener(this);
        streamView.setOnKeyListener(this);
        streamView.setInputCallbacks(this);

        stereo3dEnabled = isStereo3dEnabled();
        glesRenderingEnabled = isGlesRenderingEnabled();
        fsrEnabled = isFsrEnabled();
        configureGlesWindowColorMode();

        performanceRumble=findViewById(R.id.performanceRumble);
        switchPerformanceRumbleHUD();

//        //串流画面 顶部居中显示
//        if(prefConfig.enableDisplayTopCenter){
//            FrameLayout.LayoutParams params= (FrameLayout.LayoutParams) streamView.getLayoutParams();
//            params.gravity= Gravity.CENTER_HORIZONTAL|Gravity.TOP;
//        }
        //串流画面 顶部居中显示
        FrameLayout.LayoutParams params= (FrameLayout.LayoutParams) streamView.getLayoutParams();
        int gravityModel=Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString("screen_gravity_list", "0"));
        switch (gravityModel){
            case 1://顶部居中
                params.gravity= Gravity.CENTER_HORIZONTAL|Gravity.TOP;
                break;
            case 2://顶部居左
                params.gravity= Gravity.LEFT|Gravity.TOP;
                break;
            case 3://顶部居右
                params.gravity= Gravity.RIGHT|Gravity.TOP;
                break;
            case 4://底部居中
                params.gravity= Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM;
                break;
            case 5://底部居左
                params.gravity= Gravity.LEFT|Gravity.BOTTOM;
                break;
            case 6://底部居右
                params.gravity= Gravity.RIGHT|Gravity.BOTTOM;
                break;
        }

        if (glesRenderingEnabled) {
            fsrVideoProcessor = new FsrVideoProcessor(this);
            fsrVideoProcessor.setSharpness(getFsrSharpness());
            fsrVideoProcessor.setFsrEnabled(fsrEnabled);
            fsrVideoProcessor.setStereo3dEnabled(stereo3dEnabled);
            fsrVideoProcessor.setStereo3dDepthStrength(getStereo3dDepthStrength());
            fsrVideoProcessor.setStereo3dConvergence(getStereo3dConvergence());
            fsrVideoProcessor.setStereo3dSwapEyes(prefConfig.stereo3dSwapEyes);
            if (stereo3dEnabled) {
                int[] stereoSceneSize = fsrEnabled
                        ? getFsrOutputSize()
                        : new int[] {1920, 1080};
                fsrVideoProcessor.setStereoSceneSize(stereoSceneSize[0], stereoSceneSize[1]);
            }
            fsrView = new VideoProcessingGLSurfaceView(this, false, isGlesNativeHdrOutputEnabled(), fsrVideoProcessor,
                    new VideoProcessingGLSurfaceView.SurfaceListener() {
                        @Override
                        public void onInputSurfaceAvailable(android.graphics.SurfaceTexture surfaceTexture) {
                            if (fsrInputSurface != null) {
                                fsrInputSurface.release();
                            }
                            fsrInputSurface = new Surface(surfaceTexture);
                            fsrInputSurfaceReady = true;
                            if (attemptedConnection) {
                                decoderRenderer.setRenderTarget(fsrInputSurface);
                            }
                            startConnectionIfReady();
                        }

                        @Override
                        public void onInputSurfaceDestroyed() {
                            fsrInputSurfaceReady = false;
                            if (fsrInputSurface != null) {
                                fsrInputSurface.release();
                                fsrInputSurface = null;
                            }
                        }
                    });
            fsrView.setFocusable(false);
            fsrView.setFocusableInTouchMode(false);
            fsrView.setClickable(false);

            FrameLayout.LayoutParams fsrLayoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            fsrLayoutParams.gravity = params.gravity;
            fsrView.setLayoutParams(fsrLayoutParams);

            ViewGroup parent = (ViewGroup) streamView.getParent();
            int streamIndex = parent.indexOfChild(streamView);
            parent.addView(fsrView, streamIndex + 1);

            streamView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
            streamView.setZOrderMediaOverlay(true);
            fsrView.getHolder().addCallback(this);
            fsrView.setFrameInputSize(prefConfig.width, prefConfig.height);
            if (stereo3dEnabled) {
                // Keep full-SBS as the logical layout, but let the actual display window own
                // the buffer size. A 3840x1080 AR display will still produce a native surface,
                // while handset previews no longer allocate an oversized off-screen buffer.
                fsrView.setFixedSurfacePixelSize(0, 0);
                fsrView.setMaxRenderFps(60);
            }
            else if (fsrEnabled) {
                int[] fsrOutputSize = getFsrOutputSize();
                fsrView.setFixedSurfacePixelSize(fsrOutputSize[0], fsrOutputSize[1]);
            }
        }

        // Listen for touch events on the background touch view to enable trackpad mode
        // to work on areas outside of the StreamView itself. We use a separate View
        // for this rather than just handling it at the Activity level, because that
        // allows proper touch splitting, which the OSC relies upon.
        View backgroundTouchView = findViewById(R.id.backgroundTouchView);
        backgroundTouchView.setOnTouchListener(this);

        rootView=streamView.getParent();

        videoZoomGestureOverlay = findViewById(R.id.videoZoomGestureOverlay);
        videoZoomController = new VideoZoomController((View) rootView, streamView, fsrView);
        videoZoomGestureOverlay.bind(videoZoomController, this::handleVideoZoomTap);

        virtualMouseOverlay = findViewById(R.id.virtualMouseOverlay);
        virtualMouseOverlay.bind(createVirtualMouseController(), new VirtualMouseOverlay.PresentationHost() {
            @Override
            public void getBaseVideoRect(RectF outRect) {
                getVirtualMouseBaseVideoRect(outRect);
            }

            @Override
            public void setVirtualMouseVideoOffset(float offsetXPx, float offsetYPx) {
                applyVirtualMouseVideoOffset(offsetXPx, offsetYPx);
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Request unbuffered input event dispatching for all input classes we handle here.
            // Without this, input events are buffered to be delivered in lock-step with VBlank,
            // artificially increasing input latency while streaming.
            streamView.requestUnbufferedDispatch(
                    InputDevice.SOURCE_CLASS_BUTTON | // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK | // Gamepads
                    InputDevice.SOURCE_CLASS_POINTER | // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION | // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            );
            backgroundTouchView.requestUnbufferedDispatch(
                    InputDevice.SOURCE_CLASS_BUTTON | // Keyboards
                    InputDevice.SOURCE_CLASS_JOYSTICK | // Gamepads
                    InputDevice.SOURCE_CLASS_POINTER | // Touchscreens and mice (w/o pointer capture)
                    InputDevice.SOURCE_CLASS_POSITION | // Touchpads
                    InputDevice.SOURCE_CLASS_TRACKBALL // Mice (pointer capture)
            );
            videoZoomGestureOverlay.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN);
            virtualMouseOverlay.requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER);
        }

        notificationOverlayView = findViewById(R.id.notificationOverlay);

        performanceOverlayView = findViewById(R.id.performanceOverlay);

        performanceOverlayLite = findViewById(R.id.performanceOverlayLite);

        performanceOverlayBig = findViewById(R.id.performanceOverlayBig);
        performanceOverlayBigContent = findViewById(R.id.performanceOverlayBigContent);

        inputCaptureProvider = InputCaptureManager.getInputCaptureProvider(this, this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            streamView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                @Override
                public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
//                    LimeLog.info("onCapturedPointer="+motionEvent.toString());
//                    LimeLog.info("onCapturedPointer-Device="+motionEvent.getDevice().toString());
                    return handleMotionEvent(view, motionEvent);
                }
            });
        }

        // Warn the user if they're on a metered connection
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connMgr.isActiveNetworkMetered()) {
            displayTransientMessage(getResources().getString(R.string.conn_metered));
        }

        // Make sure Wi-Fi is fully powered up
        WifiManager wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        try {
            highPerfWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Moonlight High Perf Lock");
            highPerfWifiLock.setReferenceCounted(false);
            highPerfWifiLock.acquire();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lowLatencyWifiLock = wifiMgr.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Moonlight Low Latency Lock");
                lowLatencyWifiLock.setReferenceCounted(false);
                lowLatencyWifiLock.acquire();
            }
        } catch (SecurityException e) {
            // Some Samsung Galaxy S10+/S10e devices throw a SecurityException from
            // WifiLock.acquire() even though we have android.permission.WAKE_LOCK in our manifest.
            e.printStackTrace();
        }

        String host = Game.this.getIntent().getStringExtra(EXTRA_HOST);
        streamHost = host;
        int port = Game.this.getIntent().getIntExtra(EXTRA_PORT, NvHTTP.DEFAULT_HTTP_PORT);
        int httpsPort = Game.this.getIntent().getIntExtra(EXTRA_HTTPS_PORT, 0); // 0 is treated as unknown
        int appId = Game.this.getIntent().getIntExtra(EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID);
        String uniqueId = Game.this.getIntent().getStringExtra(EXTRA_UNIQUEID);
        boolean appSupportsHdr = Game.this.getIntent().getBooleanExtra(EXTRA_APP_HDR, false);
        byte[] derCertData = Game.this.getIntent().getByteArrayExtra(EXTRA_SERVER_CERT);

        app = new NvApp(appName != null ? appName : "app", appId, appSupportsHdr);

        X509Certificate serverCert = null;
        try {
            if (derCertData != null) {
                serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        if (appId == StreamConfiguration.INVALID_APP_ID) {
            finish();
            return;
        }

        // Initialize the MediaCodec helper before creating the decoder
        GlPreferences glPrefs = GlPreferences.readPreferences(this);
        MediaCodecHelper.initialize(this, glPrefs.glRenderer);

        // Check if the user has enabled HDR
        boolean willStreamHdr = false;
        if(prefConfig.ignoreCheckHDR){
            willStreamHdr=true;
        }else{
            if (prefConfig.enableHdr) {
                // Start our HDR checklist
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Display display = getWindowManager().getDefaultDisplay();
                    Display.HdrCapabilities hdrCaps = display.getHdrCapabilities();

                    // We must now ensure our display is compatible with HDR10
                    if (hdrCaps != null) {
                        // getHdrCapabilities() returns null on Lenovo Lenovo Mirage Solo (vega), Android 8.0
                        for (int hdrType : hdrCaps.getSupportedHdrTypes()) {
                            if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                                willStreamHdr = true;
                                break;
                            }
                        }
                    }

                    if (!willStreamHdr) {
                        // Nope, no HDR for us :(
                        Toast.makeText(this, "Display does not support HDR10", Toast.LENGTH_LONG).show();
                    }
                }
                else {
                    Toast.makeText(this, "HDR requires Android 7.0 or later", Toast.LENGTH_LONG).show();
                }
            }
        }

        if (stereo3dEnabled && willStreamHdr) {
            willStreamHdr = false;
            Toast.makeText(this, "3D输出首版使用SDR串流，已临时关闭HDR", Toast.LENGTH_LONG).show();
            logSessionWarn("VIDEO", "SBS 3D 首版禁用 HDR 串流");
        }

        // Check if the user has enabled performance stats overlay
        if (prefConfig.enablePerfOverlay) {
            performanceOverlayView.setVisibility(View.VISIBLE);
            if(prefConfig.enablePerfOverlayLite){
                performanceOverlayLite.setVisibility(View.VISIBLE);
            }else{
                performanceOverlayBig.setVisibility(View.VISIBLE);
            }
        }
        setPerformanceOverlayLiteMagin();
        performanceOverlayLite.setOnClickListener(v -> {
            if(prefConfig.enablePerfOverlayLiteDialog){
                showGameMenu(null);
            }
        });

        performanceOverlayLite.setClickable(prefConfig.enablePerfOverlayLiteDialog);
        setPerformanceOverlayZoom();

        decoderRenderer = new MediaCodecDecoderRenderer(
                this,
                prefConfig,
                new CrashListener() {
                    @Override
                    public void notifyCrash(Exception e) {
                        // The MediaCodec instance is going down due to a crash
                        // let's tell the user something when they open the app again

                        // We must use commit because the app will crash when we return from this function
                        tombstonePrefs.edit().putInt("CrashCount", tombstonePrefs.getInt("CrashCount", 0) + 1).commit();
                        reportedCrash = true;
                        logSessionError("DECODER", "MediaCodec 解码器异常", e);
                    }
                },
                tombstonePrefs.getInt("CrashCount", 0),
                connMgr.isActiveNetworkMetered(),
                willStreamHdr,
                glPrefs.glRenderer,
                this);
        decoderRenderer.setFirstFrameListener(() -> {
            logSessionInfo("VIDEO", "首帧已显示");
            streamLoadingController.onFirstFrameRendered();
            if (controllerHandler != null) {
                controllerHandler.notifyStreamReady();
            }
        });

        // Don't stream HDR if the decoder can't support it
        if (willStreamHdr && !decoderRenderer.isHevcMain10Hdr10Supported() && !decoderRenderer.isAv1Main10Supported()) {
            willStreamHdr = false;
            Toast.makeText(this, "Decoder does not support HDR10 profile", Toast.LENGTH_LONG).show();
        }
        // Display a message to the user if HEVC was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_HEVC && !decoderRenderer.isHevcSupported()) {
            Toast.makeText(this, "No HEVC decoder found", Toast.LENGTH_LONG).show();
        }

        // Display a message to the user if AV1 was forced on but we still didn't find a decoder
        if (prefConfig.videoFormat == PreferenceConfiguration.FormatOption.FORCE_AV1 && !decoderRenderer.isAv1Supported()) {
            Toast.makeText(this, "No AV1 decoder found", Toast.LENGTH_LONG).show();
        }

        // H.264 is always supported
        int supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264;
        if (decoderRenderer.isHevcSupported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265;
            if (willStreamHdr && decoderRenderer.isHevcMain10Hdr10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_H265_MAIN10;
            }
        }
        if (decoderRenderer.isAv1Supported()) {
            supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN8;
            if (willStreamHdr && decoderRenderer.isAv1Main10Supported()) {
                supportedVideoFormats |= MoonBridge.VIDEO_FORMAT_AV1_MAIN10;
            }
        }

        int gamepadMask = ControllerHandler.getAttachedControllerMask(this);
        if (!prefConfig.multiController) {
            // Always set gamepad 1 present for when multi-controller is
            // disabled for games that don't properly support detection
            // of gamepads removed and replugged at runtime.
            gamepadMask = 1;
        }
        if (prefConfig.onscreenController) {
            // If we're using OSC, always set at least gamepad 1.
            gamepadMask |= 1;
        }

        // Set to the optimal mode for streaming
        float displayRefreshRate = prepareDisplayForRendering();
        LimeLog.info("Display refresh rate: "+displayRefreshRate);

        // If the user requested frame pacing using a capped FPS, we will need to change our
        // desired FPS setting here in accordance with the active display refresh rate.
        int roundedRefreshRate = Math.round(displayRefreshRate);
        int chosenFrameRate = prefConfig.fps;
        if (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS) {
            if (prefConfig.fps >= roundedRefreshRate) {
                if (prefConfig.fps > roundedRefreshRate + 3) {
                    // Use frame drops when rendering above the screen frame rate
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Using drop mode for FPS > Hz");
                } else if (roundedRefreshRate <= 49) {
                    // Let's avoid clearly bogus refresh rates and fall back to legacy rendering
                    prefConfig.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED;
                    LimeLog.info("Bogus refresh rate: " + roundedRefreshRate);
                }
                else {
                    chosenFrameRate = roundedRefreshRate - 1;
                    LimeLog.info("Adjusting FPS target for screen to " + chosenFrameRate);
                }
            }
        }

        StreamConfiguration config = new StreamConfiguration.Builder()
                .setResolution(prefConfig.width, prefConfig.height)
                .setLaunchRefreshRate(prefConfig.fps)
                .setRefreshRate(chosenFrameRate)
                .setApp(app)
                .setBitrate(prefConfig.bitrate)
                .setEnableSops(prefConfig.enableSops)
                .enableLocalAudioPlayback(prefConfig.playHostAudio)
                .setMaxPacketSize(1392)
                .setRemoteConfiguration(StreamConfiguration.STREAM_CFG_AUTO) // NvConnection will perform LAN and VPN detection
                .setSupportedVideoFormats(supportedVideoFormats)
                .setAttachedGamepadMask(gamepadMask)
                .setClientRefreshRateX100((int)(displayRefreshRate * 100))
                .setAudioConfiguration(prefConfig.audioConfiguration)
                .setColorSpace(decoderRenderer.getPreferredColorSpace())
                .setColorRange(decoderRenderer.getPreferredColorRange())
                .setPPI(RazerUtils.getPPI(this))
                .setRazerVD(prefConfig.razerVD)
                .setPersistGamepadsAfterDisconnect(!prefConfig.multiController)
                .build();

        streamReqBean=new StreamReqBean();
        streamReqBean.setAppName(appName);
        streamReqBean.setServerCert(serverCert);
        streamReqBean.setHttpsPort(httpsPort);
        streamReqBean.setUniqueId(uniqueId);
        streamReqBean.setActiveAddress(new ComputerDetails.AddressTuple(host, port));
        streamReqBean.setCryptoProvider(PlatformBinding.getCryptoProvider(this));
        // Initialize the connection
        conn = new NvConnection(getApplicationContext(),
                new ComputerDetails.AddressTuple(host, port),
                httpsPort, uniqueId, config,
                PlatformBinding.getCryptoProvider(this), serverCert);
        startConnectionIfReady();
        PcmHapticsBackend optionalPcmHapticsBackend = OptionalPcmHapticsLoader.load(this,
                new PcmHapticsBackend.Callback() {
                    @Override
                    public void onUsbPermissionPromptStarting() {
                        Game.this.onUsbPermissionPromptStarting();
                    }

                    @Override
                    public void onUsbPermissionPromptCompleted() {
                        Game.this.onUsbPermissionPromptCompleted();
                    }

                    @Override
                    public void onAvailabilityChanged(boolean active) {
                        LimeLog.info("Kishi PCM haptics " + (active ? "active" : "inactive"));
                        logSessionInfo("HAPTICS", "Kishi 原生触觉通道" + (active ? "已启用" : "已停用"));
                    }

                    @Override
                    public void onSessionLogEvent(String level, String category, String message) {
                        if ("ERROR".equalsIgnoreCase(level)) {
                            logSessionError(category, message, null);
                        }
                        else if ("WARN".equalsIgnoreCase(level)) {
                            logSessionWarn(category, message);
                        }
                        else {
                            logSessionInfo(category, message);
                        }
                    }
                });
        controllerHandler = new ControllerHandler(this, conn, this, prefConfig,
                optionalPcmHapticsBackend);
        keyboardTranslator = new KeyboardTranslator();

        InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        inputManager.registerInputDeviceListener(keyboardTranslator, null);

        // Initialize touch contexts
//        for (int i = 0; i < touchContextMap.length; i++) {
//            if (!prefConfig.touchscreenTrackpad) {
//                touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
//            }
//            else {
//                touchContextMap[i] = new RelativeTouchContext(conn, i,
//                        REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
//                        streamView, prefConfig);
//            }
//        }
        //鼠标触控模式
        String mouseModel=PreferenceManager.getDefaultSharedPreferences(this).getString("mouse_model_list_axi", "0");
        switchMouseModel(Integer.parseInt(mouseModel));

        if (prefConfig.onscreenController) {
            // create virtual onscreen controller
            initVirtualController();
        }

        //特殊按键屏幕布局
        if(prefConfig.enableKeyboard){
            initKeyboardController();
        }

        if (prefConfig.usbDriver) {
            // Start the USB driver
            bindService(new Intent(this, UsbDriverService.class),
                    usbDriverServiceConnection, Service.BIND_AUTO_CREATE);
        }

        //悬浮球
        if(prefConfig.enableAXFloating){
            initFloatingView();
        }

        if (!decoderRenderer.isAvcSupported()) {
            // If we can't find an AVC decoder, we can't proceed
            streamLoadingController.showFailure(
                    "This device or ROM doesn't support hardware accelerated H.264 playback.");
            return;
        }

        // The connection will be started when the surface gets created
        if (!glesRenderingEnabled) {
            streamView.getHolder().addCallback(this);
        }

        //外接显示器模式
        if(prefConfig.enableExDisplay){
            showSecondScreen();
        }

        //强制体感
        setMotionForceGyro();

        setPerformanceOverlayLiteMagin();

        addPerformanceOverlayLiteLeftIcon();

        //光标是否显示
        if(!cursorVisible&&prefConfig.enableMouseLocalCursor){
            switchMouseLocalCursor();
        }
//        cursorVisible=prefConfig.enableMouseLocalCursor;
//        initFloatingView();

    }

    private void addPerformanceOverlayLiteLeftIcon(){
        NetworkInfo networkInfo=connManager.getActiveNetworkInfo();
        if(networkInfo==null){
            return;
        }
        Drawable drawable = getResources().getDrawable(R.drawable.icon_axi_wifi);
        if(networkInfo.getType() == ConnectivityManager.TYPE_MOBILE){
            drawable = getResources().getDrawable(R.drawable.icon_axi_mobile);
        }
        int textSize = (int) performanceOverlayLite.getTextSize();
        // 设置 Drawable 的宽和高与文字大小一致（或者按比例，如 0.8f）
        drawable.setBounds(0, 0, textSize, textSize);
        performanceOverlayLite.setCompoundDrawables(drawable, null,null, null);
    }

    private void initKeyboardController(){
        keyBoardController=new KeyBoardController(controllerHandler, (FrameLayout) rootView, this,prefConfig,false);
//        keyBoardController.refreshLayout();
        keyBoardController.show();
    }


    private void initVirtualController(){
        virtualController = new KeyBoardController(controllerHandler,(FrameLayout) rootView, this,prefConfig,true);
//        virtualController.refreshLayout();
        virtualController.show();
    }

    private void initkeyBoardLayoutController(){
        keyBoardLayoutController=new KeyBoardLayoutController(controllerHandler,(FrameLayout)rootView, this,prefConfig);
        keyBoardLayoutController.refreshLayout();
        keyBoardLayoutController.show();
    }

    //显示隐藏虚拟特殊按键
    public void showHideKeyboardController(){
        if(keyBoardController==null){
            initKeyboardController();
            prefConfig.enableKeyboard=true;
            return;
        }
        prefConfig.enableKeyboard=keyBoardController.switchShowHide() != 0;
    }

    public void showHidekeyBoardLayoutController(){
        if(keyBoardLayoutController==null){
            initkeyBoardLayoutController();
            setVirtualMouseInputSuppressed(true);
            setVideoZoomInputSuppressed(true);
            return;
        }
        keyBoardLayoutController.switchShowHide();
        setVirtualMouseInputSuppressed(keyBoardLayoutController.isVisible());
        setVideoZoomInputSuppressed(keyBoardLayoutController.isVisible());
    }

    //显示隐藏虚拟手柄控制器
    public void showHideVirtualController(){
        if(virtualController==null){
            initVirtualController();
            prefConfig.onscreenController=true;
            return;
        }
        prefConfig.onscreenController= virtualController.switchShowHide() != 0;
    }

    private void setPreferredOrientationForCurrentDisplay() {
        Display display = getWindowManager().getDefaultDisplay();

        // For semi-square displays, we use more complex logic to determine which orientation to use (if any)
        if (PreferenceConfiguration.isSquarishScreen(display)) {
            int desiredOrientation = Configuration.ORIENTATION_UNDEFINED;

            // OSC doesn't properly support portrait displays, so don't use it in portrait mode by default
            if (prefConfig.onscreenController) {
                desiredOrientation = Configuration.ORIENTATION_LANDSCAPE;
            }

            // For native resolution, we will lock the orientation to the one that matches the specified resolution
            if (PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height)) {
                if (prefConfig.width > prefConfig.height) {
                    desiredOrientation = Configuration.ORIENTATION_LANDSCAPE;
                }
                else {
                    desiredOrientation = Configuration.ORIENTATION_PORTRAIT;
                }
            }

            if (desiredOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
            }
            else if (desiredOrientation == Configuration.ORIENTATION_PORTRAIT) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT);
            }
            else {
                // If we don't have a reason to lock to portrait or landscape, allow any orientation
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            }
        }
        else {
            //强制竖屏模式
            if(prefConfig.enablePortrait|| isPortrait){
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
                return;
            }
            //解锁横竖屏切换
            if(prefConfig.autoScreenOrientation){
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
                return;
            }
            // For regular displays, we always request landscape
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.cancelActiveInteractions();
        }
        if (videoZoomGestureOverlay != null) {
            videoZoomGestureOverlay.resetTransform();
        }
        super.onConfigurationChanged(newConfig);


        // Set requested orientation for possible new screen size
        setPreferredOrientationForCurrentDisplay();

        if (virtualController != null) {
            // Refresh layout of OSC for possible new screen size
            virtualController.refreshLayout();
        }

        if(keyBoardController !=null){
            keyBoardController.refreshLayout();
        }

        if(keyBoardLayoutController!=null){
            keyBoardLayoutController.refreshLayout();
        }

        // Hide on-screen overlays in PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isInPictureInPictureMode()) {
                isHidingOverlays = true;

                if (virtualController != null) {
                    virtualController.hide();
                    prefConfig.onscreenController=false;
                }

                if (keyBoardController != null) {
                    keyBoardController.hide();
                    prefConfig.enableKeyboard=false;
                }

                if(keyBoardLayoutController!=null){
                    keyBoardLayoutController.hide();
                }

                performanceOverlayView.setVisibility(View.GONE);
                notificationOverlayView.setVisibility(View.GONE);

                // Disable sensors while in PiP mode
                controllerHandler.disableSensors();

                // Update GameManager state to indicate we're in PiP (still gaming, but interruptible)
                UiHelper.notifyStreamEnteringPiP(this);
            }
            else {
                isHidingOverlays = false;

                // Restore overlays to previous state when leaving PiP
//                if (virtualController != null) {
//                    if(!prefConfig.onscreenController){
//                        virtualController.hide();
//                    }
//                }
//
//                if (keyBoardController != null) {
//                    if(!prefConfig.enableKeyboard){
//                        keyBoardController.hide();
//                    }
//                }
                if (prefConfig.enablePerfOverlay) {
                    performanceOverlayView.setVisibility(View.VISIBLE);
                }

                notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);

                // Enable sensors again after exiting PiP
                controllerHandler.enableSensors();

                // Update GameManager state to indicate we're out of PiP (gaming, non-interruptible)
                UiHelper.notifyStreamExitingPiP(this);
            }
        }

        if (virtualMouseOverlay != null) {
            setVirtualMouseInputSuppressed(isHidingOverlays);
            virtualMouseOverlay.post(new Runnable() {
                @Override
                public void run() {
                    virtualMouseOverlay.refreshAfterHostSizeChanged();
                }
            });
        }
        if (videoZoomGestureOverlay != null) {
            setVideoZoomInputSuppressed(isHidingOverlays);
            videoZoomGestureOverlay.post(videoZoomGestureOverlay::refreshAfterHostSizeChanged);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private PictureInPictureParams getPictureInPictureParams(boolean autoEnter) {
        PictureInPictureParams.Builder builder =
                new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(prefConfig.width, prefConfig.height))
                        .setSourceRectHint(new Rect(
                                streamView.getLeft(), streamView.getTop(),
                                streamView.getRight(), streamView.getBottom()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter);
            builder.setSeamlessResizeEnabled(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appName != null) {
                builder.setTitle(appName);
                if (pcName != null) {
                    builder.setSubtitle(pcName);
                }
            }
            else if (pcName != null) {
                builder.setTitle(pcName);
            }
        }

        return builder.build();
    }

    private void updatePipAutoEnter() {
        if (!prefConfig.enablePip) {
            return;
        }

        boolean autoEnter = connected && suppressPipRefCount == 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setPictureInPictureParams(getPictureInPictureParams(autoEnter));
        }
        else {
            autoEnterPip = autoEnter;
        }
    }

    public void setMetaKeyCaptureState(boolean enabled) {
        // This uses custom APIs present on some Samsung devices to allow capture of
        // meta key events while streaming.
        try {
            Class<?> semWindowManager = Class.forName("com.samsung.android.view.SemWindowManager");
            Method getInstanceMethod = semWindowManager.getMethod("getInstance");
            Object manager = getInstanceMethod.invoke(null);

            if (manager != null) {
                Class<?>[] parameterTypes = new Class<?>[2];
                parameterTypes[0] = ComponentName.class;
                parameterTypes[1] = boolean.class;
                Method requestMetaKeyEventMethod = semWindowManager.getDeclaredMethod("requestMetaKeyEvent", parameterTypes);
                requestMetaKeyEventMethod.invoke(manager, this.getComponentName(), enabled);
            }
            else {
                LimeLog.warning("SemWindowManager.getInstance() returned null");
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();

        // PiP is only supported on Oreo and later, and we don't need to manually enter PiP on
        // Android S and later. On Android R, we will use onPictureInPictureRequested() instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (autoEnterPip) {
                try {
                    // This has thrown all sorts of weird exceptions on Samsung devices
                    // running Oreo. Just eat them and close gracefully on leave, rather
                    // than crashing.
                    enterPictureInPictureMode(getPictureInPictureParams(false));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.R)
    public boolean onPictureInPictureRequested() {
        // Enter PiP when requested unless we're on Android 12 which supports auto-enter.
        if (autoEnterPip && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPictureInPictureMode(getPictureInPictureParams(false));
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // We can't guarantee the state of modifiers keys which may have
        // lifted while focus was not on us. Clear the modifier state.
        this.modifierFlags = 0;

        // With Android native pointer capture, capture is lost when focus is lost,
        // so it must be requested again when focus is regained.
        inputCaptureProvider.onWindowFocusChanged(hasFocus);
        if (virtualMouseOverlay != null) {
            setVirtualMouseInputSuppressed(!hasFocus);
        }
        if (videoZoomGestureOverlay != null) {
            setVideoZoomInputSuppressed(!hasFocus);
        }
    }

    private boolean isRefreshRateEqualMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                refreshRate <= prefConfig.fps + 3;
    }

    private boolean isRefreshRateGoodMatch(float refreshRate) {
        return refreshRate >= prefConfig.fps &&
                Math.round(refreshRate) % prefConfig.fps <= 3;
    }

    private boolean shouldIgnoreInsetsForResolution(int width, int height) {
        // Never ignore insets for non-native resolutions
        if (!PreferenceConfiguration.isNativeResolution(width, height)) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display display = getWindowManager().getDefaultDisplay();
            for (Display.Mode candidate : display.getSupportedModes()) {
                // Ignore insets if this is an exact match for the display resolution
                if ((width == candidate.getPhysicalWidth() && height == candidate.getPhysicalHeight()) ||
                        (height == candidate.getPhysicalWidth() && width == candidate.getPhysicalHeight())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean mayReduceRefreshRate() {
        return prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_CAP_FPS ||
                prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS ||
                (prefConfig.framePacing == PreferenceConfiguration.FRAME_PACING_BALANCED && prefConfig.reduceRefreshRate);
    }

    private float prepareDisplayForRendering() {
        Display display = getWindowManager().getDefaultDisplay();
        WindowManager.LayoutParams windowLayoutParams = getWindow().getAttributes();
        float displayRefreshRate;

        // On M, we can explicitly set the optimal display mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Display.Mode bestMode = display.getMode();
            boolean isNativeResolutionStream = PreferenceConfiguration.isNativeResolution(prefConfig.width, prefConfig.height);
            boolean refreshRateIsGood = isRefreshRateGoodMatch(bestMode.getRefreshRate());
            boolean refreshRateIsEqual = isRefreshRateEqualMatch(bestMode.getRefreshRate());

            LimeLog.info("Current display mode: "+bestMode.getPhysicalWidth()+"x"+
                    bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());

            for (Display.Mode candidate : display.getSupportedModes()) {
                boolean refreshRateReduced = candidate.getRefreshRate() < bestMode.getRefreshRate();
                boolean resolutionReduced = candidate.getPhysicalWidth() < bestMode.getPhysicalWidth() ||
                        candidate.getPhysicalHeight() < bestMode.getPhysicalHeight();
                boolean resolutionFitsStream = candidate.getPhysicalWidth() >= prefConfig.width &&
                        candidate.getPhysicalHeight() >= prefConfig.height;

                LimeLog.info("Examining display mode: "+candidate.getPhysicalWidth()+"x"+
                        candidate.getPhysicalHeight()+"x"+candidate.getRefreshRate());

                if (candidate.getPhysicalWidth() > 4096 && prefConfig.width <= 4096) {
                    // Avoid resolutions options above 4K to be safe
                    continue;
                }

                // On non-4K streams, we force the resolution to never change unless it's above
                // 60 FPS, which may require a resolution reduction due to HDMI bandwidth limitations,
                // or it's a native resolution stream.
                if (prefConfig.width < 3840 && prefConfig.fps <= 60 && !isNativeResolutionStream) {
                    if (display.getMode().getPhysicalWidth() != candidate.getPhysicalWidth() ||
                            display.getMode().getPhysicalHeight() != candidate.getPhysicalHeight()) {
                        continue;
                    }
                }

                // Make sure the resolution doesn't regress unless if it's over 60 FPS
                // where we may need to reduce resolution to achieve the desired refresh rate.
                if (resolutionReduced && !(prefConfig.fps > 60 && resolutionFitsStream)) {
                    continue;
                }

                if (mayReduceRefreshRate() && refreshRateIsEqual && !isRefreshRateEqualMatch(candidate.getRefreshRate())) {
                    // If we had an equal refresh rate and this one is not, skip it. In min latency
                    // mode, we want to always prefer the highest frame rate even though it may cause
                    // microstuttering.
                    continue;
                }
                else if (refreshRateIsGood) {
                    // We've already got a good match, so if this one isn't also good, it's not
                    // worth considering at all.
                    if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                        continue;
                    }

                    if (mayReduceRefreshRate()) {
                        // User asked for the lowest possible refresh rate, so don't raise it if we
                        // have a good match already
                        if (candidate.getRefreshRate() > bestMode.getRefreshRate()) {
                            continue;
                        }
                    }
                    else {
                        // User asked for the highest possible refresh rate, so don't reduce it if we
                        // have a good match already
                        if (refreshRateReduced) {
                            continue;
                        }
                    }
                }
                else if (!isRefreshRateGoodMatch(candidate.getRefreshRate())) {
                    // We didn't have a good match and this match isn't good either, so just don't
                    // reduce the refresh rate.
                    if (refreshRateReduced) {
                        continue;
                    }
                } else {
                    // We didn't have a good match and this match is good. Prefer this refresh rate
                    // even if it reduces the refresh rate. Lowering the refresh rate can be beneficial
                    // when streaming a 60 FPS stream on a 90 Hz device. We want to select 60 Hz to
                    // match the frame rate even if the active display mode is 90 Hz.
                }

                bestMode = candidate;
                refreshRateIsGood = isRefreshRateGoodMatch(candidate.getRefreshRate());
                refreshRateIsEqual = isRefreshRateEqualMatch(candidate.getRefreshRate());
            }

            LimeLog.info("Best display mode: "+bestMode.getPhysicalWidth()+"x"+
                    bestMode.getPhysicalHeight()+"x"+bestMode.getRefreshRate());

            // Only apply new window layout parameters if we've actually changed the display mode
            if (display.getMode().getModeId() != bestMode.getModeId()) {
                // If we only changed refresh rate and we're on an OS that supports Surface.setFrameRate()
                // use that instead of using preferredDisplayModeId to avoid the possibility of triggering
                // bugs that can cause the system to switch from 4K60 to 4K24 on Chromecast 4K.
                if (prefConfig.enforceDisplayMode ||Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        display.getMode().getPhysicalWidth() != bestMode.getPhysicalWidth() ||
                        display.getMode().getPhysicalHeight() != bestMode.getPhysicalHeight()) {
                    // Apply the display mode change
                    windowLayoutParams.preferredDisplayModeId = bestMode.getModeId();
                    getWindow().setAttributes(windowLayoutParams);
                }
                else {
                    LimeLog.info("Using setFrameRate() instead of preferredDisplayModeId due to matching resolution");
                }
            }
            else {
                LimeLog.info("Current display mode is already the best display mode");
            }

            displayRefreshRate = bestMode.getRefreshRate();
        }
        // On L, we can at least tell the OS that we want a refresh rate
        else {
            float bestRefreshRate = display.getRefreshRate();
            for (float candidate : display.getSupportedRefreshRates()) {
                LimeLog.info("Examining refresh rate: "+candidate);

                if (candidate > bestRefreshRate) {
                    // Ensure the frame rate stays around 60 Hz for <= 60 FPS streams
                    if (prefConfig.fps <= 60) {
                        if (candidate >= 63) {
                            continue;
                        }
                    }

                    bestRefreshRate = candidate;
                }
            }

            LimeLog.info("Selected refresh rate: "+bestRefreshRate);
            windowLayoutParams.preferredRefreshRate = bestRefreshRate;
            displayRefreshRate = bestRefreshRate;

            // Apply the refresh rate change
            getWindow().setAttributes(windowLayoutParams);
        }

        // Until Marshmallow, we can't ask for a 4K display mode, so we'll
        // need to hint the OS to provide one.
        boolean aspectRatioMatch = false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // We'll calculate whether we need to scale by aspect ratio. If not, we'll use
            // setFixedSize so we can handle 4K properly. The only known devices that have
            // >= 4K screens have exactly 4K screens, so we'll be able to hit this good path
            // on these devices. On Marshmallow, we can start changing to 4K manually but no
            // 4K devices run 6.0 at the moment.
            Point screenSize = new Point(0, 0);
            display.getSize(screenSize);

            double screenAspectRatio = ((double)screenSize.y) / screenSize.x;
            double streamAspectRatio = ((double)prefConfig.height) / prefConfig.width;
            if (Math.abs(screenAspectRatio - streamAspectRatio) < 0.001) {
                LimeLog.info("Stream has compatible aspect ratio with output display");
                aspectRatioMatch = true;
            }
        }

        if (prefConfig.stretchVideo || aspectRatioMatch) {
            // Set the surface to the size of the video
            streamView.getHolder().setFixedSize(prefConfig.width, prefConfig.height);
            if (fsrView != null) {
                fsrView.setDesiredAspectRatio(0.0);
            }
        }
        else {
            // Set the surface to scale based on the aspect ratio of the stream
            streamView.setDesiredAspectRatio((double)prefConfig.width / (double)prefConfig.height);
            if (fsrView != null) {
                fsrView.setDesiredAspectRatio((double)prefConfig.width / (double)prefConfig.height);
            }
            LimeLog.info("surfaceChanged-->"+(double)prefConfig.width / (double)prefConfig.height);
        }

        if (stereo3dEnabled && fsrView != null) {
            // Full-SBS output is two 16:9 eye images placed side by side.
            fsrView.setDesiredAspectRatio(3840.0 / 1080.0);
        }

        // Set the desired refresh rate that will get passed into setFrameRate() later
        desiredRefreshRate = displayRefreshRate;

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // TVs may take a few moments to switch refresh rates, and we can probably assume
            // it will be eventually activated.
            // TODO: Improve this
            return displayRefreshRate;
        }
        else {
            // Use the lower of the current refresh rate and the selected refresh rate.
            // The preferred refresh rate may not actually be applied (ex: Battery Saver mode).
            return Math.min(getWindowManager().getDefaultDisplay().getRefreshRate(), displayRefreshRate);
        }
    }

    @SuppressLint("InlinedApi")
    private final Runnable hideSystemUi = new Runnable() {
            @Override
            public void run() {
                // TODO: Do we want to use WindowInsetsController here on R+ instead of
                // SYSTEM_UI_FLAG_IMMERSIVE_STICKY? They seem to do the same thing as of S...

                // In multi-window mode on N+, we need to drop our layout flags or we'll
                // be drawing underneath the system UI.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode()) {
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
                }
                else {
                    // Use immersive mode
                    Game.this.getWindow().getDecorView().setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                }
            }
    };

    private void hideSystemUi(int delay) {
        Handler h = getWindow().getDecorView().getHandler();
        if (h != null) {
            h.removeCallbacks(hideSystemUi);
            h.postDelayed(hideSystemUi, delay);
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.N)
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);

        // In multi-window, we don't want to use the full-screen layout
        // flag. It will cause us to collide with the system UI.
        // This function will also be called for PiP so we can cover
        // that case here too.
        if (isInMultiWindowMode) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoBackground();
        }
        else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decoderRenderer.notifyVideoForeground();
        }

        // Correct the system UI visibility flags
        hideSystemUi(50);
    }

    @Override
    protected void onDestroy() {
        logSessionInfo("LIFECYCLE", "串流页面正在销毁");

        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.destroy();
            virtualMouseOverlay = null;
        }
        if (videoZoomGestureOverlay != null) {
            videoZoomGestureOverlay.destroy();
            videoZoomGestureOverlay = null;
        }
        if (videoZoomController != null) {
            videoZoomController.destroy();
            videoZoomController = null;
        }

        instance = null;
        UiHelper.notifyHdrWindowStatus(this, false);

        if(presentation!=null){
            presentation.dismiss();
        }

        if (controllerHandler != null) {
            controllerHandler.destroy();
        }
        if (keyboardTranslator != null) {
            InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            inputManager.unregisterInputDeviceListener(keyboardTranslator);
        }

        if (lowLatencyWifiLock != null) {
            lowLatencyWifiLock.release();
        }
        if (highPerfWifiLock != null) {
            highPerfWifiLock.release();
        }

        if (connectedToUsbDriverService) {
            // Unbind from the discovery service
            unbindService(usbDriverServiceConnection);
        }

        if (fsrInputSurface != null) {
            fsrInputSurface.release();
            fsrInputSurface = null;
        }

        if (streamLoadingController != null) {
            streamLoadingController.destroy();
        }

        // Destroy the capture provider
        inputCaptureProvider.destroy();

        if (streamSessionLogger != null) {
            streamSessionLogger.close(reportedCrash ? "解码器异常后结束" : "串流页面已关闭");
            streamSessionLogger = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (virtualMouseOverlay != null) {
            setVirtualMouseInputSuppressed(false);
        }
        if (videoZoomGestureOverlay != null) {
            setVideoZoomInputSuppressed(false);
        }

        if (fsrView != null && fsrViewLifecyclePaused) {
            fsrView.onResume();
            fsrViewLifecyclePaused = false;
        }
    }

    @Override
    protected void onPause() {
        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.setInputSuppressed(true);
        }
        if (videoZoomGestureOverlay != null) {
            videoZoomGestureOverlay.setInputSuppressed(true);
        }

        if (fsrView != null && !(usbPermissionPromptVisible && !isFinishing())) {
            fsrView.onPause();
            fsrViewLifecyclePaused = true;
        }

        if (isFinishing()) {
            // Stop any further input device notifications before we lose focus (and pointer capture)
            if (controllerHandler != null) {
                controllerHandler.stop();
            }

            // Ungrab input to prevent further input device notifications
            setInputGrabState(false);
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.setInputSuppressed(true);
        }
        if (videoZoomGestureOverlay != null) {
            videoZoomGestureOverlay.setInputSuppressed(true);
        }

        logSessionInfo("LIFECYCLE", "串流页面进入后台/停止");

        Dialog.closeDialogs();

        if (virtualController != null) {
            virtualController.hide();
        }
        if (keyBoardController != null) {
            keyBoardController.hide();
        }

        if(keyBoardLayoutController!=null){
            keyBoardLayoutController.hide();
        }

        if(dialogGameMenu!=null&&dialogGameMenu.isVisible()){
            dialogGameMenu.dismiss();
        }

        if (conn != null) {
            int videoFormat = decoderRenderer.getActiveVideoFormat();

            displayedFailureDialog = true;
            stopConnection();
            if(isQuitSteamingFlag){
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        quitSteaming();
                    }
                },200); // 延时100毫秒
            }
            if (prefConfig.enableLatencyToast) {
                int averageEndToEndLat = decoderRenderer.getAverageEndToEndLatency();
                int averageDecoderLat = decoderRenderer.getAverageDecoderLatency();
                String message = null;
                if (averageEndToEndLat > 0) {
                    message = getResources().getString(R.string.conn_client_latency)+" "+averageEndToEndLat+" ms";
                    if (averageDecoderLat > 0) {
                        message += " ("+getResources().getString(R.string.conn_client_latency_hw)+" "+averageDecoderLat+" ms)";
                    }
                }
                else if (averageDecoderLat > 0) {
                    message = getResources().getString(R.string.conn_hardware_latency)+" "+averageDecoderLat+" ms";
                }

                // Add the video codec to the post-stream toast
                if (message != null) {
                    message += " [";

                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H264) != 0) {
                        message += "H.264";
                    }
                    else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_H265) != 0) {
                        message += "HEVC";
                    }
                    else if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_AV1) != 0) {
                        message += "AV1";
                    }
                    else {
                        message += "UNKNOWN";
                    }

                    if ((videoFormat & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0) {
                        message += " HDR";
                    }

                    message += "]";
                }

                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                }
            }

            // Clear the tombstone count if we terminated normally
            if (!reportedCrash && tombstonePrefs.getInt("CrashCount", 0) != 0) {
                tombstonePrefs.edit()
                        .putInt("CrashCount", 0)
                        .putInt("LastNotifiedCrashCount", 0)
                        .apply();
            }
        }
        if(prefConfig.enableScreenOnAuto!=0){
            isAutoLink=true;
            return;
        }
        finish();
    }

    @Override
    public void finish() {
        super.finish();
        if(prefConfig.enableScreenOnAuto==1){
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit()
                    .putInt("enable_screen_on_auto",0)
                    .commit();
        }
    }

    private boolean isAutoLink=false;

    @Override
    protected void onStart() {
        super.onStart();
        if (isAutoLink) {
            isAutoLink = false;
            recreate();
        }
    }

    private void setInputGrabState(boolean grab) {
        // Grab/ungrab the mouse cursor
        if (grab) {
            inputCaptureProvider.enableCapture();

            // Enabling capture may hide the cursor again, so
            // we will need to show it again.
            if (cursorVisible) {
                inputCaptureProvider.showCursor();
            }
        }
        else {
            inputCaptureProvider.disableCapture();
        }

        // Grab/ungrab system keyboard shortcuts
        setMetaKeyCaptureState(grab);

        grabbedInput = grab;
    }

    private final Runnable toggleGrab = new Runnable() {
        @Override
        public void run() {
            setInputGrabState(!grabbedInput);
        }
    };

    // Returns true if the key stroke was consumed
    private boolean handleSpecialKeys(int androidKeyCode, boolean down) {
        int modifierMask = 0;
        int nonModifierKeyCode = KeyEvent.KEYCODE_UNKNOWN;

        if (androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_CTRL;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
                 androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_SHIFT;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT ||
                 androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_ALT;
        }
        else if (androidKeyCode == KeyEvent.KEYCODE_META_LEFT ||
                androidKeyCode == KeyEvent.KEYCODE_META_RIGHT) {
            modifierMask = KeyboardPacket.MODIFIER_META;
        }
        else {
            nonModifierKeyCode = androidKeyCode;
        }

        if (down) {
            this.modifierFlags |= modifierMask;
        }
        else {
            this.modifierFlags &= ~modifierMask;
        }

        // Handle the special combos on the key up
        if (waitingForAllModifiersUp || specialKeyCode != KeyEvent.KEYCODE_UNKNOWN) {
            if (specialKeyCode == androidKeyCode) {
                // If this is a key up for the special key itself, eat that because the host never saw the original key down
                return true;
            }
            else if (modifierFlags != 0) {
                // While we're waiting for modifiers to come up, eat all key downs and allow all key ups to pass
                return down;
            }
            else {
                // When all modifiers are up, perform the special action
                switch (specialKeyCode) {
                    // Toggle input grab
                    case KeyEvent.KEYCODE_Z:
                        Handler h = getWindow().getDecorView().getHandler();
                        if (h != null) {
                            h.postDelayed(toggleGrab, 250);
                        }
                        break;

                    // Quit
                    case KeyEvent.KEYCODE_Q:
                        finish();
                        break;

                    // Toggle cursor visibility
                    case KeyEvent.KEYCODE_C:
                        if (!grabbedInput) {
                            inputCaptureProvider.enableCapture();
                            grabbedInput = true;
                        }
                        cursorVisible = !cursorVisible;
                        if (cursorVisible) {
                            inputCaptureProvider.showCursor();
                        } else {
                            inputCaptureProvider.hideCursor();
                        }
                        break;

                    default:
                        break;
                }

                // Reset special key state
                specialKeyCode = KeyEvent.KEYCODE_UNKNOWN;
                waitingForAllModifiersUp = false;
            }
        }
        // Check if Ctrl+Alt+Shift is down when a non-modifier key is pressed
        else if ((modifierFlags & (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT)) ==
                (KeyboardPacket.MODIFIER_CTRL | KeyboardPacket.MODIFIER_ALT | KeyboardPacket.MODIFIER_SHIFT) &&
                (down && nonModifierKeyCode != KeyEvent.KEYCODE_UNKNOWN)) {
            switch (androidKeyCode) {
                case KeyEvent.KEYCODE_Z:
                case KeyEvent.KEYCODE_Q:
                case KeyEvent.KEYCODE_C:
                    // Remember that a special key combo was activated, so we can consume all key
                    // events until the modifiers come up
                    specialKeyCode = androidKeyCode;
                    waitingForAllModifiersUp = true;
                    return true;

                default:
                    // This isn't a special combo that we consume on the client side
                    return false;
            }
        }

        // Not a special combo
        return false;
    }

    // We cannot simply use modifierFlags for all key event processing, because
    // some IMEs will not generate real key events for pressing Shift. Instead
    // they will simply send key events with isShiftPressed() returning true,
    // and we will need to send the modifier flag ourselves.
    private byte getModifierState(KeyEvent event) {
        // Start with the global modifier state to ensure we cover the case
        // detailed in https://github.com/moonlight-stream/moonlight-android/issues/840
        byte modifier = getModifierState();
        if (event.isShiftPressed()) {
            modifier |= KeyboardPacket.MODIFIER_SHIFT;
        }
        if (event.isCtrlPressed()) {
            modifier |= KeyboardPacket.MODIFIER_CTRL;
        }
        if (event.isAltPressed()) {
            modifier |= KeyboardPacket.MODIFIER_ALT;
        }
        if (event.isMetaPressed()) {
            modifier |= KeyboardPacket.MODIFIER_META;
        }
        return modifier;
    }

    private byte getModifierState() {
        return (byte) modifierFlags;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return handleKeyDown(event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean handleKeyDown(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click. This event WILL repeat if
        // the right mouse button is held down, so we ignore those.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }

        boolean handled = false;

        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonDown(event);
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            // Let this method take duplicate key down events
            if (handleSpecialKeys(event.getKeyCode(), true)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }

            // We'll send it as a raw key event if we have a key mapping, otherwise we'll send it
            // as UTF-8 text (if it's a printable character).
            short translated = keyboardTranslator.translate(event.getKeyCode(), event.getDeviceId());
            if (translated == 0) {
                // Make sure it has a valid Unicode representation and it's not a dead character
                // (which we don't support). If those are true, we can send it as UTF-8 text.
                //
                // NB: We need to be sure this happens before the getRepeatCount() check because
                // UTF-8 events don't auto-repeat on the host side.
                int unicodeChar = event.getUnicodeChar();
                if ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0 && (unicodeChar & KeyCharacterMap.COMBINING_ACCENT_MASK) != 0) {
                    conn.sendUtf8Text(""+(char)unicodeChar);
                    return true;
                }

                return false;
            }

            // Eat repeat down events
            if (event.getRepeatCount() > 0) {
                return true;
            }

            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_DOWN, getModifierState(event),
                    keyboardTranslator.hasNormalizedMapping(event.getKeyCode(), event.getDeviceId()) ? 0 : MoonBridge.SS_KBE_FLAG_NON_NORMALIZED);
        }

        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return handleKeyUp(event) || super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean handleKeyUp(KeyEvent event) {
        // Pass-through virtual navigation keys
        if ((event.getFlags() & KeyEvent.FLAG_VIRTUAL_HARD_KEY) != 0) {
            return false;
        }

        // Handle a synthetic back button event that some Android OS versions
        // create as a result of a right-click.
        int eventSource = event.getSource();
        if ((eventSource == InputDevice.SOURCE_MOUSE ||
                eventSource == InputDevice.SOURCE_MOUSE_RELATIVE) &&
                event.getKeyCode() == KeyEvent.KEYCODE_BACK) {

            // Send the right mouse button event if mouse back and forward
            // are disabled. If they are enabled, handleMotionEvent() will take
            // care of this.
            if (!prefConfig.mouseNavButtons) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
            }

            // Always return true, otherwise the back press will be propagated
            // up to the parent and finish the activity.
            return true;
        }

        boolean handled = false;
        if (ControllerHandler.isGameControllerDevice(event.getDevice())) {
            // Always try the controller handler first, unless it's an alphanumeric keyboard device.
            // Otherwise, controller handler will eat keyboard d-pad events.
            handled = controllerHandler.handleButtonUp(event);
        }

        // Try the keyboard handler if it wasn't handled as a game controller
        if (!handled) {
            if (handleSpecialKeys(event.getKeyCode(), false)) {
                return true;
            }

            // Pass through keyboard input if we're not grabbing
            if (!grabbedInput) {
                return false;
            }

            short translated = keyboardTranslator.translate(event.getKeyCode(), event.getDeviceId());
            if (translated == 0) {
                // If we sent this event as UTF-8 on key down, also report that it was handled
                // when we get the key up event for it.
                int unicodeChar = event.getUnicodeChar();
                return (unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0 && (unicodeChar & KeyCharacterMap.COMBINING_ACCENT_MASK) != 0;
            }

            conn.sendKeyboardInput(translated, KeyboardPacket.KEY_UP, getModifierState(event),
                    keyboardTranslator.hasNormalizedMapping(event.getKeyCode(), event.getDeviceId()) ? 0 : MoonBridge.SS_KBE_FLAG_NON_NORMALIZED);
        }

        return true;
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        return handleKeyMultiple(event) || super.onKeyMultiple(keyCode, repeatCount, event);
    }

    private boolean handleKeyMultiple(KeyEvent event) {
        // We can receive keys from a software keyboard that don't correspond to any existing
        // KEYCODE value. Android will give those to us as an ACTION_MULTIPLE KeyEvent.
        //
        // Despite the fact that the Android docs say this is unused since API level 29, these
        // events are still sent as of Android 13 for the above case.
        //
        // For other cases of ACTION_MULTIPLE, we will not report those as handled so hopefully
        // they will be passed to us again as regular singular key events.
        if (event.getKeyCode() != KeyEvent.KEYCODE_UNKNOWN || event.getCharacters() == null) {
            return false;
        }

        conn.sendUtf8Text(event.getCharacters());
        return true;
    }

    private TouchContext getTouchContext(int actionIndex)
    {
        if (actionIndex < touchContextMap.length) {
            return touchContextMap[actionIndex];
        }
        else {
            return null;
        }
    }

    @Override
    public void toggleKeyboard() {
        LimeLog.info("Toggling keyboard overlay");
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.toggleSoftInput(0, 0);
    }

    private byte getLiTouchTypeFromEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                return MoonBridge.LI_TOUCH_EVENT_DOWN;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if ((event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                    return MoonBridge.LI_TOUCH_EVENT_CANCEL;
                }
                else {
                    return MoonBridge.LI_TOUCH_EVENT_UP;
                }

            case MotionEvent.ACTION_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_MOVE;

            case MotionEvent.ACTION_CANCEL:
                // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
                // rather than CANCEL. For a single pointer cancellation, that's indicated via
                // FLAG_CANCELED on a ACTION_POINTER_UP.
                // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
                return MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL;

            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
                return MoonBridge.LI_TOUCH_EVENT_HOVER;

            case MotionEvent.ACTION_HOVER_EXIT:
                return MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE;

            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MoonBridge.LI_TOUCH_EVENT_BUTTON_ONLY;

            default:
               return -1;
        }
    }

    //灵敏度保存到集合 适配多个手指
    private Map<String,SensitivityBean> sensitivityMap=new HashMap<>();

    //修改移动的触控灵敏度（通过修改移动的距离实现） 默认使用右半边屏幕的时候开启
    private float[] getStreamViewRelativeSensitivityXY(MotionEvent event,float normalizedX,float normalizedY,int pointerIndex){
        float[] normalized=new float[2];
        normalized[0]=normalizedX;
        normalized[1]=normalizedY;
        //记录按下的坐标
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN ||event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN) {
            SensitivityBean bean=new SensitivityBean();
            bean.setStartDownX(normalizedX);
            sensitivityMap.put(String.valueOf(event.getPointerId(pointerIndex)),bean);
        }
        //抬起的时候，恢复初始化状态
        if (event.getActionMasked() == MotionEvent.ACTION_UP||event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            sensitivityMap.remove(String.valueOf(event.getPointerId(pointerIndex)));
        }
        //移动
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            SensitivityBean bean=sensitivityMap.get(String.valueOf(event.getPointerId(pointerIndex)));
            //不是全局模式 或者 按下时坐标 不在右边 则返回
            if(!prefConfig.touchSensitivityGlobal&&(bean==null||bean.getStartDownX()<streamView.getWidth()/2f)){
                return normalized;
            }
            float dx = 0;
            float dy = 0;
            if(bean.getLastAbsoluteX() !=-1){
                dx=normalizedX- bean.getLastAbsoluteX();
                dy=normalizedY- bean.getLastAbsoluteY();
                dx*=0.01f*prefConfig.touchSensitivityX;//灵敏度
                dy*=0.01f*prefConfig.touchSensitivityY;
                normalizedX= bean.getLastRelativelyX() +dx;
                normalizedY= bean.getLastRelativelyY() +dy;
            }
            if(prefConfig.touchSensitivityRotationAuto){
                int w = streamView.getWidth();
                int h = streamView.getHeight();
                if (normalizedX > w || normalizedX < 0 || normalizedY > h || normalizedY < 0) {
                    normalizedX -= dx;
                    normalizedY -= dy;
                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_UP, event.getPointerId(pointerIndex),
                            normalizedX / w, normalizedY / h,
                            0.5f, 0.5f, 0.5f, (short) 0);
                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_DOWN, event.getPointerId(pointerIndex),
                            0.5f, 0.5f,
                            0.5f, 0.5f, 0.5f, (short) 0);
                    normalizedX = w / 2f + dx;
                    normalizedY = h / 2f + dy;

                }
            }
            bean.setLastAbsoluteX(event.getX(pointerIndex));
            bean.setLastAbsoluteY(event.getY(pointerIndex));
            bean.setLastRelativelyX(normalizedX);
            bean.setLastRelativelyY(normalizedY);
            sensitivityMap.put(String.valueOf(event.getPointerId(pointerIndex)),bean);
        }
        normalized[0]=normalizedX;
        normalized[1]=normalizedY;
        return normalized;
    }


    private float[] getStreamViewRelativeNormalizedXY(View view, MotionEvent event, int pointerIndex,boolean isTouch) {
        float normalizedX = event.getX(pointerIndex);
        float normalizedY = event.getY(pointerIndex);
        //开启自定义修改触控灵敏度 并且 数值不为100
        if(isTouch&&prefConfig.enableTouchSensitivity&&(prefConfig.touchSensitivityX !=100||prefConfig.touchSensitivityY!=100)){
            float[] normalized=getStreamViewRelativeSensitivityXY(event,normalizedX,normalizedY,pointerIndex);
            normalizedX=normalized[0];
            normalizedY=normalized[1];
        }
        // For the containing background view, we must subtract the origin
        // of the StreamView to get video-relative coordinates.
        if (view != streamView) {
            normalizedX -= streamView.getX();
            normalizedY -= streamView.getY();
        }

        normalizedX = Math.max(normalizedX, 0.0f);
        normalizedY = Math.max(normalizedY, 0.0f);

        normalizedX = Math.min(normalizedX, streamView.getWidth());
        normalizedY = Math.min(normalizedY, streamView.getHeight());

        normalizedX /= streamView.getWidth();
        normalizedY /= streamView.getHeight();

        return new float[] { normalizedX, normalizedY };
    }

    private static float normalizeValueInRange(float value, InputDevice.MotionRange range) {
        return (value - range.getMin()) / range.getRange();
    }

    private static float getPressureOrDistance(MotionEvent event, int pointerIndex) {
        InputDevice dev = event.getDevice();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
                // Hover events report distance
                if (dev != null) {
                    InputDevice.MotionRange distanceRange = dev.getMotionRange(MotionEvent.AXIS_DISTANCE, event.getSource());
                    if (distanceRange != null) {
                        return normalizeValueInRange(event.getAxisValue(MotionEvent.AXIS_DISTANCE, pointerIndex), distanceRange);
                    }
                }
                return 0.0f;

            default:
                // Other events report pressure
                return event.getPressure(pointerIndex);
        }
    }

    private static short getRotationDegrees(MotionEvent event, int pointerIndex) {
        InputDevice dev = event.getDevice();
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_ORIENTATION, event.getSource()) != null) {
                short rotationDegrees = (short) Math.toDegrees(event.getOrientation(pointerIndex));
                if (rotationDegrees < 0) {
                    rotationDegrees += 360;
                }
                return rotationDegrees;
            }
        }
        return MoonBridge.LI_ROT_UNKNOWN;
    }

    private static float[] polarToCartesian(float r, float theta) {
        return new float[] { (float)(r * Math.cos(theta)), (float)(r * Math.sin(theta)) };
    }

    private static float cartesianToR(float[] point) {
        return (float)Math.sqrt(Math.pow(point[0], 2) + Math.pow(point[1], 2));
    }

    private float[] getStreamViewNormalizedContactArea(MotionEvent event, int pointerIndex) {
        float orientation;

        // If the orientation is unknown, we'll just assume it's at a 45 degree angle and scale it by
        // X and Y scaling factors evenly.
        if (event.getDevice() == null || event.getDevice().getMotionRange(MotionEvent.AXIS_ORIENTATION, event.getSource()) == null) {
            orientation = (float)(Math.PI / 4);
        }
        else {
            orientation = event.getOrientation(pointerIndex);
        }

        float contactAreaMajor, contactAreaMinor;
        switch (event.getActionMasked()) {
            // Hover events report the tool size
            case MotionEvent.ACTION_HOVER_ENTER:
            case MotionEvent.ACTION_HOVER_MOVE:
            case MotionEvent.ACTION_HOVER_EXIT:
                contactAreaMajor = event.getToolMajor(pointerIndex);
                contactAreaMinor = event.getToolMinor(pointerIndex);
                break;

            // Other events report contact area
            default:
                contactAreaMajor = event.getTouchMajor(pointerIndex);
                contactAreaMinor = event.getTouchMinor(pointerIndex);
                break;
        }

        // The contact area major axis is parallel to the orientation, so we simply convert
        // polar to cartesian coordinates using the orientation as theta.
        float[] contactAreaMajorCartesian = polarToCartesian(contactAreaMajor, orientation);

        // The contact area minor axis is perpendicular to the contact area major axis (and thus
        // the orientation), so rotate the orientation angle by 90 degrees.
        float[] contactAreaMinorCartesian = polarToCartesian(contactAreaMinor, (float)(orientation + (Math.PI / 2)));

        // 获取视图的缩放因子
        float scaleX = streamView.getScaleX();
        float scaleY = streamView.getScaleY();

        // Normalize the contact area to the stream view size
        contactAreaMajorCartesian[0] = Math.min(Math.abs(contactAreaMajorCartesian[0]) / scaleX, streamView.getWidth()) / streamView.getWidth();
        contactAreaMinorCartesian[0] = Math.min(Math.abs(contactAreaMinorCartesian[0]) /scaleX, streamView.getWidth()) / streamView.getWidth();
        contactAreaMajorCartesian[1] = Math.min(Math.abs(contactAreaMajorCartesian[1]) /scaleY, streamView.getHeight()) / streamView.getHeight();
        contactAreaMinorCartesian[1] = Math.min(Math.abs(contactAreaMinorCartesian[1]) /scaleY, streamView.getHeight()) / streamView.getHeight();

        // Convert the normalized values back into polar coordinates
        return new float[] { cartesianToR(contactAreaMajorCartesian), cartesianToR(contactAreaMinorCartesian) };
    }



    private boolean sendPenEventForPointer(View view, MotionEvent event, byte eventType, byte toolType, int pointerIndex) {
        byte penButtons = 0;
        if ((event.getButtonState() & MotionEvent.BUTTON_STYLUS_PRIMARY) != 0) {
            penButtons |= MoonBridge.LI_PEN_BUTTON_PRIMARY;
        }
        if ((event.getButtonState() & MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) {
            penButtons |= MoonBridge.LI_PEN_BUTTON_SECONDARY;
        }

        byte tiltDegrees = MoonBridge.LI_TILT_UNKNOWN;
        InputDevice dev = event.getDevice();
        if (dev != null) {
            if (dev.getMotionRange(MotionEvent.AXIS_TILT, event.getSource()) != null) {
                tiltDegrees = (byte)Math.toDegrees(event.getAxisValue(MotionEvent.AXIS_TILT, pointerIndex));
            }
        }

        float[] normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex,false);
        float[] normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex);
        return conn.sendPenEvent(eventType, toolType, penButtons,
                normalizedCoords[0], normalizedCoords[1],
                getPressureOrDistance(event, pointerIndex),
                normalizedContactArea[0], normalizedContactArea[1],
                getRotationDegrees(event, pointerIndex), tiltDegrees) != MoonBridge.LI_ERR_UNSUPPORTED;
    }

    private static byte convertToolTypeToStylusToolType(MotionEvent event, int pointerIndex) {
        switch (event.getToolType(pointerIndex)) {
            case MotionEvent.TOOL_TYPE_ERASER:
                return MoonBridge.LI_TOOL_TYPE_ERASER;
            case MotionEvent.TOOL_TYPE_STYLUS:
                return MoonBridge.LI_TOOL_TYPE_PEN;
            default:
                return MoonBridge.LI_TOOL_TYPE_UNKNOWN;
        }
    }

    private boolean trySendPenEvent(View view, MotionEvent event) {
        byte eventType = getLiTouchTypeFromEvent(event);
        if (eventType < 0) {
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            boolean handledStylusEvent = false;
            for (int i = 0; i < event.getPointerCount(); i++) {
                byte toolType = convertToolTypeToStylusToolType(event, i);
                if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                    // Not a stylus pointer, so skip it
                    continue;
                }
                else {
                    // This pointer is a stylus, so we'll report that we handled this event
                    handledStylusEvent = true;
                }

                if (!sendPenEventForPointer(view, event, eventType, toolType, i)) {
                    // Pen events aren't supported by the host
                    return false;
                }
            }
            return handledStylusEvent;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendPenEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, MoonBridge.LI_TOOL_TYPE_UNKNOWN, (byte)0,
                    0, 0, 0, 0, 0,
                    MoonBridge.LI_ROT_UNKNOWN, MoonBridge.LI_TILT_UNKNOWN) != MoonBridge.LI_ERR_UNSUPPORTED;
        }
        else {
            // Up, Down, and Hover events are specific to the action index
            byte toolType = convertToolTypeToStylusToolType(event, event.getActionIndex());
            if (toolType == MoonBridge.LI_TOOL_TYPE_UNKNOWN) {
                // Not a stylus event
                return false;
            }
            return sendPenEventForPointer(view, event, eventType, toolType, event.getActionIndex());
        }
    }

    private boolean sendTouchEventForPointer(View view, MotionEvent event, byte eventType, int pointerIndex) {
        float[] normalizedCoords = getStreamViewRelativeNormalizedXY(view, event, pointerIndex,true);
        float[] normalizedContactArea = getStreamViewNormalizedContactArea(event, pointerIndex);
        return conn.sendTouchEvent(eventType, event.getPointerId(pointerIndex),
                normalizedCoords[0], normalizedCoords[1],
                getPressureOrDistance(event, pointerIndex),
                normalizedContactArea[0], normalizedContactArea[1],
                getRotationDegrees(event, pointerIndex)) != MoonBridge.LI_ERR_UNSUPPORTED;
    }

    private boolean trySendTouchEvent(View view, MotionEvent event) {
        byte eventType = getLiTouchTypeFromEvent(event);
        if (eventType < 0) {
            return false;
        }

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (!sendTouchEventForPointer(view, event, eventType, i)) {
                    return false;
                }
            }
            return true;
        }
        else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0,
                    0, 0, 0, 0, 0,
                    MoonBridge.LI_ROT_UNKNOWN) != MoonBridge.LI_ERR_UNSUPPORTED;
        }
        else {
            // Up, Down, and Hover events are specific to the action index
            return sendTouchEventForPointer(view, event, eventType, event.getActionIndex());
        }
    }

    // Returns true if the event was consumed
    // NB: View is only present if called from a view callback
    private boolean handleMotionEvent(View view, MotionEvent event) {
        // Pass through mouse/touch/joystick input if we're not grabbing
        if (!grabbedInput) {
            return false;
        }
        int eventSource = event.getSource();
        int deviceSources = event.getDevice() != null ? event.getDevice().getSources() : 0;
        if ((eventSource & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
            if (controllerHandler.handleMotionEvent(event)) {
                return true;
            }
        }
        else if ((deviceSources & InputDevice.SOURCE_CLASS_JOYSTICK) != 0 && controllerHandler.tryHandleTouchpadEvent(event)) {
            return true;
        }
        else if ((eventSource & InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                 (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 ||
                 eventSource == InputDevice.SOURCE_MOUSE_RELATIVE)
        {
            // This case is for mice and non-finger touch devices
            if (eventSource == InputDevice.SOURCE_MOUSE ||
                    (eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0 || // SOURCE_TOUCHPAD
                    eventSource == InputDevice.SOURCE_MOUSE_RELATIVE ||
                    (event.getPointerCount() >= 1 &&
                            (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS ||
                                    event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER)) ||
                    eventSource == 12290) // 12290 = Samsung DeX mode desktop mouse
            {
                int buttonState = event.getButtonState();
                int changedButtons = buttonState ^ lastButtonState;

                // The DeX touchpad on the Fold 4 sends proper right click events using BUTTON_SECONDARY,
                // but doesn't send BUTTON_PRIMARY for a regular click. Instead it sends ACTION_DOWN/UP,
                // so we need to fix that up to look like a sane input event to process it correctly.
                if (eventSource == 12290) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        buttonState |= MotionEvent.BUTTON_PRIMARY;
                    }
                    else if (event.getAction() == MotionEvent.ACTION_UP) {
                        buttonState &= ~MotionEvent.BUTTON_PRIMARY;
                    }
                    else {
                        // We may be faking the primary button down from a previous event,
                        // so be sure to add that bit back into the button state.
                        buttonState |= (lastButtonState & MotionEvent.BUTTON_PRIMARY);
                    }

                    changedButtons = buttonState ^ lastButtonState;
                }

                // Some external touchpads report a 2-finger tap as a primary action button press
                // while keeping the pointer source as SOURCE_TOUCHPAD. Promote that gesture to
                // a secondary click so it behaves like a desktop touchpad right-click.
                if (eventSource == InputDevice.SOURCE_TOUCHPAD &&
                        event.getPointerCount() == 2 &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        event.getActionButton() == MotionEvent.BUTTON_PRIMARY) {
                    if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                        buttonState |= MotionEvent.BUTTON_SECONDARY;
                    }
                    else if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_RELEASE) {
                        buttonState &= ~MotionEvent.BUTTON_SECONDARY;
                    }

                    // Keep any previously faked primary state, but don't let this gesture look
                    // like a left click in addition to the synthesized right click.
                    buttonState &= ~MotionEvent.BUTTON_PRIMARY;
                    buttonState |= (lastButtonState & MotionEvent.BUTTON_PRIMARY);
                    changedButtons = buttonState ^ lastButtonState;
                }

                // Ignore mouse input if we're not capturing from our input source
                if (!inputCaptureProvider.isCapturingActive()) {
                    // We return true here because otherwise the events may end up causing
                    // Android to synthesize d-pad events.
                    return true;
                }

                // Always update the position before sending any button events. If we're
                // dealing with a stylus without hover support, our position might be
                // significantly different than before.
                if (inputCaptureProvider.eventHasRelativeMouseAxes(event)) {
                    // Send the deltas straight from the motion event
                    float rawDeltaX = inputCaptureProvider.getRelativeAxisX(event);
                    float rawDeltaY = inputCaptureProvider.getRelativeAxisY(event);
                    short deltaX = (short)(rawDeltaX * prefConfig.externalTouchPadSensitityX * 0.01f);
                    short deltaY = (short)(rawDeltaY * prefConfig.externalTouchPadSensitityY * 0.01f);

                    if (deltaX != 0 || deltaY != 0) {
                        boolean isTwoFingerTouchpadScroll =
                                eventSource == InputDevice.SOURCE_TOUCHPAD &&
                                        event.getPointerCount() == 2 &&
                                        event.getActionMasked() == MotionEvent.ACTION_MOVE;

                        if (isTwoFingerTouchpadScroll) {
                            float scrollFactor = prefConfig.externalTouchPadScrollAmount * EXTERNAL_TOUCHPAD_SCROLL_FACTOR;
                            externalTouchpadScrollRemainderX += -rawDeltaX * scrollFactor;
                            externalTouchpadScrollRemainderY += -rawDeltaY * scrollFactor;

                            short hScroll = 0;
                            short vScroll = 0;

                            if (Math.abs(externalTouchpadScrollRemainderX) >= 1f) {
                                hScroll = (short) externalTouchpadScrollRemainderX;
                                externalTouchpadScrollRemainderX -= hScroll;
                            }
                            if (Math.abs(externalTouchpadScrollRemainderY) >= 1f) {
                                vScroll = (short) externalTouchpadScrollRemainderY;
                                externalTouchpadScrollRemainderY -= vScroll;
                            }

                            if (vScroll != 0) {
                                conn.sendMouseHighResScroll(vScroll);
                            }
                            if (hScroll != 0) {
                                conn.sendMouseHighResHScroll(hScroll);
                            }
                        }
                        else {
                            externalTouchpadScrollRemainderX = 0f;
                            externalTouchpadScrollRemainderY = 0f;

                            if (prefConfig.absoluteMouseMode) {
                                // NB: view may be null, but we can unconditionally use streamView because we don't need to adjust
                                // relative axis deltas for the position of the streamView within the parent's coordinate system.
                                conn.sendMouseMoveAsMousePosition(deltaX, deltaY, (short)streamView.getWidth(), (short)streamView.getHeight());
                            }
                            else {
                                conn.sendMouseMove(deltaX, deltaY);
                            }
                        }
                    }
                }
                else if ((eventSource & InputDevice.SOURCE_CLASS_POSITION) != 0) {
                    // If this input device is not associated with the view itself (like a trackpad),
                    // we'll convert the device-specific coordinates to use to send the cursor position.
                    // This really isn't ideal but it's probably better than nothing.
                    //
                    // Trackpad on newer versions of Android (Oreo and later) should be caught by the
                    // relative axes case above. If we get here, we're on an older version that doesn't
                    // support pointer capture.
                    InputDevice device = event.getDevice();
                    if (device != null) {
                        InputDevice.MotionRange xRange = device.getMotionRange(MotionEvent.AXIS_X, eventSource);
                        InputDevice.MotionRange yRange = device.getMotionRange(MotionEvent.AXIS_Y, eventSource);

                        // All touchpads coordinate planes should start at (0, 0)
                        if (xRange != null && yRange != null && xRange.getMin() == 0 && yRange.getMin() == 0) {
                            int xMax = (int)xRange.getMax();
                            int yMax = (int)yRange.getMax();

                            // Touchpads must be smaller than (65535, 65535)
                            if (xMax <= Short.MAX_VALUE && yMax <= Short.MAX_VALUE) {
                                conn.sendMousePosition((short)event.getX(), (short)event.getY(),
                                                       (short)xMax, (short)yMax);
                            }
                        }
                    }
                }
                else if (view != null && trySendPenEvent(view, event)) {
                    // If our host supports pen events, send it directly
                    return true;
                }
                else if (view != null) {
                    // Otherwise send absolute position based on the view for SOURCE_CLASS_POINTER
                    updateMousePosition(view, event);
                }

                if (event.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                    // Send the vertical scroll packet
                    conn.sendMouseHighResScroll((short)(event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 120));
                    conn.sendMouseHighResHScroll((short)(event.getAxisValue(MotionEvent.AXIS_HSCROLL) * 120));
                }

                if (eventSource == InputDevice.SOURCE_TOUCHPAD &&
                        event.getActionMasked() != MotionEvent.ACTION_MOVE) {
                    externalTouchpadScrollRemainderX = 0f;
                    externalTouchpadScrollRemainderY = 0f;
                }

                if ((changedButtons & MotionEvent.BUTTON_PRIMARY) != 0) {
                    if ((buttonState & MotionEvent.BUTTON_PRIMARY) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                    }
                }

                // Mouse secondary or stylus primary is right click (stylus down is left click)
                if ((changedButtons & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                    if ((buttonState & (MotionEvent.BUTTON_SECONDARY | MotionEvent.BUTTON_STYLUS_PRIMARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                    }
                }

                // Mouse tertiary or stylus secondary is middle click
                if ((changedButtons & (MotionEvent.BUTTON_TERTIARY | MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) {
                    if ((buttonState & (MotionEvent.BUTTON_TERTIARY | MotionEvent.BUTTON_STYLUS_SECONDARY)) != 0) {
                        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                    else {
                        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_MIDDLE);
                    }
                }

                if (prefConfig.mouseNavButtons) {
                    if ((changedButtons & MotionEvent.BUTTON_BACK) != 0) {
                        if ((buttonState & MotionEvent.BUTTON_BACK) != 0) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X1);
                        }
                        else {
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X1);
                        }
                    }

                    if ((changedButtons & MotionEvent.BUTTON_FORWARD) != 0) {
                        if ((buttonState & MotionEvent.BUTTON_FORWARD) != 0) {
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_X2);
                        }
                        else {
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_X2);
                        }
                    }
                }

                // Handle stylus presses
                if (event.getPointerCount() == 1 && event.getActionIndex() == 0) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchDownTime = event.getEventTime();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Stylus is left click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchDownTime = event.getEventTime();
                            lastAbsTouchDownX = event.getX(0);
                            lastAbsTouchDownY = event.getY(0);

                            // Eraser is right click
                            conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                    else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                            lastAbsTouchUpTime = event.getEventTime();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);

                            // Stylus is left click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
                        } else if (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
                            lastAbsTouchUpTime = event.getEventTime();
                            lastAbsTouchUpX = event.getX(0);
                            lastAbsTouchUpY = event.getY(0);

                            // Eraser is right click
                            conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT);
                        }
                    }
                }

                lastButtonState = buttonState;
            }
            // This case is for fingers
            else
            {
                if (virtualController != null &&
                        (virtualController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons ||
                         virtualController.getControllerMode() == KeyBoardController.ControllerMode.ResizeButtons||
                         virtualController.getControllerMode() == KeyBoardController.ControllerMode.DisableEnableButtons)) {
                    // Ignore presses when the virtual controller is being configured
                    return true;
                }

                if (keyBoardController != null &&
                        (keyBoardController.getControllerMode() == KeyBoardController.ControllerMode.MoveButtons ||
                                keyBoardController.getControllerMode() == KeyBoardController.ControllerMode.ResizeButtons||
                                keyBoardController.getControllerMode() == KeyBoardController.ControllerMode.DisableEnableButtons)) {
                    // Ignore presses when the virtual controller is being configured
                    return true;
                }
                //禁用鼠标
                if(disableMouseModel){
                    return true;
                }

                // If this is the parent view, we'll offset our coordinates to appear as if they
                // are relative to the StreamView like our StreamView touch events are.
                float xOffset, yOffset;
                if (view != streamView && !prefConfig.touchscreenTrackpad) {
                    xOffset = -streamView.getX();
                    yOffset = -streamView.getY();
                }
                else {
                    xOffset = 0.f;
                    yOffset = 0.f;
                }

                //五指打开输入法
                if(prefConfig.quickSoftKeyboardFingers>0){
                    switch (event.getActionMasked()){
                        case MotionEvent.ACTION_POINTER_DOWN:
                            if(event.getPointerCount() == prefConfig.quickSoftKeyboardFingers){
                                threeFingerDownTime = event.getEventTime();
                                // Cancel the first and second touches to avoid
                                // erroneous events
                                for (TouchContext aTouchContext : touchContextMap) {
                                    aTouchContext.cancelTouch();
                                }
                                return true;
                            }
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_POINTER_UP:
                            if (event.getPointerCount() == 1 &&
                                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0)) {
                                // All fingers up
                                if (event.getEventTime() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                                    // This is a 3 finger tap to bring up the keyboard
                                    conn.sendTouchEvent(MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL, 0,
                                            0, 0, 0, 0, 0,
                                            MoonBridge.LI_ROT_UNKNOWN);
                                    toggleKeyboard();
                                    return true;
                                }
                            }
                    }
                }

                // TODO: Re-enable native touch when have a better solution for handling
                // cancelled touches from Android gestures and 3 finger taps to activate the software keyboard.
                if(prefConfig.enableMultiTouchScreen){
                    if (!prefConfig.touchscreenTrackpad && trySendTouchEvent(view, event)) {
                        // If this host supports touch events and absolute touch is enabled,
                        // send it directly as a touch event.
                        return true;
                    }
                }

                int actionIndex = event.getActionIndex();

                int eventX = (int)(event.getX(actionIndex) + xOffset);
                int eventY = (int)(event.getY(actionIndex) + yOffset);

                // Special handling for 3 finger gesture
                if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN &&
                        event.getPointerCount() == 3) {
                    // Three fingers down
                    threeFingerDownTime = event.getEventTime();

                    // Cancel the first and second touches to avoid
                    // erroneous events
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                    }

                    return true;
                }

                TouchContext context = getTouchContext(actionIndex);
                if (context == null) {
                    return false;
                }

                switch (event.getActionMasked())
                {
                case MotionEvent.ACTION_POINTER_DOWN:
                case MotionEvent.ACTION_DOWN:
                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount());
                    }
                    context.touchDownEvent(eventX, eventY, event.getEventTime(), true);
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                case MotionEvent.ACTION_UP:
                    //是触控板模式 三点呼出软键盘
                    if(prefConfig.touchscreenTrackpad){
                        if (event.getPointerCount() == 1 &&
                                (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || (event.getFlags() & MotionEvent.FLAG_CANCELED) == 0)) {
                            // All fingers up
                            if (event.getEventTime() - threeFingerDownTime < THREE_FINGER_TAP_THRESHOLD) {
                                // This is a 3 finger tap to bring up the keyboard
                                toggleKeyboard();
                                return true;
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && (event.getFlags() & MotionEvent.FLAG_CANCELED) != 0) {
                        context.cancelTouch();
                    }
                    else {
                        context.touchUpEvent(eventX, eventY, event.getEventTime());
                    }

                    for (TouchContext touchContext : touchContextMap) {
                        touchContext.setPointerCount(event.getPointerCount() - 1);
                    }
                    if (actionIndex == 0 && event.getPointerCount() > 1 && !context.isCancelled()) {
                        // The original secondary touch now becomes primary
                        context.touchDownEvent(
                                (int)(event.getX(1) + xOffset),
                                (int)(event.getY(1) + yOffset),
                                event.getEventTime(), false);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    // ACTION_MOVE is special because it always has actionIndex == 0
                    // We'll call the move handlers for all indexes manually

                    // First process the historical events
                    for (int i = 0; i < event.getHistorySize(); i++) {
                        for (TouchContext aTouchContextMap : touchContextMap) {
                            if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                            {
                                aTouchContextMap.touchMoveEvent(
                                        (int)(event.getHistoricalX(aTouchContextMap.getActionIndex(), i) + xOffset),
                                        (int)(event.getHistoricalY(aTouchContextMap.getActionIndex(), i) + yOffset),
                                        event.getHistoricalEventTime(i));
                            }
                        }
                    }

                    // Now process the current values
                    for (TouchContext aTouchContextMap : touchContextMap) {
                        if (aTouchContextMap.getActionIndex() < event.getPointerCount())
                        {
                            aTouchContextMap.touchMoveEvent(
                                    (int)(event.getX(aTouchContextMap.getActionIndex()) + xOffset),
                                    (int)(event.getY(aTouchContextMap.getActionIndex()) + yOffset),
                                    event.getEventTime());
                        }
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    for (TouchContext aTouchContext : touchContextMap) {
                        aTouchContext.cancelTouch();
                        aTouchContext.setPointerCount(0);
                    }
                    break;
                default:
                    return false;
                }
            }

            // Handled a known source
            return true;
        }

        // Unknown class
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return handleMotionEvent(null, event) || super.onGenericMotionEvent(event);

    }

    private void updateMousePosition(View touchedView, MotionEvent event) {
        // X and Y are already relative to the provided view object
        float eventX, eventY;

        // For our StreamView itself, we can use the coordinates unmodified.
        if (touchedView == streamView) {
            eventX = event.getX(0);
            eventY = event.getY(0);
        }
        else {
            // For the containing background view, we must subtract the origin
            // of the StreamView to get video-relative coordinates.
            eventX = event.getX(0) - streamView.getX();
            eventY = event.getY(0) - streamView.getY();
        }

        if (event.getPointerCount() == 1 && event.getActionIndex() == 0 &&
                (event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS))
        {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_EXIT:
                case MotionEvent.ACTION_HOVER_MOVE:
                    if (event.getEventTime() - lastAbsTouchUpTime <= STYLUS_UP_DEAD_ZONE_DELAY &&
                            Math.sqrt(Math.pow(eventX - lastAbsTouchUpX, 2) + Math.pow(eventY - lastAbsTouchUpY, 2)) <= STYLUS_UP_DEAD_ZONE_RADIUS) {
                        // Enforce a small deadzone between touch up and hover or touch down to allow more precise double-clicking
                        return;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    if (event.getEventTime() - lastAbsTouchDownTime <= STYLUS_DOWN_DEAD_ZONE_DELAY &&
                            Math.sqrt(Math.pow(eventX - lastAbsTouchDownX, 2) + Math.pow(eventY - lastAbsTouchDownY, 2)) <= STYLUS_DOWN_DEAD_ZONE_RADIUS) {
                        // Enforce a small deadzone between touch down and move or touch up to allow more precise double-clicking
                        return;
                    }
                    break;
            }
        }

        // We may get values slightly outside our view region on ACTION_HOVER_ENTER and ACTION_HOVER_EXIT.
        // Normalize these to the view size. We can't just drop them because we won't always get an event
        // right at the boundary of the view, so dropping them would result in our cursor never really
        // reaching the sides of the screen.
        eventX = Math.min(Math.max(eventX, 0), streamView.getWidth());
        eventY = Math.min(Math.max(eventY, 0), streamView.getHeight());

        conn.sendMousePosition((short)eventX, (short)eventY, (short)streamView.getWidth(), (short)streamView.getHeight());
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent event) {
        return handleMotionEvent(view, event);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // Tell the OS not to buffer input events for us
            //
            // NB: This is still needed even when we call the newer requestUnbufferedDispatch()!
            view.requestUnbufferedDispatch(event);
        }

        return handleMotionEvent(view, event);
    }

    @Override
    public void stageStarting(final String stage) {
        logSessionInfo("CONNECT", "开始阶段: " + stage);
        streamLoadingController.onStageStarting(stage);
    }

    @Override
    public void stageComplete(String stage) {
        logSessionInfo("CONNECT", "完成阶段: " + stage);
    }

    private void stopConnection() {
        if (connecting || connected) {
            logSessionInfo("CONNECT", "请求停止串流连接");
            connecting = connected = false;
            UiHelper.notifyHdrWindowStatus(this, false);
            updatePipAutoEnter();
            audioRenderer = null;

            controllerHandler.stop();

            // Update GameManager state to indicate we're no longer in game
            UiHelper.notifyStreamEnded(this);

            // Stop may take a few hundred ms to do some network I/O to tell
            // the server we're going away and clean up. Let it run in a separate
            // thread to keep things smooth for the UI. Inside moonlight-common,
            // we prevent another thread from starting a connection before and
            // during the process of stopping this one.
            new Thread() {
                public void run() {
                    conn.stop();
                }
            }.start();
        }
    }

    @Override
    public void stageFailed(final String stage, final int portFlags, final int errorCode) {
        logSessionWarn("CONNECT", "连接阶段失败: " + stage + ", error=" + errorCode
                + (portFlags != 0 ? ", ports=" + portFlags : ""));
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe(stage + " failed: " + errorCode);

                    // If video initialization failed and the surface is still valid, display extra information for the user
                    if (stage.contains("video") && streamView.getHolder().getSurface().isValid()) {
                        Toast.makeText(Game.this, getResources().getText(R.string.video_decoder_init_failed), Toast.LENGTH_LONG).show();
                    }

                    String dialogText = getResources().getString(R.string.conn_error_msg) + " " + stage +" (error "+errorCode+")";

                    if (portFlags != 0) {
                        dialogText += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                MoonBridge.stringifyPortFlags(portFlags, "\n");
                    }

                    if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0)  {
                        dialogText += "\n\n" + getResources().getString(R.string.nettest_text_blocked);
                    }

                    streamLoadingController.showFailure(dialogText);
                }
            }
        });
    }

    @Override
    public void connectionTerminated(final int errorCode) {
        if (errorCode == MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
            logSessionInfo("CONNECT", "连接正常结束, code=" + errorCode);
        }
        else {
            logSessionWarn("CONNECT", "连接异常中断, code=" + errorCode);
        }
        // Perform a connection test if the failure could be due to a blocked port
        // This does network I/O, so don't do it on the main thread.
        final int portFlags = MoonBridge.getPortFlagsFromTerminationErrorCode(errorCode);
        final int portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER,443, portFlags);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Let the display go to sleep now
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // Stop processing controller input
                controllerHandler.stop();

                // Ungrab input
                setInputGrabState(false);

                if (!displayedFailureDialog) {
                    displayedFailureDialog = true;
                    LimeLog.severe("Connection terminated: " + errorCode);
                    stopConnection();

                    // Display the error dialog if it was an unexpected termination.
                    // Otherwise, just finish the activity immediately.
                    if (errorCode != MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                        String message;

                        if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                            // If we got a blocked result, that supersedes any other error message
                            message = getResources().getString(R.string.nettest_text_blocked);
                        }
                        else {
                            switch (errorCode) {
                                case MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC:
                                    message = getResources().getString(R.string.no_video_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_NO_VIDEO_FRAME:
                                    message = getResources().getString(R.string.no_frame_received_error);
                                    break;

                                case MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION:
                                case MoonBridge.ML_ERROR_PROTECTED_CONTENT:
                                    message = getResources().getString(R.string.early_termination_error);
                                    break;

                                case MoonBridge.ML_ERROR_FRAME_CONVERSION:
                                    message = getResources().getString(R.string.frame_conversion_error);
                                    break;

                                default:
                                    String errorCodeString;
                                    // We'll assume large errors are hex values
                                    if (Math.abs(errorCode) > 1000) {
                                        errorCodeString = Integer.toHexString(errorCode);
                                    }
                                    else {
                                        errorCodeString = Integer.toString(errorCode);
                                    }
                                    message = getResources().getString(R.string.conn_terminated_msg) + "\n\n" +
                                            getResources().getString(R.string.error_code_prefix) + " " + errorCodeString;
                                    break;
                            }
                        }

                        if (portFlags != 0) {
                            message += "\n\n" + getResources().getString(R.string.check_ports_msg) + "\n" +
                                    MoonBridge.stringifyPortFlags(portFlags, "\n");
                        }

                        AppDialog.showMessage(
                                Game.this,
                                getResources().getString(R.string.conn_terminated_title),
                                message,
                                getResources().getString(R.string.dialog_action_close),
                                Game.this::finish,
                                getResources().getString(R.string.help),
                                () -> {
                                    HelpLauncher.launchTroubleshooting(Game.this);
                                    finish();
                                },
                                false);
                    }
                    else {
                        finish();
                    }
                }
            }
        });
    }

    @Override
    public void connectionStatusUpdate(final int connectionStatus) {
        if (connectionStatus != lastLoggedConnectionStatus) {
            lastLoggedConnectionStatus = connectionStatus;
            logSessionInfo("NETWORK", connectionStatus == MoonBridge.CONN_STATUS_POOR
                    ? "连接质量变差" : connectionStatus == MoonBridge.CONN_STATUS_OKAY
                    ? "连接质量恢复" : "连接状态=" + connectionStatus);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (prefConfig.disableWarnings) {
                    return;
                }

                if (connectionStatus == MoonBridge.CONN_STATUS_POOR) {
                    if (prefConfig.bitrate > 5000) {
                        notificationOverlayView.setText(getResources().getString(R.string.slow_connection_msg));
                    }
                    else {
                        notificationOverlayView.setText(getResources().getString(R.string.poor_connection_msg));
                    }

                    requestedNotificationOverlayVisibility = View.VISIBLE;
                }
                else if (connectionStatus == MoonBridge.CONN_STATUS_OKAY) {
                    requestedNotificationOverlayVisibility = View.GONE;
                }

                if (!isHidingOverlays) {
                    notificationOverlayView.setVisibility(requestedNotificationOverlayVisibility);
                }
            }
        });
    }

    @Override
    public void connectionStarted() {
        logSessionInfo("CONNECT", "串流连接已建立");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connected = true;
                connecting = false;
                streamStartElapsedMs = SystemClock.elapsedRealtime();
                updatePipAutoEnter();
                streamLoadingController.onConnectionStarted();

                // Hide the mouse cursor after a short delay so pointer capture
                // isn't disturbed while the startup overlay is transitioning out.
                Handler h = new Handler();
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setInputGrabState(true);
                    }
                }, 500);

                // Keep the display on
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

                // Update GameManager state to indicate we're in game
                UiHelper.notifyStreamConnected(Game.this);

                hideSystemUi(1000);
            }
        });

        // Report this shortcut being used (off the main thread to prevent ANRs)
        ComputerDetails computer = new ComputerDetails();
        computer.name = pcName;
        computer.uuid = Game.this.getIntent().getStringExtra(EXTRA_PC_UUID);
        ShortcutHelper shortcutHelper = new ShortcutHelper(this);
        shortcutHelper.reportComputerShortcutUsed(computer);
        if (appName != null) {
            // This may be null if launched from the "Resume Session" PC context menu item
            shortcutHelper.reportGameLaunched(computer, app);
        }
    }

    @Override
    public void displayMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void displayTransientMessage(final String message) {
        if (!prefConfig.disableWarnings) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(Game.this, message, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor) {
        LimeLog.info(String.format((Locale)null, "Rumble on gamepad %d: %04x %04x", controllerNumber, lowFreqMotor, highFreqMotor));
        controllerHandler.handleRumble(controllerNumber, lowFreqMotor, highFreqMotor);
        //联动扳机震动
        if(prefConfig.gameTriggerRumbleLink){
            rumbleTriggers(controllerNumber,lowFreqMotor,highFreqMotor);
        }
        if(!prefConfig.showRumbleHUD){
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                performanceRumble.setText(String.format((Locale)null, "手柄%d 震动信号 高%d 低%d", controllerNumber,  (short)((highFreqMotor >> 8) & 0xFF),  (short)((lowFreqMotor >> 8) & 0xFF)));
            }
        });
    }

    @Override
    public void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger) {
        LimeLog.info(String.format((Locale)null, "Rumble on gamepad triggers %d: %04x %04x", controllerNumber, leftTrigger, rightTrigger));

        controllerHandler.handleRumbleTriggers(controllerNumber, leftTrigger, rightTrigger);
    }

    @Override
    public void setHdrMode(boolean enabled, byte[] hdrMetadata) {
        LimeLog.info("Display HDR mode: " + (enabled ? "enabled" : "disabled"));
        decoderRenderer.setHdrMode(enabled, hdrMetadata);
        if (fsrVideoProcessor != null) {
            fsrVideoProcessor.setHdrToneMappingEnabled(enabled);
        }
        UiHelper.notifyHdrWindowStatus(this, enabled);
    }

    @Override
    public void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz) {
        LimeLog.info("axi-->: controllerNumber" + controllerNumber+"-motionType:"+motionType+"-reportRateHz:"+reportRateHz);
        controllerHandler.handleSetMotionEventState(controllerNumber, motionType, reportRateHz);
    }

    @Override
    public void setControllerLED(short controllerNumber, byte r, byte g, byte b) {
        controllerHandler.handleSetControllerLED(controllerNumber, r, g, b);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (glesRenderingEnabled && (fsrView == null || holder != fsrView.getHolder())) {
            return;
        }

        if (!surfaceCreated) {
            throw new IllegalStateException("Surface changed before creation!");
        }

        LimeLog.info("surfaceChanged-->"+width+" x "+height + "----"+prefConfig.width+" x "+prefConfig.height);
        if (glesRenderingEnabled) {
            return;
        }
        if (!attemptedConnection) {
            attemptedConnection = true;
            logSessionInfo("CONNECT", "渲染表面就绪，开始连接；渲染路径=Surface");

            // Update GameManager state to indicate we're "loading" while connecting
            UiHelper.notifyStreamConnecting(Game.this);

            decoderRenderer.setRenderTarget(holder.getSurface());
            audioRenderer = new AndroidAudioRenderer(Game.this, controllerHandler, prefConfig.enableAudioFx,
                    prefConfig.enableAudioHaptics, prefConfig.audioHapticsStrength,
                    prefConfig.audioHapticsVoiceFilter, prefConfig.audioHapticsOutputTarget);
            streamLoadingController.showPreparing(null);
            conn.start(audioRenderer,
                    decoderRenderer, Game.this);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        logSessionInfo("VIDEO", glesRenderingEnabled && fsrView != null && holder == fsrView.getHolder()
                ? "GLES 显示表面已创建" : "视频表面已创建");
        float desiredFrameRate;

        if (glesRenderingEnabled) {
            if (fsrView == null || holder != fsrView.getHolder()) {
                return;
            }
            fsrDisplaySurfaceCreated = true;
            startConnectionIfReady();
        }

        surfaceCreated = true;

        // Android will pick the lowest matching refresh rate for a given frame rate value, so we want
        // to report the true FPS value if refresh rate reduction is enabled. We also report the true
        // FPS value if there's no suitable matching refresh rate. In that case, Android could try to
        // select a lower refresh rate that avoids uneven pull-down (ex: 30 Hz for a 60 FPS stream on
        // a display that maxes out at 50 Hz).
        if (stereo3dEnabled) {
            desiredFrameRate = Math.min(prefConfig.fps, 60.0f);
        }
        else if (mayReduceRefreshRate() || desiredRefreshRate < prefConfig.fps) {
            desiredFrameRate = prefConfig.fps;
        }
        else {
            // Otherwise, we will pretend that our frame rate matches the refresh rate we picked in
            // prepareDisplayForRendering(). This will usually be the highest refresh rate that our
            // frame rate evenly divides into, which ensures the lowest possible display latency.
            desiredFrameRate = desiredRefreshRate;
        }

        // Tell the OS about our frame rate to allow it to adapt the display refresh rate appropriately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // We want to change frame rate even if it's not seamless, since prepareDisplayForRendering()
            // will not set the display mode on S+ if it only differs by the refresh rate. It depends
            // on us to trigger the frame rate switch here.
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS);
        }
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            holder.getSurface().setFrameRate(desiredFrameRate,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        logSessionInfo("VIDEO", "视频表面已销毁");
        if (glesRenderingEnabled) {
            if (fsrView == null || holder != fsrView.getHolder()) {
                return;
            }
            fsrDisplaySurfaceCreated = false;
        }

        if (!surfaceCreated) {
            throw new IllegalStateException("Surface destroyed before creation!");
        }

        surfaceCreated = false;

        if (attemptedConnection) {
            // Let the decoder know immediately that the surface is gone
            decoderRenderer.prepareForStop();

            if (connected) {
                stopConnection();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return;
        }

        awaitingRecordAudioPermission = false;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingMicToggleAfterPermission) {
                pendingMicToggleAfterPermission = false;
                switchMic();
            }
            return;
        }

        pendingMicToggleAfterPermission = false;

        Toast.makeText(this, getResources().getString(R.string.mic_uplink_permission_denied), Toast.LENGTH_LONG).show();
    }

    @Override
    public void mouseMove(int deltaX, int deltaY) {
        conn.sendMouseMove((short) deltaX, (short) deltaY);
    }

    @Override
    public void mouseButtonEvent(int buttonId, boolean down) {
        byte buttonIndex;

        switch (buttonId)
        {
        case EvdevListener.BUTTON_LEFT:
            buttonIndex = MouseButtonPacket.BUTTON_LEFT;
            break;
        case EvdevListener.BUTTON_MIDDLE:
            buttonIndex = MouseButtonPacket.BUTTON_MIDDLE;
            break;
        case EvdevListener.BUTTON_RIGHT:
            buttonIndex = MouseButtonPacket.BUTTON_RIGHT;
            break;
        case EvdevListener.BUTTON_X1:
            buttonIndex = MouseButtonPacket.BUTTON_X1;
            break;
        case EvdevListener.BUTTON_X2:
            buttonIndex = MouseButtonPacket.BUTTON_X2;
            break;
        default:
            LimeLog.warning("Unhandled button: "+buttonId);
            return;
        }

        if (down) {
            conn.sendMouseButtonDown(buttonIndex);
        }
        else {
            conn.sendMouseButtonUp(buttonIndex);
        }
    }

    @Override
    public void mouseVScroll(byte amount) {
        conn.sendMouseScroll(amount);
    }

    @Override
    public void mouseHScroll(byte amount) {
        conn.sendMouseHScroll(amount);
    }

    public void mouseHighResScroll(boolean up){
        conn.sendMouseHighResScroll((short) (up?prefConfig.mouseSCAmount*50:-50*prefConfig.mouseSCAmount));
    }

    @Override
    public void keyboardEvent(boolean buttonDown, short keyCode) {
        short keyMap = keyboardTranslator.translate(keyCode, -1);
        if (keyMap != 0) {
            // handleSpecialKeys() takes the Android keycode
            if (handleSpecialKeys(keyCode, buttonDown)) {
                return;
            }

            if (buttonDown) {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_DOWN, getModifierState(), (byte)0);
            }
            else {
                conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_UP, getModifierState(), (byte)0);
            }
        }
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        // Don't do anything if we're not connected
        if (!connected) {
            return;
        }

        // This flag is set for all devices
        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
            hideSystemUi(2000);
        }
        else if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
            hideSystemUi(2000);
        }
    }

    @Override
    public void onPerfUpdate(final PerfOverlayStats stats) {
        long now = SystemClock.elapsedRealtime();
        if (stats != null && now - lastSessionPerfLogElapsedMs >= 10000) {
            lastSessionPerfLogElapsedMs = now;
            logSessionInfo("PERF", String.format(Locale.US,
                    "%dx%d %s, FPS %.1f/%.1f, 网络 %.0f Kbps, 视频 %.0f Kbps, 音频 %.0f Kbps, "
                            + "延迟 %d±%d ms, 丢包 %.2f%%, 解码 %.2f ms, 主机 %.2f ms",
                    stats.width, stats.height, nonEmpty(stats.codecName, "--"),
                    stats.renderedFps, stats.receivedFps, stats.networkRateKbps,
                    stats.videoRateKbps, stats.audioRateKbps, stats.networkLatencyMs,
                    stats.networkLatencyVarianceMs, stats.packetLossPercent,
                    stats.decodeTimeMs, stats.hostProcessingLatencyMs));
            logSessionInfo("STATE", "渲染=" + buildRenderPipelineText()
                    + "；USB手柄=" + buildUsbControllerStatusText()
                    + "；游戏震动=" + buildNativeGameHapticsStatusText()
                    + "；音频震动=" + buildAudioHapticsStatusText());
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(prefConfig.enablePerfOverlayLite){
                    String displayText = buildLitePerfInfo(stats);
                    CharSequence styledDisplayText = applyPerfOverlayColors(displayText);
                    performanceOverlayLite.setText(styledDisplayText);
                }else{
                    renderFullPerfInfo(stats);
                }
            }
        });
    }

    private String buildLitePerfInfo(PerfOverlayStats stats) {
        if (stats == null) {
            return "--";
        }

        StringBuilder builder = new StringBuilder();
        if (stats.networkRateKbps > 0) {
            builder.append("带宽：").append(formatThroughput(stats.networkRateKbps)).append("  ");
        }
        if (prefConfig.enablePerfOverlayLiteExt) {
            builder.append(stats.width > 0 && stats.height > 0
                    ? stats.width + "x" + stats.height
                    : prefConfig.width + "x" + prefConfig.height);
            builder.append(" ");
            builder.append(nonEmpty(stats.codecName, "--"));
            builder.append("  ");
        }
        if (fsrEnabled) {
            builder.append(buildFsrPerfLabel()).append("  ");
        }
        if (stereo3dEnabled) {
            builder.append("SBS 3D  ");
        }
        builder.append("延迟/解码：");
        builder.append(stats.networkLatencyMs).append(" ms / ");
        builder.append(stats.decodeTimeMs > 0 ? String.format(Locale.US, "%.2f ms", stats.decodeTimeMs) : "--");
        builder.append("  丢包率：").append(String.format(Locale.US, "%.2f%%", stats.packetLossPercent));
        builder.append("  FPS：").append(String.format(Locale.US, "%.2f", stats.totalFps));
        if (micStatus == 1) {
            builder.append(" Mic");
        }
        return builder.toString();
    }

    private String buildFsrPerfLabel() {
        String target = getFsrTargetDisplayName();
        if(prefConfig.enablePerfOverlayLite){
            return "FSR " + target;
        }
        return "FSR " + target + " / 锐化 " + getFsrSharpnessDisplayName();
    }

    private void renderFullPerfInfo(PerfOverlayStats stats) {
        if (performanceOverlayBigContent == null) {
            return;
        }
        performanceOverlayBigContent.removeAllViews();

        if (stats == null) {
            addPerfRow("状态", "--");
            return;
        }

        addPerfRow("分辨率", stats.width > 0 && stats.height > 0
                ? stats.width + "x" + stats.height + (stats.hdr ? " HDR" : "")
                : prefConfig.width + "x" + prefConfig.height
                + (prefConfig.enableHdr && !stereo3dEnabled ? " HDR" : ""));
        addPerfRow("编码", nonEmpty(stats.codecName, "--"));
        addPerfRow("目标码率", formatMbps(stats.targetBitrateKbps > 0 ? stats.targetBitrateKbps : prefConfig.bitrate));
        addPerfRow("目标帧率", (stats.targetFps > 0 ? stats.targetFps : prefConfig.fps) + " FPS");
        addPerfRow("实时帧率", formatFps(stats.totalFps));
        addPerfRow("视频码率", formatRate(stats.videoRateKbps));
        addPerfRow("音频码率", formatRate(stats.audioRateKbps));
        addPerfRow("累计视频流量", formatBytes(stats.videoBytes));
        addPerfRow("累计音频流量", formatBytes(stats.audioBytes));
        addPerfRow("渲染方式", glesRenderingEnabled ? "GLES渲染" : "系统渲染");
        addPerfRow("3D输出", stereo3dEnabled
                ? "SBS 3840x1080 / 景深" + getStereo3dDepthDisplayName()
                : "关闭");
        addPerfRow("超分状态", buildUpscaleStatusText());
        addPerfRow("实际渲染链", buildRenderPipelineText());
        addPerfRow("连接地址", nonEmpty(streamHost, "--"));
        addPerfRow("本地时长", buildSessionDurationText());
        addPerfRow("网络延迟", stats.networkLatencyMs > 0
                ? stats.networkLatencyMs + " ms / 抖动 " + stats.networkLatencyVarianceMs + " ms"
                : "--");
        addPerfRow("丢包率", String.format(Locale.US, "%.2f%%", stats.packetLossPercent));
        addPerfRow("解码延迟", stats.decodeTimeMs > 0 ? String.format(Locale.US, "%.2f ms", stats.decodeTimeMs) : "--");
        addPerfRow("主机延迟", stats.hostProcessingLatencyMs > 0 ? String.format(Locale.US, "%.1f ms", stats.hostProcessingLatencyMs) : "--");
        addPerfRow("麦克风", micStatus == 1 ? "开启" : "关闭");
        addPerfRow("游戏震动", buildNativeGameHapticsStatusText());
        addPerfRow("音频震动", buildAudioHapticsStatusText());
        addPerfRow("USB手柄", buildUsbControllerStatusText());
    }

    private void addPerfRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = performanceOverlayBigContent.getChildCount() == 0 ? 0 : UiHelper.dpToPx(this, 6);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(this);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelView.setLayoutParams(labelParams);
        labelView.setText(label);
        labelView.setTextColor(Color.argb(204, 218, 230, 255));
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        labelView.setSingleLine(true);

        TextView valueView = new TextView(this);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f);
        valueView.setLayoutParams(valueParams);
        valueView.setText(applyPerfOverlayColors(nonEmpty(value, "--")));
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        valueView.setGravity(Gravity.END);
        valueView.setSingleLine(true);

        row.addView(labelView);
        row.addView(valueView);
        performanceOverlayBigContent.addView(row);
    }

    private String buildUpscaleStatusText() {
        if (!fsrEnabled) {
            return "关闭";
        }
        return getFsrTargetDisplayName() + " / "
                + getFsrSharpnessDisplayName() + " / "
                + (isFsrNativeHdrOutputEnabled() ? "HDR" : "SDR");
    }

    private String buildRenderPipelineText() {
        if (!glesRenderingEnabled) {
            return "系统直出";
        }
        String stereoSuffix = stereo3dEnabled ? " → SBS 3D" : "";
        if (!fsrEnabled) {
            return "GLES直通" + stereoSuffix;
        }
        return (isFsrNativeHdrOutputEnabled() ? "GLES FSR HDR" : "GLES FSR SDR")
                + stereoSuffix;
    }

    private String buildUsbControllerStatusText() {
        if (controllerHandler != null && controllerHandler.hasActiveUsbController()) {
            String controllerType = controllerHandler.getActiveUsbControllerTypeDisplayName();
            String protocol = controllerHandler.getActiveUsbControllerProtocolDisplayName();
            StringBuilder status = new StringBuilder("已连接");
            if (controllerType != null && !controllerType.isEmpty()) {
                status.append(" / ").append(controllerType);
            }
            if (protocol != null && !protocol.isEmpty()) {
                status.append(" / ").append(protocol);
            }
            return status.toString();
        }
        if (!prefConfig.usbDriver) {
            return "关闭";
        }
        return connectedToUsbDriverService ? "待机" : "未启动";
    }

    private String buildNativeGameHapticsStatusText() {
        return controllerHandler == null
                ? "待触发"
                : controllerHandler.getNativeGameHapticsOutputRouteDisplayName();
    }

    private String buildAudioHapticsStatusText() {
        if (!prefConfig.enableAudioHaptics) {
            return "关闭";
        }

        String outputRoute;
        if ("controller".equals(prefConfig.audioHapticsOutputTarget)) {
            outputRoute = controllerHandler == null
                    ? "待触发"
                    : controllerHandler.getAudioHapticsOutputRouteDisplayName();
            if ("待触发".equals(outputRoute)) {
                outputRoute += " / 手柄";
            }
        }
        else {
            outputRoute = audioRenderer != null && audioRenderer.hasRecentPhoneAudioHapticsOutput()
                    ? "普通震动 / 手机"
                    : "待触发 / 手机";
        }

        return outputRoute
                + " / 滤" + getAudioHapticsVoiceFilterDisplayName()
                + " / " + prefConfig.audioHapticsStrength + "%";
    }

    private String getAudioHapticsVoiceFilterDisplayName() {
        if ("low".equals(prefConfig.audioHapticsVoiceFilter)) {
            return "低";
        }
        if ("medium".equals(prefConfig.audioHapticsVoiceFilter)) {
            return "中";
        }
        if ("high".equals(prefConfig.audioHapticsVoiceFilter)) {
            return "高";
        }
        return "关";
    }

    private String buildSessionDurationText() {
        if (streamStartElapsedMs <= 0) {
            return "--";
        }
        long totalSeconds = Math.max(0, (SystemClock.elapsedRealtime() - streamStartElapsedMs) / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatFps(float fps) {
        return fps > 0 ? String.format(Locale.US, "%.2f FPS", fps) : "--";
    }

    private String formatMbps(int kbps) {
        return kbps > 0 ? String.format(Locale.US, "%.0f Mbps", kbps / 1000f) : "--";
    }

    private String formatRate(float kbps) {
        if (kbps <= 0) {
            return "--";
        }
        if (kbps >= 1000f) {
            return String.format(Locale.US, "%.2f Mbps", kbps / 1000f);
        }
        return String.format(Locale.US, "%.0f Kbps", kbps);
    }

    private String formatThroughput(float kbps) {
        if (kbps <= 0) {
            return "--";
        }
        float kilobytesPerSecond = kbps / 8f;
        if (kilobytesPerSecond >= 1024f) {
            return String.format(Locale.US, "%.2fM/s", kilobytesPerSecond / 1024f);
        }
        return String.format(Locale.US, "%.2fK/s", kilobytesPerSecond);
    }

    private String formatBytes(long bytes) {
        return bytes > 0 ? Formatter.formatShortFileSize(this, bytes) : "--";
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private CharSequence applyPerfOverlayColors(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        SpannableString spannable = new SpannableString(text);
        applyFsrSpan(spannable, text);
        applyMicSpan(spannable, text);
        return spannable;
    }

    private void applyFsrSpan(SpannableString spannable, String text) {
        if (!fsrEnabled) {
            return;
        }
        int start = text.indexOf("FSR ");
        if (start < 0) {
            return;
        }

        int end = text.indexOf('\n', start);
        if (end < 0) {
            end = text.indexOf("  ", start);
        }
        if (end < 0) {
            end = text.length();
        }

        spannable.setSpan(new ForegroundColorSpan(Color.rgb(250, 191, 2)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void applyMicSpan(SpannableString spannable, String text) {
        int start = text.indexOf("Mic");
        if (start < 0) {
            start = text.indexOf("麦克风");
        }
        if (start < 0) {
            return;
        }
        int end = text.indexOf('\n', start);
        if (end < 0) {
            end = text.length();
        }
        spannable.setSpan(new ForegroundColorSpan(Color.rgb(79, 210, 122)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Override
    public void onUsbPermissionPromptStarting() {
        logSessionInfo("USB", "正在请求 USB 手柄授权");
        usbPermissionPromptVisible = true;
        streamLoadingController.setCancelEnabled(false);
        // Disable PiP auto-enter while the USB permission prompt is on-screen. This prevents
        // us from entering PiP while the user is interacting with the OS permission dialog.
        suppressPipRefCount++;
        updatePipAutoEnter();
    }

    @Override
    public void onUsbPermissionPromptCompleted() {
        logSessionInfo("USB", "USB 手柄授权流程已结束");
        usbPermissionPromptVisible = false;
        streamLoadingController.setCancelEnabled(true);
        suppressPipRefCount--;
        updatePipAutoEnter();
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
        switch (keyEvent.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return handleKeyDown(keyEvent);
            case KeyEvent.ACTION_UP:
                return handleKeyUp(keyEvent);
            case KeyEvent.ACTION_MULTIPLE:
                return handleKeyMultiple(keyEvent);
            default:
                return false;
        }
    }

    @Override
    public void onBackPressed() {
        if (streamLoadingController != null && streamLoadingController.isVisible()) {
            if (!usbPermissionPromptVisible) {
                finish();
            }
            return;
        }
        if(prefConfig.enableQtDialog){
            showGameMenu(null);
            return;
        }
        super.onBackPressed();
    }

    //禁用鼠标
    private boolean disableMouseModel;

    //本地鼠标光标切换
    public void switchMouseLocalCursor(){
        if (!grabbedInput) {
            inputCaptureProvider.enableCapture();
            grabbedInput = true;
        }
        cursorVisible = !cursorVisible;
        if (cursorVisible) {
            inputCaptureProvider.showCursor();
        } else {
            inputCaptureProvider.hideCursor();
        }
    }

    public void switchMouseModel(int which){
        disableMouseModel=false;
        //多点触控
        if(which==0){
            prefConfig.enableMultiTouchScreen=true;
            prefConfig.touchscreenTrackpad=false;
        }
        //普通鼠标模式
        if(which==1){
            prefConfig.enableMultiTouchScreen=false;
            prefConfig.touchscreenTrackpad=false;
        }
        //触控板模式
        if(which==2){
            prefConfig.enableMultiTouchScreen=false;
            prefConfig.touchscreenTrackpad=true;
        }
        //禁用鼠标
        if(which==3){
            disableMouseModel=true;
            return;
        }
        //普通鼠标 左右键互换
        if(which==4){
            prefConfig.enableMultiTouchScreen=false;
            prefConfig.touchscreenTrackpad=false;
        }

        //触控板模式 仅移动
        if(which==5){
            prefConfig.enableMultiTouchScreen=false;
            prefConfig.touchscreenTrackpad=true;
        }

        //触控板模式 仅移动&左键点击
        if(which==6){
            prefConfig.enableMultiTouchScreen=false;
            prefConfig.touchscreenTrackpad=true;
        }

        for (int i = 0; i < touchContextMap.length; i++) {
            if (!prefConfig.touchscreenTrackpad) {
                if(which==4){
                    touchContextMap[i] = new AbsoluteTouchSwitchContext(conn, i, streamView);
                }else{
                    touchContextMap[i] = new AbsoluteTouchContext(conn, i, streamView);
                }
            }
            else {
                if(which==5||which==6){
                    touchContextMap[i] = new RelativeTouchSwitchContext(conn, i,
                            REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                            streamView, prefConfig, which != 5);
                }else{
                    touchContextMap[i] = new RelativeTouchContext(conn, i,
                            REFERENCE_HORIZ_RES, REFERENCE_VERT_RES,
                            streamView, prefConfig);
                }
            }
        }
    }

    public void showHUD(){
        prefConfig.enablePerfOverlay=!prefConfig.enablePerfOverlay;
        if(prefConfig.enablePerfOverlay){
            performanceOverlayView.setVisibility(View.VISIBLE);
            if(prefConfig.enablePerfOverlayLite){
                performanceOverlayLite.setVisibility(View.VISIBLE);
            }else{
                performanceOverlayBig.setVisibility(View.VISIBLE);
            }
            return;
        }
        performanceOverlayView.setVisibility(View.GONE);
    }

    public void switchHUD(){
        performanceOverlayView.setVisibility(View.VISIBLE);
        if(performanceOverlayLite.getVisibility()==View.VISIBLE){
            prefConfig.enablePerfOverlayLite=false;
            performanceOverlayBig.setVisibility(View.VISIBLE);
            performanceOverlayLite.setVisibility(View.GONE);
            return;
        }
        prefConfig.enablePerfOverlayLite=true;
        performanceOverlayBig.setVisibility(View.GONE);
        performanceOverlayLite.setVisibility(View.VISIBLE);
    }

    //切换触控灵敏度开关
    public void switchTouchSensitivity(){
        prefConfig.enableTouchSensitivity=!prefConfig.enableTouchSensitivity;
    }

    //更新虚拟布局视图
    public void updateVirtualView(){
        if (virtualController != null && prefConfig.onscreenController) {
            virtualController.refreshLayout();
        }
        if(keyBoardController !=null && prefConfig.enableKeyboard){
            keyBoardController.refreshLayout();
        }
        if(keyBoardLayoutController!=null){
            keyBoardLayoutController.refreshLayout();
        }
    }

    //切换虚拟手柄模式
    public void switchVirtualController(KeyBoardController.ControllerMode mode){
        if(virtualController==null||!prefConfig.onscreenController){
            Toast.makeText(this,"请先打开虚拟手柄开关！",Toast.LENGTH_SHORT).show();
            return;
        }
        virtualController.switchMode(mode);

    }
    //返回虚拟手柄当前的状态
    public KeyBoardController.ControllerMode getVirtualControllerMode(){
        if(virtualController==null){
            return KeyBoardController.ControllerMode.NONE;
        }
        return virtualController.getControllerMode();
    }

    //切换虚拟手柄模式
    public void switchVirtualKeyController(KeyBoardController.ControllerMode mode){
        if(keyBoardController==null||!prefConfig.enableKeyboard){
            Toast.makeText(this,"请先打开虚拟按键开关！",Toast.LENGTH_SHORT).show();
            return;
        }
        keyBoardController.switchMode(mode);

    }
    //返回虚拟手柄当前的状态
    public KeyBoardController.ControllerMode getVirtualKeyControllerMode(){
        if(keyBoardController==null){
            return KeyBoardController.ControllerMode.NONE;
        }
        return keyBoardController.getControllerMode();
    }


    public boolean isPortrait;

    //横竖屏切换
    public void switchLandscapePortraitScreen(){
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE){
            isPortrait =true;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        }else{
            isPortrait =false;
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);
        }
    }

    // 会话内画面查看模式：手势缩放/平移，轻点发送鼠标左键。
    public void screenMoveZoom(){
        if (videoZoomGestureOverlay == null) {
            return;
        }

        boolean enabled = !videoZoomGestureOverlay.isModeEnabled();
        videoZoomGestureOverlay.setModeEnabled(enabled);
        setVideoZoomInputSuppressed(false);
        setVirtualMouseInputSuppressed(false);
        if (enabled) {
            refreshVideoZoomOverlayZOrder();
        }
        Toast.makeText(this, enabled ? R.string.video_zoom_enabled_hint
                : R.string.video_zoom_disabled, Toast.LENGTH_SHORT).show();
    }

    public boolean getScreenMoveZoom(){
        return videoZoomGestureOverlay != null && videoZoomGestureOverlay.isModeEnabled();
    }

    public boolean isVirtualMouseEnabled() {
        return virtualMouseOverlay != null && virtualMouseOverlay.isEnabledForCurrentStream();
    }

    public void toggleVirtualMouse() {
        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.toggleEnabledForCurrentStream();
        }
    }

    public void setVirtualMouseInputSuppressed(boolean suppressed) {
        if (virtualMouseOverlay == null) {
            return;
        }

        boolean gameMenuShowing = dialogGameMenu != null
                && dialogGameMenu.getDialog() != null
                && dialogGameMenu.getDialog().isShowing();
        boolean fullKeyboardShowing = keyBoardLayoutController != null
                && keyBoardLayoutController.isVisible();
        virtualMouseOverlay.setInputSuppressed(suppressed
                || disableMouseModel
                || getScreenMoveZoom()
                || isHidingOverlays
                || gameMenuShowing
                || fullKeyboardShowing
                || !hasWindowFocus());
    }

    public void setVideoZoomInputSuppressed(boolean suppressed) {
        if (videoZoomGestureOverlay == null) {
            return;
        }

        boolean gameMenuShowing = dialogGameMenu != null
                && dialogGameMenu.getDialog() != null
                && dialogGameMenu.getDialog().isShowing();
        boolean fullKeyboardShowing = keyBoardLayoutController != null
                && keyBoardLayoutController.isVisible();
        videoZoomGestureOverlay.setInputSuppressed(suppressed
                || isHidingOverlays
                || gameMenuShowing
                || fullKeyboardShowing
                || !hasWindowFocus());
        if (videoZoomGestureOverlay.isModeEnabled()
                && videoZoomGestureOverlay.getVisibility() == View.VISIBLE) {
            refreshVideoZoomOverlayZOrder();
        }
    }

    private void handleVideoZoomTap(float x, float y) {
        if (conn == null || videoZoomController == null
                || !videoZoomController.mapPointToNormalizedVideo(x, y, videoZoomTapPoint)) {
            return;
        }

        videoZoomController.getBaseVideoRect(videoZoomBaseRect);
        int referenceWidth = Math.max(1, Math.min(Short.MAX_VALUE,
                Math.round(videoZoomBaseRect.width())));
        int referenceHeight = Math.max(1, Math.min(Short.MAX_VALUE,
                Math.round(videoZoomBaseRect.height())));
        int positionX = Math.max(0, Math.min(referenceWidth - 1,
                Math.round(videoZoomTapPoint.x * (referenceWidth - 1))));
        int positionY = Math.max(0, Math.min(referenceHeight - 1,
                Math.round(videoZoomTapPoint.y * (referenceHeight - 1))));
        conn.sendMousePosition((short) positionX, (short) positionY,
                (short) referenceWidth, (short) referenceHeight);
        conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT);
        conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT);
    }

    private void refreshVideoZoomOverlayZOrder() {
        if (videoZoomGestureOverlay == null) {
            return;
        }
        videoZoomGestureOverlay.bringToFront();
        if (performanceOverlayView != null) {
            performanceOverlayView.bringToFront();
        }
        if (notificationOverlayView != null) {
            notificationOverlayView.bringToFront();
        }
        View loadingOverlay = findViewById(R.id.stream_loading_overlay);
        if (loadingOverlay != null) {
            loadingOverlay.bringToFront();
        }
        if (virtualMouseOverlay != null) {
            virtualMouseOverlay.bringToFront();
        }
    }

    private VirtualMouseController createVirtualMouseController() {
        return new VirtualMouseController(new RemoteMouseSink() {
            @Override
            public void sendAbsolutePosition(float x, float y,
                                             float referenceWidth, float referenceHeight) {
                if (conn == null) {
                    return;
                }

                int width = Math.max(1, Math.min(Short.MAX_VALUE, Math.round(referenceWidth)));
                int height = Math.max(1, Math.min(Short.MAX_VALUE, Math.round(referenceHeight)));
                float normalizedX = x / Math.max(referenceWidth, 1f);
                float normalizedY = y / Math.max(referenceHeight, 1f);
                int positionX = Math.max(0, Math.min(width - 1,
                        Math.round(normalizedX * (width - 1))));
                int positionY = Math.max(0, Math.min(height - 1,
                        Math.round(normalizedY * (height - 1))));
                conn.sendMousePosition((short) positionX, (short) positionY,
                        (short) width, (short) height);
            }

            @Override
            public void sendButton(Button button, boolean pressed) {
                if (conn == null) {
                    return;
                }

                byte remoteButton;
                switch (button) {
                    case LEFT:
                        remoteButton = MouseButtonPacket.BUTTON_LEFT;
                        break;
                    case RIGHT:
                        remoteButton = MouseButtonPacket.BUTTON_RIGHT;
                        break;
                    case MIDDLE:
                    default:
                        remoteButton = MouseButtonPacket.BUTTON_MIDDLE;
                        break;
                }

                if (pressed) {
                    conn.sendMouseButtonDown(remoteButton);
                }
                else {
                    conn.sendMouseButtonUp(remoteButton);
                }
            }

            @Override
            public void sendVerticalScroll(int ticks) {
                if (conn != null) {
                    conn.sendMouseScroll((byte) Math.max(Byte.MIN_VALUE,
                            Math.min(Byte.MAX_VALUE, ticks)));
                }
            }

            @Override
            public void sendHorizontalScroll(int ticks) {
                if (conn != null) {
                    conn.sendMouseHScroll((byte) Math.max(Byte.MIN_VALUE,
                            Math.min(Byte.MAX_VALUE, ticks)));
                }
            }
        });
    }

    private void getVirtualMouseBaseVideoRect(RectF outRect) {
        if (videoZoomController != null) {
            videoZoomController.getBaseVideoRect(outRect);
            return;
        }
        if (streamView == null || streamView.getWidth() <= 0 || streamView.getHeight() <= 0) {
            outRect.set(0f, 0f,
                    virtualMouseOverlay != null ? virtualMouseOverlay.getWidth() : 1f,
                    virtualMouseOverlay != null ? virtualMouseOverlay.getHeight() : 1f);
            return;
        }
        outRect.set(streamView.getLeft(), streamView.getTop(),
                streamView.getRight(), streamView.getBottom());
    }

    private void applyVirtualMouseVideoOffset(float offsetXPx, float offsetYPx) {
        if (videoZoomController != null) {
            videoZoomController.setExternalOffset(offsetXPx, offsetYPx);
        }
    }

    public void disconnect() {
        finish();
    }

    private GameMenuFragment dialogGameMenu;
    @Override
    public void showGameMenu(GameInputDevice device) {
        setVirtualMouseInputSuppressed(true);
        setVideoZoomInputSuppressed(true);
        if(dialogGameMenu!=null){
            dialogGameMenu=null;
        }
        dialogGameMenu=new GameMenuFragment();
        dialogGameMenu.setWidth(UiHelper.dpToPx(this,364));
        dialogGameMenu.setConn(conn);
        dialogGameMenu.setDevice(device);
        dialogGameMenu.setGame(this);
        dialogGameMenu.show(getFragmentManager());
    }


    private SecondaryDisplayPresentation presentation;
    public void showSecondScreen(){
        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        Display[] displays = displayManager.getDisplays();
        int mainDisplayId = Display.DEFAULT_DISPLAY;
        int secondaryDisplayId = -1;
        for (Display display : displays) {
//            LimeLog.info(display.toString());
            if (display.getDisplayId() != mainDisplayId) {
                secondaryDisplayId = display.getDisplayId();
                break;
            }
        }
        if (secondaryDisplayId != -1) {
            Display secondaryDisplay = displayManager.getDisplay(secondaryDisplayId);
            presentation = new SecondaryDisplayPresentation(this, secondaryDisplay);
            presentation.show();
            if (stereo3dEnabled) {
                boolean exactModeSelected = presentation.selectPreferredDisplayMode(
                        3840, 1080, Math.min(prefConfig.fps, 60.0f));
                logSessionInfo("DISPLAY", exactModeSelected
                        ? "外接显示器已请求 3840x1080 SBS 模式"
                        : "外接显示器未暴露 3840x1080 模式，SBS 将适配实际显示尺寸");
            }
            View renderView = glesRenderingEnabled ? fsrView : streamView;
            if (renderView != null && renderView.getParent() instanceof ViewGroup) {
                ((ViewGroup) renderView.getParent()).removeView(renderView);
                presentation.addView(renderView);
            }

        }
    }


    // 设置surfaceView的圆角 setSurfaceviewCorner(UiHelper.dpToPx(this,24));
    private void setSurfaceviewCorner(final float radius) {

        streamView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                Rect rect = new Rect();
                view.getGlobalVisibleRect(rect);
                int leftMargin = 0;
                int topMargin = 0;
                Rect selfRect = new Rect(leftMargin, topMargin, rect.right - rect.left - leftMargin, rect.bottom - rect.top - topMargin);
                outline.setRoundRect(selfRect, radius);
            }
        });
        streamView.setClipToOutline(true);
    }

    private boolean isGlesRenderingEnabled() {
        return stereo3dEnabled
                || prefConfig.videoRenderMode == PreferenceConfiguration.VideoRenderMode.GLES;
    }

    private boolean isStereo3dEnabled() {
        return PreferenceConfiguration.STEREO_3D_MODE_SBS
                .equalsIgnoreCase(prefConfig.stereo3dMode);
    }

    private boolean isFsrEnabled() {
        return glesRenderingEnabled && !"off".equalsIgnoreCase(getFsrTarget());
    }

    private float getStereo3dDepthStrength() {
        if ("soft".equalsIgnoreCase(prefConfig.stereo3dDepth)) {
            return 0.003f;
        }
        if ("strong".equalsIgnoreCase(prefConfig.stereo3dDepth)) {
            return 0.009f;
        }
        return 0.006f;
    }

    private float getStereo3dConvergence() {
        if ("near".equalsIgnoreCase(prefConfig.stereo3dConvergence)) {
            return 0.44f;
        }
        if ("far".equalsIgnoreCase(prefConfig.stereo3dConvergence)) {
            return 0.56f;
        }
        return 0.50f;
    }

    private String getStereo3dDepthDisplayName() {
        if ("soft".equalsIgnoreCase(prefConfig.stereo3dDepth)) {
            return "弱";
        }
        if ("strong".equalsIgnoreCase(prefConfig.stereo3dDepth)) {
            return "强";
        }
        return "标准";
    }

    private float getFsrSharpness() {
        String value = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("list_fsr_sharpness", "standard");
        if ("soft".equalsIgnoreCase(value)) {
            return 0.55f;
        }
        if ("strong".equalsIgnoreCase(value)) {
            return 1.45f;
        }
        if ("max".equalsIgnoreCase(value)) {
            return 1.85f;
        }
        return 0.85f;
    }

    private int[] getFsrOutputSize() {
        String target = getFsrTarget();
        int targetHeight = "4k".equalsIgnoreCase(target) ? 2160 : 1440;
        float aspect = prefConfig.width > 0 && prefConfig.height > 0
                ? (prefConfig.width / (float) prefConfig.height)
                : (16f / 9f);
        int targetWidth = Math.round(targetHeight * aspect);
        if ("4k".equalsIgnoreCase(target)) {
            targetWidth = Math.max(targetWidth, 3840);
        } else {
            targetWidth = Math.max(targetWidth, 2560);
        }
        return new int[] {targetWidth, targetHeight};
    }

    private String getFsrTarget() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getString("list_fsr_target", "off");
    }

    private String getFsrTargetDisplayName() {
        String target = getFsrTarget();
        if ("4k".equalsIgnoreCase(target)) {
            return "4K";
        }
        if ("2k".equalsIgnoreCase(target)) {
            return "2K";
        }
        return "关闭";
    }

    private String getFsrSharpnessDisplayName() {
        String value = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("list_fsr_sharpness", "standard");
        if ("soft".equalsIgnoreCase(value)) {
            return "柔和";
        }
        if ("strong".equalsIgnoreCase(value)) {
            return "强";
        }
        if ("max".equalsIgnoreCase(value)) {
            return "极强";
        }
        return "标准";
    }

    private boolean isFsrNativeHdrOutputEnabled() {
        String value = PreferenceManager.getDefaultSharedPreferences(this)
                .getString("list_fsr_hdr_output", "native");
        return prefConfig.enableHdr && "native".equalsIgnoreCase(value);
    }

    private boolean isGlesNativeHdrOutputEnabled() {
        return !stereo3dEnabled
                && prefConfig.enableHdr
                && (!fsrEnabled || isFsrNativeHdrOutputEnabled());
    }

    private void configureGlesWindowColorMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !glesRenderingEnabled) {
            return;
        }
        boolean nativeHdrOutput = isGlesNativeHdrOutputEnabled();
        getWindow().setColorMode(nativeHdrOutput
                ? ActivityInfo.COLOR_MODE_HDR
                : ActivityInfo.COLOR_MODE_DEFAULT);
        LimeLog.info("HDR validation: GLES window color mode="
                + (nativeHdrOutput
                ? "HDR (native output)"
                : (fsrEnabled && prefConfig.enableHdr ? "DEFAULT (software tone-map)" : "DEFAULT")));
    }

    private void startConnectionIfReady() {
        if (!glesRenderingEnabled || attemptedConnection || conn == null || !fsrInputSurfaceReady || !fsrDisplaySurfaceCreated) {
            return;
        }

        attemptedConnection = true;
        logSessionInfo("CONNECT", "渲染表面就绪，开始连接；渲染路径="
                + (fsrEnabled ? "FSR/GLES" : "GLES直通")
                + (stereo3dEnabled ? "/SBS3D 3840x1080" : ""));
        UiHelper.notifyStreamConnecting(Game.this);
        decoderRenderer.setRenderTarget(fsrInputSurface);
        audioRenderer = new AndroidAudioRenderer(Game.this, controllerHandler, prefConfig.enableAudioFx,
                prefConfig.enableAudioHaptics, prefConfig.audioHapticsStrength,
                prefConfig.audioHapticsVoiceFilter, prefConfig.audioHapticsOutputTarget);
        streamLoadingController.showPreparing(null);
        conn.start(audioRenderer,
                decoderRenderer, Game.this);
    }

    private void logSessionInfo(String category, String message) {
        if (streamSessionLogger != null) {
            streamSessionLogger.info(category, message);
        }
    }

    private void logSessionWarn(String category, String message) {
        if (streamSessionLogger != null) {
            streamSessionLogger.warn(category, message);
        }
    }

    private void logSessionError(String category, String message, Throwable throwable) {
        if (streamSessionLogger != null) {
            streamSessionLogger.error(category, message, throwable);
        }
    }

    private boolean isRecordAudioPermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    //是否退出串流
    public boolean isQuitSteamingFlag;

    public void quitSteaming(){
        ServerHelper.doQuit(this,streamReqBean, null);
    }

    private AXFloatingView floatingView;
    private void initFloatingView(){
        floatingView = new AXFloatingView(this);
        floatingView.setIconImage(R.drawable.app_icon_axi);
        floatingView.setLayoutParams(AXFloatingView.getLayParams());
        ViewGroup decorViewGroup= (ViewGroup) getWindow().getDecorView();
        decorViewGroup.addView(floatingView);
        floatingView.setFloatingViewListener(new AXFloatingViewListener() {
            @Override
            public void onClick(AXFloatingMagnetView magnetView) {
                switch (prefConfig.axFloatingOperate){
                    case 0://游戏菜单
                        showGameMenu(null);
                        break;
                    case 1://软键盘
                        toggleKeyboard();
                        break;
                    case 2://全键盘
                        showHidekeyBoardLayoutController();
                        break;
                }

            }
        });
//        streamView.setZOrderOnTop(true);
//        streamView.setZOrderMediaOverlay(true);
    }

    public void switchFloatView(){
        if(floatingView==null){
            showFloatView();
            return;
        }
        if (floatingView.getVisibility() == View.VISIBLE) {
            hideFloatView();
        } else {
            showFloatView();
        }
    }

    public void showFloatView(){
        if(floatingView==null){
            initFloatingView();
        }
        floatingView.setVisibility(View.VISIBLE);
    }

    public void hideFloatView(){
        if(floatingView!=null){
            floatingView.setVisibility(View.GONE);
        }
    }

    public void sendClipboardText(){
        if(conn==null){
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            String text=clip.getItemAt(0).coerceToText(this).toString();
            conn.sendUtf8Text(text);
        }
    }

    public void switchPerformanceRumbleHUD(){
        performanceRumble.setVisibility(prefConfig.showRumbleHUD?View.VISIBLE:View.GONE);
    }

    //设置性能信息是否可以点击
    public void switchPerformanceLiteHudclick(){
        performanceOverlayLite.setClickable(prefConfig.enablePerfOverlayLiteDialog);
    }

    public void setPerformanceOverlayZoom(){
        performanceOverlayLite.setTextSize(TypedValue.COMPLEX_UNIT_SP,prefConfig.gameSettingPrefZoom*0.1f);
        addPerformanceOverlayLiteLeftIcon();
    }

    //设置ds5手柄的自适应扳机
    public void setDualSenseTrigger(){
        controllerHandler.setDualSenseTrigger(prefConfig.ds5TriggerMode,
                prefConfig.ds5TriggerStrength,
                prefConfig.ds5TriggerFrequency,prefConfig.ds5TriggerStart,prefConfig.ds5TriggerEnd);
    }

    public void setMotionForceGyro(){
        if(prefConfig.gameForceGyro){
            if(controllerHandler!=null){
                controllerHandler.handleSetMotionEventState((short) 0, MoonBridge.LI_MOTION_TYPE_GYRO, (short) 100);
            }
        }
    }

    public KeyBoardController getKeyBoardController(){
        return keyBoardController;
    }

    public void setPerformanceOverlayLiteMagin(){
        if(prefConfig.performanceOverlayLiteMaginTop==4){
            return;
        }
        LinearLayout.LayoutParams params1= (LinearLayout.LayoutParams) performanceOverlayLite.getLayoutParams();
        params1.setMargins(0,UiHelper.dpToPx(this,prefConfig.performanceOverlayLiteMaginTop),0,0);
        performanceOverlayLite.setLayoutParams(params1);
    }

    public void setAudioHapticsSettings() {
        if (audioRenderer != null) {
            audioRenderer.updateAudioHapticsSettings(prefConfig.enableAudioHaptics,
                    prefConfig.audioHapticsStrength, prefConfig.audioHapticsVoiceFilter,
                    prefConfig.audioHapticsOutputTarget);
        }
        if (controllerHandler != null) {
            controllerHandler.refreshAudioHapticsState();
        }
    }

    //麦克风状态 0 关闭 1开启
    public int micStatus=0;

    //开启关闭 麦克风
    public void switchMic(){
        if (conn == null || micToggleInFlight) {
            return;
        }

        if (micStatus == 1) {
            conn.stopMicUplink();
            micStatus = 0;
            Toast.makeText(this, "麦克风已关闭", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!MicUplinkConnection.isSupported()) {
            Toast.makeText(this, getResources().getString(R.string.mic_uplink_not_supported), Toast.LENGTH_LONG).show();
            return;
        }

        if (!isRecordAudioPermissionGranted()) {
            if (awaitingRecordAudioPermission) {
                return;
            }

            pendingMicToggleAfterPermission = true;
            awaitingRecordAudioPermission = true;
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
            return;
        }

        micToggleInFlight = true;
        final NvConnection currentConn = conn;
        new Thread(() -> {
            boolean started = currentConn.startMicUplink();
            String message = currentConn.getLastMicUplinkMessage();
            runOnUiThread(() -> {
                micToggleInFlight = false;
                if (started) {
                    micStatus = 1;
                } else {
                    micStatus = 0;
                }

                if (message != null && !message.isEmpty()) {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                }
            });
        }, "MicToggle").start();
    }

}
