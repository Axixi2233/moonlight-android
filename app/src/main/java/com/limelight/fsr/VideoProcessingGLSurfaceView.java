package com.limelight.fsr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

@SuppressLint("ViewConstructor")
public class VideoProcessingGLSurfaceView extends GLSurfaceView {
    public static final int EGL_PROTECTED_CONTENT_EXT = 0x32C0;

    private final VideoProcessor videoProcessor;
    private final SurfaceListener surfaceListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final VideoRenderer renderer = new VideoRenderer();

    private int measuredWidthPx;
    private int measuredHeightPx;
    private float surfacePixelScale = 1.0f;
    private boolean fixedSurfaceSizeEnabled;
    private int fixedSurfaceWidth;
    private int fixedSurfaceHeight;
    private double desiredAspectRatio;

    public VideoProcessingGLSurfaceView(Context context,
                                        boolean requireSecureContext,
                                        VideoProcessor videoProcessor,
                                        SurfaceListener surfaceListener) {
        super(context);
        this.videoProcessor = videoProcessor;
        this.surfaceListener = surfaceListener;
        init(requireSecureContext);
    }

    private void init(boolean requireSecureContext) {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setEGLContextFactory(new ContextFactory(requireSecureContext));
        setEGLWindowSurfaceFactory(new WindowSurfaceFactory(requireSecureContext));
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }

    public void setSurfacePixelScale(float scale) {
        float safeScale = Math.max(1.0f, scale);
        if (Math.abs(surfacePixelScale - safeScale) < 0.01f) {
            return;
        }

        surfacePixelScale = safeScale;
        fixedSurfaceSizeEnabled = false;
        applyPreferredSurfaceSize();
    }

    public void setFixedSurfacePixelSize(int width, int height) {
        if (width > 0 && height > 0) {
            fixedSurfaceSizeEnabled = true;
            fixedSurfaceWidth = width;
            fixedSurfaceHeight = height;
        }
        else {
            fixedSurfaceSizeEnabled = false;
        }

        applyPreferredSurfaceSize();
    }

    public void setFrameInputSize(int width, int height) {
        renderer.setFrameSize(width, height);
    }

    public void setDesiredAspectRatio(double aspectRatio) {
        desiredAspectRatio = aspectRatio;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (desiredAspectRatio == 0.0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int measuredWidth;
        int measuredHeight;
        if (widthSize > heightSize * desiredAspectRatio) {
            measuredHeight = heightSize;
            measuredWidth = (int) (measuredHeight * desiredAspectRatio);
        }
        else {
            measuredWidth = widthSize;
            measuredHeight = (int) (measuredWidth / desiredAspectRatio);
        }

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        measuredWidthPx = w;
        measuredHeightPx = h;
        applyPreferredSurfaceSize();
    }

    @Override
    protected void onDetachedFromWindow() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                renderer.release();
                videoProcessor.release();
            }
        });
        super.onDetachedFromWindow();
    }

    private void applyPreferredSurfaceSize() {
        SurfaceHolder holder = getHolder();
        if (holder == null) {
            return;
        }

        if (fixedSurfaceSizeEnabled && fixedSurfaceWidth > 0 && fixedSurfaceHeight > 0) {
            holder.setFixedSize(fixedSurfaceWidth, fixedSurfaceHeight);
            return;
        }

        if (surfacePixelScale > 1.0f && measuredWidthPx > 0 && measuredHeightPx > 0) {
            int scaledWidth = Math.round(measuredWidthPx * surfacePixelScale);
            int scaledHeight = Math.round(measuredHeightPx * surfacePixelScale);
            holder.setFixedSize(scaledWidth, scaledHeight);
        }
        else {
            holder.setSizeFromLayout();
        }
    }

    private final class VideoRenderer implements Renderer {
        private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
        private final AtomicBoolean pendingBufferSizeUpdate = new AtomicBoolean(false);
        private final float[] transformMatrix = new float[16];

        private SurfaceTexture inputSurfaceTexture;
        private int textureId;
        private boolean initialized;
        private long frameTimestampUs;
        private int frameWidth = -1;
        private int frameHeight = -1;
        private int surfaceWidth = -1;
        private int surfaceHeight = -1;

        void setFrameSize(int width, int height) {
            frameWidth = width;
            frameHeight = height;
            pendingBufferSizeUpdate.set(true);
            requestRender();
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            destroyInputSurfaceTexture(true);

            textureId = GlUtil.createExternalTexture();
            inputSurfaceTexture = new SurfaceTexture(textureId);
            pendingBufferSizeUpdate.set(true);
            maybeUpdateInputSurfaceDefaultSize();
            inputSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    frameAvailable.set(true);
                    requestRender();
                }
            });

            notifySurfaceAvailable(inputSurfaceTexture);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            if (!initialized) {
                String version = gl.glGetString(GL10.GL_VERSION);
                String extensions = gl.glGetString(GL10.GL_EXTENSIONS);
                int[] parsedVersion = parseGlVersion(version);
                videoProcessor.initialize(parsedVersion[0], parsedVersion[1],
                        extensions != null ? extensions : "");
                initialized = true;
            }

            if (surfaceWidth > 0 && surfaceHeight > 0) {
                videoProcessor.setSurfaceSize(surfaceWidth, surfaceHeight);
                surfaceWidth = -1;
                surfaceHeight = -1;
            }

            maybeUpdateInputSurfaceDefaultSize();

            if (frameAvailable.compareAndSet(true, false)) {
                SurfaceTexture surfaceTexture = inputSurfaceTexture;
                if (surfaceTexture != null) {
                    surfaceTexture.updateTexImage();
                    frameTimestampUs = surfaceTexture.getTimestamp() / 1000L;
                    surfaceTexture.getTransformMatrix(transformMatrix);
                }
            }

            videoProcessor.draw(textureId, frameTimestampUs, frameWidth, frameHeight, transformMatrix);
        }

        void release() {
            destroyInputSurfaceTexture(true);
        }

        private void maybeUpdateInputSurfaceDefaultSize() {
            if (!pendingBufferSizeUpdate.get()) {
                return;
            }

            SurfaceTexture surfaceTexture = inputSurfaceTexture;
            if (surfaceTexture == null || frameWidth <= 0 || frameHeight <= 0) {
                return;
            }

            surfaceTexture.setDefaultBufferSize(frameWidth, frameHeight);
            pendingBufferSizeUpdate.set(false);
        }

        private void notifySurfaceAvailable(final SurfaceTexture surfaceTexture) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    surfaceListener.onInputSurfaceAvailable(surfaceTexture);
                }
            });
        }

        private void destroyInputSurfaceTexture(boolean notifyListener) {
            SurfaceTexture oldSurfaceTexture = inputSurfaceTexture;
            if (oldSurfaceTexture != null) {
                if (notifyListener) {
                    notifySurfaceDestroyedBlocking();
                }
                oldSurfaceTexture.release();
                inputSurfaceTexture = null;
            }

            if (textureId != 0) {
                GLES20.glDeleteTextures(1, new int[] {textureId}, 0);
                textureId = 0;
            }

            frameAvailable.set(false);
            initialized = false;
            frameTimestampUs = 0;
            for (int i = 0; i < transformMatrix.length; i++) {
                transformMatrix[i] = 0.0f;
            }
        }

        private void notifySurfaceDestroyedBlocking() {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                surfaceListener.onInputSurfaceDestroyed();
                return;
            }

            final CountDownLatch latch = new CountDownLatch(1);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    surfaceListener.onInputSurfaceDestroyed();
                    latch.countDown();
                }
            });

            try {
                latch.await(300, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private int[] parseGlVersion(String version) {
            if (version == null) {
                return new int[] {2, 0};
            }

            String cleaned = version.replace("OpenGL ES ", "");
            String[] parts = cleaned.split("[ .]");
            return new int[] {
                    parseInt(parts, 0, 2),
                    parseInt(parts, 1, 0)
            };
        }

        private int parseInt(String[] parts, int index, int fallback) {
            if (index >= parts.length) {
                return fallback;
            }

            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    private static final class ContextFactory implements EGLContextFactory {
        private final boolean requireSecure;

        private ContextFactory(boolean requireSecure) {
            this.requireSecure = requireSecure;
        }

        @Override
        public EGLContext createContext(EGL10 egl, EGLDisplay display, EGLConfig eglConfig) {
            int[] attrs = new int[] {
                    EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL14.EGL_NONE
            };

            if (requireSecure) {
                attrs = new int[] {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                        EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE,
                        EGL14.EGL_NONE
                };
            }

            EGLContext context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, attrs);
            if (context == null || context == EGL10.EGL_NO_CONTEXT || EGL14.eglGetError() != EGL14.EGL_SUCCESS) {
                if (context != null && context != EGL10.EGL_NO_CONTEXT) {
                    egl.eglDestroyContext(display, context);
                }

                int[] fallbackAttrs = new int[] {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };
                context = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, fallbackAttrs);
            }

            return context;
        }

        @Override
        public void destroyContext(EGL10 egl, EGLDisplay display, EGLContext context) {
            egl.eglDestroyContext(display, context);
        }
    }

    private static final class WindowSurfaceFactory implements EGLWindowSurfaceFactory {
        private final boolean requireSecure;

        private WindowSurfaceFactory(boolean requireSecure) {
            this.requireSecure = requireSecure;
        }

        @Override
        public EGLSurface createWindowSurface(EGL10 egl, EGLDisplay display, EGLConfig config,
                                              Object nativeWindow) {
            int[] attribList = requireSecure
                    ? new int[] {EGL_PROTECTED_CONTENT_EXT, EGL14.EGL_TRUE, EGL10.EGL_NONE}
                    : new int[] {EGL10.EGL_NONE};
            return egl.eglCreateWindowSurface(display, config, nativeWindow, attribList);
        }

        @Override
        public void destroySurface(EGL10 egl, EGLDisplay display, EGLSurface surface) {
            egl.eglDestroySurface(display, surface);
        }
    }

    public interface VideoProcessor {
        void initialize(int glMajorVersion, int glMinorVersion, String extensions);

        void setSurfaceSize(int width, int height);

        void draw(int frameTexture,
                  long frameTimestampUs,
                  int frameWidth,
                  int frameHeight,
                  float[] transformMatrix);

        void release();
    }

    public interface SurfaceListener {
        void onInputSurfaceAvailable(SurfaceTexture surfaceTexture);

        void onInputSurfaceDestroyed();
    }
}
