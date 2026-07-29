package com.limelight.fsr;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import com.limelight.stereo3d.NoOpStereo3dBackend;
import com.limelight.stereo3d.Stereo3dBackend;

import java.io.IOException;
import java.nio.FloatBuffer;

public final class FsrVideoProcessor implements VideoProcessingGLSurfaceView.VideoProcessor {
    private static final String TAG = "FsrVideoProcessor";
    private static final boolean ENABLE_TWO_PASS_PIPELINE = true;
    private static final boolean ENABLE_TWO_PASS_FOR_31 = false;
    private static final boolean FORCE_SOFTWARE_HDR_TONE_MAP = true;

    private static final int EGL_EXTENSIONS = 0x3055;
    private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
    private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;

    private static final int PIPELINE_NONE = 0;
    private static final int PIPELINE_TWO_PASS = 1;
    private static final int PIPELINE_MOBILE_SINGLE_PASS = 2;

    private static final String SHADER_DIR_20 = "fsr/2.0/";
    private static final String SHADER_DIR_30 = "fsr/3.0/";
    private static final String SHADER_DIR_31 = "fsr/3.1/";
    private static final float[] IDENTITY_TEXTURE_TRANSFORM = new float[] {
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
    };

    private final Context context;
    private final Stereo3dBackend optionalStereo3dBackend;
    private final FloatBuffer fullscreenVertices = GlUtil.getFullscreenVertices();
    private final FloatBuffer fullscreenTexCoords = GlUtil.getFullscreenTexCoords();
    private final int[] framebuffers = new int[1];
    private final int[] textures = new int[1];
    private final int[] stereoSceneFramebuffers = new int[1];
    private final int[] stereoSceneTextures = new int[1];

    private GlProgram easuProgram;
    private GlProgram rcasProgram;
    private GlProgram mobileProgram;
    private GlProgram passthroughProgram;
    private GlProgram stereoExternalProgram;
    private GlProgram stereoTextureProgram;

    private int pipelineMode = PIPELINE_NONE;
    private String activeShaderDir = "none";
    private boolean needInputSize = true;
    private boolean mobileHasSharpness;
    private boolean mobileHasHdrToneMap;
    private boolean twoPassFailureLogged;
    private boolean fsrEnabled;
    private boolean stereo3dEnabled;
    private boolean stereo3dSwapEyes;
    private boolean optionalStereo3dReady;
    private volatile boolean optionalStereo3dRendering;
    private boolean optionalStereo3dFailureLogged;
    private boolean hdrToneMappingEnabled;
    private boolean usingPqWindow;

    private float rcasSharpness = 0.1f;
    private float mobileSharpness = 1.5f;
    private float stereo3dDepthStrength = 0.018f;
    private float stereo3dConvergence = 0.50f;
    private int surfaceWidth = -1;
    private int surfaceHeight = -1;
    private int outputWidth = -1;
    private int outputHeight = -1;
    private int stereoSceneWidth = 1920;
    private int stereoSceneHeight = 1080;
    private float[] outputSize = new float[] {1f, 1f};

    public FsrVideoProcessor(Context context) {
        this.context = context.getApplicationContext();
        optionalStereo3dBackend = OptionalStereo3dLoader.load(this.context);
    }

    @Override
    public void initialize(int glMajorVersion, int glMinorVersion, String extensions) {
        releaseOptionalStereo3d();
        resetPrograms();
        deleteFramebuffer();
        deleteStereoSceneFramebuffer();
        detectHdrWindowState();

        if (stereo3dEnabled) {
            initializeOptionalStereo3d(glMajorVersion, glMinorVersion, extensions);
            try {
                ensureStereoExternalProgram();
                Log.i(TAG, "SBS renderer ready: logicalOutput=3840x1080"
                        + ", scene=" + stereoSceneWidth + "x" + stereoSceneHeight);
            } catch (IOException | GlUtil.GlException e) {
                Log.e(TAG, "Failed to initialize SBS OES renderer", e);
            }
        }

        if (!fsrEnabled) {
            Log.i(TAG, "FSR disabled; using GLES passthrough pipeline");
            return;
        }

        boolean supportsExternalOesEssl3 = extensions != null
                && extensions.contains("GL_OES_EGL_image_external_essl3");
        String preferredDir = SHADER_DIR_20;
        boolean preferredNeedInputSize = true;
        if (supportsExternalOesEssl3) {
            if (glMajorVersion > 3 || (glMajorVersion == 3 && glMinorVersion >= 1)) {
                preferredDir = SHADER_DIR_31;
                preferredNeedInputSize = false;
            } else if (glMajorVersion == 3) {
                preferredDir = SHADER_DIR_30;
                preferredNeedInputSize = false;
            }
        } else if (glMajorVersion >= 3) {
            Log.w(TAG, "GLES3 context without GL_OES_EGL_image_external_essl3, forcing FSR 2.0 shaders");
        }

        Log.i(TAG, "FSR preferred shader dir=" + preferredDir
                + ", GLES=" + glMajorVersion + "." + glMinorVersion);
        boolean tryTwoPassPreferred = ENABLE_TWO_PASS_PIPELINE
                && (!SHADER_DIR_31.equals(preferredDir) || ENABLE_TWO_PASS_FOR_31);
        if (SHADER_DIR_31.equals(preferredDir) && !ENABLE_TWO_PASS_FOR_31) {
            Log.w(TAG, "Skip FSR 3.1 two-pass for driver stability; use 3.1 mobile path first");
        }
        if (tryTwoPassPreferred
                && (tryInitTwoPass(preferredDir, preferredNeedInputSize)
                || (!SHADER_DIR_20.equals(preferredDir) && tryInitTwoPass(SHADER_DIR_20, true)))) {
            return;
        }

        boolean preferredHasSharpness = SHADER_DIR_20.equals(preferredDir);
        if (tryInitMobileSinglePass(preferredDir, preferredNeedInputSize, true, preferredHasSharpness)
                || (!SHADER_DIR_20.equals(preferredDir)
                && tryInitMobileSinglePass(SHADER_DIR_20, true, true, true))) {
            return;
        }

        pipelineMode = PIPELINE_NONE;
        activeShaderDir = "none";
        Log.e(TAG, "All FSR pipelines failed; passthrough only");
    }

    @Override
    public void setSurfaceSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (surfaceWidth == width && surfaceHeight == height) {
            return;
        }
        surfaceWidth = width;
        surfaceHeight = height;
        if (optionalStereo3dReady) {
            try {
                optionalStereo3dBackend.setSurfaceSize(width, height);
            } catch (RuntimeException | LinkageError error) {
                disableOptionalStereo3d("surface-size", error);
            }
        }
        if (stereo3dEnabled) {
            Log.i(TAG, "SBS display surface=" + width + "x" + height
                    + ", logicalOutput=3840x1080"
                    + ", eyeViewport=" + (width / 2) + "x" + height);
        }
        updateOutputSize();
    }

    private void updateOutputSize() {
        int desiredOutputWidth = stereo3dEnabled ? stereoSceneWidth : surfaceWidth;
        int desiredOutputHeight = stereo3dEnabled ? stereoSceneHeight : surfaceHeight;
        if (desiredOutputWidth <= 0 || desiredOutputHeight <= 0
                || (outputWidth == desiredOutputWidth && outputHeight == desiredOutputHeight)) {
            return;
        }
        outputWidth = desiredOutputWidth;
        outputHeight = desiredOutputHeight;
        outputSize = new float[] {outputWidth, outputHeight};
        deleteFramebuffer();
        deleteStereoSceneFramebuffer();
        if (pipelineMode == PIPELINE_TWO_PASS) {
            createFramebuffer();
        }
    }

    @Override
    public void draw(int frameTexture,
                     long frameTimestampUs,
                     int frameWidth,
                     int frameHeight,
                     float[] transformMatrix) {
        int viewportWidth = Math.max(surfaceWidth, 1);
        int viewportHeight = Math.max(surfaceHeight, 1);
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (stereo3dEnabled) {
            drawStereoFrame(frameTexture, frameTimestampUs, frameWidth, frameHeight,
                    transformMatrix);
            return;
        }
        optionalStereo3dRendering = false;
        if (!fsrEnabled) {
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }
        if (pipelineMode == PIPELINE_TWO_PASS) {
            drawTwoPass(frameTexture, frameWidth, frameHeight, transformMatrix);
            return;
        }
        if (pipelineMode == PIPELINE_MOBILE_SINGLE_PASS) {
            drawMobileSinglePass(frameTexture, frameWidth, frameHeight, transformMatrix);
            return;
        }
        drawPassthrough(frameTexture, transformMatrix);
    }

    @Override
    public void release() {
        releaseOptionalStereo3d();
        resetPrograms();
        deleteFramebuffer();
        deleteStereoSceneFramebuffer();
    }

    public void setFsrEnabled(boolean enabled) {
        fsrEnabled = enabled;
    }

    public void setStereo3dEnabled(boolean enabled) {
        if (stereo3dEnabled == enabled) {
            return;
        }
        stereo3dEnabled = enabled;
        if (!enabled) {
            optionalStereo3dRendering = false;
        }
        updateOutputSize();
    }

    public boolean isOptionalStereo3dRendering() {
        return optionalStereo3dRendering;
    }

    public void setStereo3dDepthStrength(float strength) {
        stereo3dDepthStrength = Math.max(0.0f, Math.min(0.03f, strength));
    }

    public void setStereo3dConvergence(float convergence) {
        stereo3dConvergence = Math.max(0.25f, Math.min(0.75f, convergence));
    }

    public void setStereo3dSwapEyes(boolean swapEyes) {
        stereo3dSwapEyes = swapEyes;
    }

    public void setStereoSceneSize(int width, int height) {
        if (width <= 0 || height <= 0
                || (stereoSceneWidth == width && stereoSceneHeight == height)) {
            return;
        }
        stereoSceneWidth = width;
        stereoSceneHeight = height;
        updateOutputSize();
    }

    public void setHdrToneMappingEnabled(boolean enabled) {
        hdrToneMappingEnabled = enabled;
        Log.i(TAG, "HDR tone mapping requested=" + enabled);
    }

    public void setSharpness(float sharpness) {
        float clamped = Math.max(0.0f, Math.min(2.0f, sharpness));
        mobileSharpness = clamped;
        rcasSharpness = 2.0f - clamped;
        Log.i(TAG, "Sharpness=" + clamped + ", mobile=" + mobileSharpness + ", rcas=" + rcasSharpness);
    }

    public void setHdrWhiteScale(float hdrWhiteScale) {
        // Kept for callers from older builds. RemoteLightAndroid uses a fixed conservative HDR curve.
    }

    public void setHdrShadowLiftScale(float hdrShadowLiftScale) {
        // Kept for callers from older builds. RemoteLightAndroid uses a fixed conservative HDR curve.
    }

    private boolean tryInitTwoPass(String shaderDir, boolean requireInputSize) {
        GlProgram easu = null;
        GlProgram rcas = null;
        try {
            easu = buildProgram(shaderDir + "fsr_easu_vertex.glsl", shaderDir + "fsr_easu_fragment.glsl");
            rcas = buildProgram(shaderDir + "fsr_rcas_vertex.glsl", shaderDir + "fsr_rcas_fragment.glsl");
            easuProgram = easu;
            rcasProgram = rcas;
            needInputSize = requireInputSize;
            pipelineMode = PIPELINE_TWO_PASS;
            activeShaderDir = shaderDir;
            deleteFramebuffer();
            if (outputWidth > 0 && outputHeight > 0) {
                createFramebuffer();
            }
            Log.i(TAG, "FSR pipeline active: two-pass, shaderDir=" + shaderDir);
            return true;
        } catch (IOException | GlUtil.GlException e) {
            Log.e(TAG, "Failed to initialize two-pass FSR from " + shaderDir, e);
            safeDeleteProgram(easu);
            safeDeleteProgram(rcas);
            return false;
        }
    }

    private boolean tryInitMobileSinglePass(String shaderDir,
                                            boolean requireInputSize,
                                            boolean hasHdrToneMapUniform,
                                            boolean hasSharpnessUniform) {
        GlProgram program = null;
        try {
            program = buildProgram(shaderDir + "opt_fsr_vertex.glsl", shaderDir + "opt_fsr_fragment.glsl");
            mobileProgram = program;
            needInputSize = requireInputSize;
            mobileHasHdrToneMap = hasHdrToneMapUniform;
            mobileHasSharpness = hasSharpnessUniform;
            pipelineMode = PIPELINE_MOBILE_SINGLE_PASS;
            activeShaderDir = shaderDir;
            deleteFramebuffer();
            Log.i(TAG, "FSR pipeline active: mobile single-pass, shaderDir=" + shaderDir);
            return true;
        } catch (IOException | GlUtil.GlException e) {
            Log.e(TAG, "Failed to initialize mobile FSR from " + shaderDir, e);
            safeDeleteProgram(program);
            return false;
        }
    }

    private void drawStereoFrame(int frameTexture,
                                 long frameTimestampUs,
                                 int frameWidth,
                                 int frameHeight,
                                 float[] transformMatrix) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, Math.max(surfaceWidth, 1), Math.max(surfaceHeight, 1));

        submitOptionalDepthSource(frameTexture, frameTimestampUs, frameWidth, frameHeight,
                transformMatrix);

        if (!fsrEnabled || pipelineMode == PIPELINE_NONE) {
            if (tryDrawOptionalStereo(frameTexture, GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    frameWidth, frameHeight, transformMatrix)) {
                return;
            }
            drawStereoExternal(frameTexture, frameWidth, frameHeight, transformMatrix);
            return;
        }

        ensureStereoSceneFramebuffer();
        if (stereoSceneFramebuffers[0] == 0 || stereoSceneTextures[0] == 0) {
            Log.w(TAG, "SBS scene framebuffer unavailable; bypassing FSR for this frame");
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            drawStereoExternal(frameTexture, frameWidth, frameHeight, transformMatrix);
            return;
        }

        if (pipelineMode == PIPELINE_TWO_PASS) {
            drawTwoPass(frameTexture, frameWidth, frameHeight, transformMatrix);
            if (pipelineMode != PIPELINE_TWO_PASS) {
                // The failure path rendered the frame again using the newly selected fallback.
                return;
            }
        }
        else if (pipelineMode == PIPELINE_MOBILE_SINGLE_PASS) {
            drawMobileSinglePass(frameTexture, frameWidth, frameHeight, transformMatrix);
        }
        else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            drawStereoExternal(frameTexture, frameWidth, frameHeight, transformMatrix);
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, Math.max(surfaceWidth, 1), Math.max(surfaceHeight, 1));
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (tryDrawOptionalStereo(stereoSceneTextures[0], GLES20.GL_TEXTURE_2D,
                outputWidth, outputHeight, IDENTITY_TEXTURE_TRANSFORM)) {
            return;
        }
        drawStereoTexture(stereoSceneTextures[0], outputWidth, outputHeight);
    }

    private void initializeOptionalStereo3d(int glMajorVersion, int glMinorVersion,
                                            String extensions) {
        optionalStereo3dFailureLogged = false;
        if (optionalStereo3dBackend == NoOpStereo3dBackend.INSTANCE) {
            optionalStereo3dReady = false;
            return;
        }

        try {
            optionalStereo3dReady = optionalStereo3dBackend.initialize(
                    glMajorVersion, glMinorVersion, extensions);
            if (optionalStereo3dReady) {
                if (surfaceWidth > 0 && surfaceHeight > 0) {
                    optionalStereo3dBackend.setSurfaceSize(surfaceWidth, surfaceHeight);
                }
                Log.i(TAG, "Optional AI stereo renderer ready: "
                        + optionalStereo3dBackend.getDisplayName());
            }
            else {
                Log.i(TAG, "Optional AI stereo renderer unavailable; using built-in 3D renderer");
            }
        } catch (RuntimeException | LinkageError error) {
            disableOptionalStereo3d("initialize", error);
        }
    }

    private void submitOptionalDepthSource(int frameTexture,
                                           long frameTimestampUs,
                                           int frameWidth,
                                           int frameHeight,
                                           float[] transformMatrix) {
        if (!optionalStereo3dReady) {
            return;
        }

        try {
            optionalStereo3dBackend.submitDepthSource(
                    frameTexture,
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    frameTimestampUs,
                    frameWidth,
                    frameHeight,
                    transformMatrix);
        } catch (RuntimeException | LinkageError error) {
            disableOptionalStereo3d("depth-source", error);
        }
    }

    private boolean tryDrawOptionalStereo(int textureId,
                                          int textureTarget,
                                          int sourceWidth,
                                          int sourceHeight,
                                          float[] textureTransform) {
        if (!optionalStereo3dReady) {
            optionalStereo3dRendering = false;
            return false;
        }

        try {
            boolean rendered = optionalStereo3dBackend.renderStereo(
                    textureId,
                    textureTarget,
                    sourceWidth,
                    sourceHeight,
                    textureTransform,
                    Math.max(surfaceWidth, 1),
                    Math.max(surfaceHeight, 1),
                    stereo3dDepthStrength,
                    stereo3dConvergence,
                    stereo3dSwapEyes);
            optionalStereo3dRendering = rendered;
            if (!rendered) {
                restoreStereoOutputTarget();
            }
            return rendered;
        } catch (RuntimeException | LinkageError error) {
            disableOptionalStereo3d("render", error);
            restoreStereoOutputTarget();
            return false;
        }
    }

    private void restoreStereoOutputTarget() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, Math.max(surfaceWidth, 1), Math.max(surfaceHeight, 1));
    }

    private void disableOptionalStereo3d(String stage, Throwable error) {
        optionalStereo3dReady = false;
        optionalStereo3dRendering = false;
        if (!optionalStereo3dFailureLogged) {
            optionalStereo3dFailureLogged = true;
            Log.e(TAG, "Optional AI stereo failed during " + stage
                    + "; using built-in 3D renderer", error);
        }
        try {
            optionalStereo3dBackend.release();
        } catch (RuntimeException | LinkageError releaseError) {
            Log.w(TAG, "Optional AI stereo release failed", releaseError);
        }
    }

    private void releaseOptionalStereo3d() {
        optionalStereo3dReady = false;
        optionalStereo3dRendering = false;
        if (optionalStereo3dBackend == NoOpStereo3dBackend.INSTANCE) {
            return;
        }
        try {
            optionalStereo3dBackend.release();
        } catch (RuntimeException | LinkageError error) {
            Log.w(TAG, "Optional AI stereo release failed", error);
        }
    }

    private void drawTwoPass(int frameTexture,
                             int frameWidth,
                             int frameHeight,
                             float[] transformMatrix) {
        if (easuProgram == null || rcasProgram == null || outputWidth <= 0 || outputHeight <= 0) {
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }
        if (framebuffers[0] == 0 || textures[0] == 0) {
            createFramebuffer();
            if (framebuffers[0] == 0 || textures[0] == 0) {
                drawPassthrough(frameTexture, transformMatrix);
                return;
            }
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[0]);
        GLES20.glViewport(0, 0, outputWidth, outputHeight);
        try {
            bindExternalInput(easuProgram, frameTexture, frameWidth, frameHeight, transformMatrix, true);
            easuProgram.setFloatsUniform("outputTextureSize", outputSize);
            easuProgram.bindAttributesAndUniforms();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind EASU shader (" + activeShaderDir + ")", e);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            fallbackToMobileSinglePass("bind-easu");
            draw(frameTexture, 0L, frameWidth, frameHeight, transformMatrix);
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (!checkGlError("EASU draw")) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            fallbackToMobileSinglePass("draw-easu");
            draw(frameTexture, 0L, frameWidth, frameHeight, transformMatrix);
            return;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,
                stereo3dEnabled ? stereoSceneFramebuffers[0] : 0);
        GLES20.glViewport(0, 0, Math.max(outputWidth, 1), Math.max(outputHeight, 1));
        try {
            rcasProgram.setSamplerTexIdUniform("inputTexture", textures[0], 0, GLES20.GL_TEXTURE_2D);
            if (needInputSize) {
                rcasProgram.setFloatsUniform("inputTextureSize", outputSize);
            }
            rcasProgram.setFloatUniform("sharpness", rcasSharpness);
            rcasProgram.bindAttributesAndUniforms();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind RCAS shader (" + activeShaderDir + ")", e);
            fallbackToMobileSinglePass("bind-rcas");
            draw(frameTexture, 0L, frameWidth, frameHeight, transformMatrix);
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        if (!checkGlError("RCAS draw")) {
            fallbackToMobileSinglePass("draw-rcas");
            draw(frameTexture, 0L, frameWidth, frameHeight, transformMatrix);
        }
    }

    private void drawMobileSinglePass(int frameTexture,
                                      int frameWidth,
                                      int frameHeight,
                                      float[] transformMatrix) {
        if (mobileProgram == null || outputWidth <= 0 || outputHeight <= 0) {
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,
                stereo3dEnabled ? stereoSceneFramebuffers[0] : 0);
        GLES20.glViewport(0, 0, Math.max(outputWidth, 1), Math.max(outputHeight, 1));
        try {
            bindExternalInput(mobileProgram, frameTexture, frameWidth, frameHeight, transformMatrix, mobileHasHdrToneMap);
            mobileProgram.setFloatsUniform("outputTextureSize", outputSize);
            if (mobileHasSharpness) {
                mobileProgram.setFloatUniform("sharpness", mobileSharpness);
            }
            mobileProgram.bindAttributesAndUniforms();
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind mobile FSR shader (" + activeShaderDir + ")", e);
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("mobile FSR draw");
    }

    private void drawPassthrough(int frameTexture, float[] transformMatrix) {
        try {
            GlProgram program = ensurePassthroughProgram();
            program.setSamplerTexIdUniform("inputTexture", frameTexture, 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            program.setMatrix4Uniform("uTexTransform", transformMatrix);
            program.setFloatUniform("uHdrToneMap", shouldApplySoftwareHdrToneMap() ? 1.0f : 0.0f);
            program.bindAttributesAndUniforms();
        } catch (IOException | GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind passthrough shader", e);
            return;
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("passthrough draw");
    }

    private void drawStereoExternal(int frameTexture,
                                    int frameWidth,
                                    int frameHeight,
                                    float[] transformMatrix) {
        optionalStereo3dRendering = false;
        try {
            GlProgram program = ensureStereoExternalProgram();
            program.setSamplerTexIdUniform("inputTexture", frameTexture, 0,
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            program.setMatrix4Uniform("uTexTransform", transformMatrix);
            bindStereoUniforms(program, frameWidth, frameHeight);
            program.bindAttributesAndUniforms();
        } catch (IOException | GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind SBS OES shader; using GLES passthrough", e);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, Math.max(surfaceWidth, 1), Math.max(surfaceHeight, 1));
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("SBS OES draw");
    }

    private void drawStereoTexture(int textureId, int textureWidth, int textureHeight) {
        optionalStereo3dRendering = false;
        try {
            GlProgram program = ensureStereoTextureProgram();
            program.setSamplerTexIdUniform("inputTexture", textureId, 0, GLES20.GL_TEXTURE_2D);
            bindStereoUniforms(program, textureWidth, textureHeight);
            program.bindAttributesAndUniforms();
        } catch (IOException | GlUtil.GlException e) {
            Log.e(TAG, "Failed to bind SBS 2D texture shader", e);
            return;
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("SBS texture draw");
    }

    private void bindStereoUniforms(GlProgram program, int sourceWidth, int sourceHeight) {
        float safeWidth = Math.max(sourceWidth, 1);
        float safeHeight = Math.max(sourceHeight, 1);
        float eyeAspect = surfaceWidth > 0 && surfaceHeight > 0
                ? (surfaceWidth * 0.5f) / surfaceHeight
                : (16.0f / 9.0f);
        program.setFloatUniform("uSourceAspect", safeWidth / safeHeight);
        program.setFloatUniform("uEyeAspect", eyeAspect);
        program.setFloatUniform("uDepthStrength", stereo3dDepthStrength);
        program.setFloatUniform("uConvergence", stereo3dConvergence);
        program.setFloatUniform("uSwapEyes", stereo3dSwapEyes ? 1.0f : 0.0f);
    }

    private void bindExternalInput(GlProgram program,
                                   int frameTexture,
                                   int frameWidth,
                                   int frameHeight,
                                   float[] transformMatrix,
                                   boolean applyHdrToneMapUniform) {
        program.setSamplerTexIdUniform("inputTexture", frameTexture, 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        program.setMatrix4Uniform("uTexTransform", transformMatrix);
        if (needInputSize) {
            program.setFloatsUniform("inputTextureSize", new float[] {
                    Math.max(frameWidth, 1),
                    Math.max(frameHeight, 1)
            });
        }
        if (applyHdrToneMapUniform) {
            program.setFloatUniform("uHdrToneMap", shouldApplySoftwareHdrToneMap() ? 1.0f : 0.0f);
        }
    }

    private GlProgram ensurePassthroughProgram() throws IOException {
        if (passthroughProgram == null) {
            passthroughProgram = buildProgram(
                    SHADER_DIR_20 + "opt_fsr_vertex.glsl",
                    SHADER_DIR_20 + "passthrough_fragment.glsl"
            );
        }
        return passthroughProgram;
    }

    private GlProgram ensureStereoExternalProgram() throws IOException {
        if (stereoExternalProgram == null) {
            stereoExternalProgram = buildProgram(
                    "stereo/stereo_vertex.glsl",
                    "stereo/stereo_oes_fragment.glsl"
            );
        }
        return stereoExternalProgram;
    }

    private GlProgram ensureStereoTextureProgram() throws IOException {
        if (stereoTextureProgram == null) {
            stereoTextureProgram = buildProgram(
                    "stereo/stereo_vertex.glsl",
                    "stereo/stereo_2d_fragment.glsl"
            );
        }
        return stereoTextureProgram;
    }

    private GlProgram buildProgram(String vertexShaderAsset, String fragmentShaderAsset) throws IOException {
        GlProgram program = new GlProgram(context, vertexShaderAsset, fragmentShaderAsset);
        program.setBufferAttribute("aPosition", fullscreenVertices, 2);
        program.setBufferAttribute("aTexCoords", fullscreenTexCoords, 2);
        return program;
    }

    private void createFramebuffer() {
        if (outputWidth <= 0 || outputHeight <= 0) {
            return;
        }
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[0]);

        GLES20.glGenTextures(1, textures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                outputWidth,
                outputHeight,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                textures[0],
                0
        );

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Framebuffer incomplete: " + status);
            deleteFramebuffer();
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void ensureStereoSceneFramebuffer() {
        if (!stereo3dEnabled || outputWidth <= 0 || outputHeight <= 0
                || (stereoSceneFramebuffers[0] != 0 && stereoSceneTextures[0] != 0)) {
            return;
        }

        GLES20.glGenFramebuffers(1, stereoSceneFramebuffers, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, stereoSceneFramebuffers[0]);

        GLES20.glGenTextures(1, stereoSceneTextures, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, stereoSceneTextures[0]);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D,
                0,
                GLES20.GL_RGBA,
                outputWidth,
                outputHeight,
                0,
                GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE,
                null
        );
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER,
                GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D,
                stereoSceneTextures[0],
                0
        );

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "SBS scene framebuffer incomplete: " + status);
            deleteStereoSceneFramebuffer();
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void deleteFramebuffer() {
        if (framebuffers[0] != 0) {
            GLES20.glDeleteFramebuffers(1, framebuffers, 0);
            framebuffers[0] = 0;
        }
        if (textures[0] != 0) {
            GLES20.glDeleteTextures(1, textures, 0);
            textures[0] = 0;
        }
    }

    private void deleteStereoSceneFramebuffer() {
        if (stereoSceneFramebuffers[0] != 0) {
            GLES20.glDeleteFramebuffers(1, stereoSceneFramebuffers, 0);
            stereoSceneFramebuffers[0] = 0;
        }
        if (stereoSceneTextures[0] != 0) {
            GLES20.glDeleteTextures(1, stereoSceneTextures, 0);
            stereoSceneTextures[0] = 0;
        }
    }

    private void fallbackToMobileSinglePass(String reason) {
        if (pipelineMode != PIPELINE_TWO_PASS) {
            return;
        }
        String failedDir = activeShaderDir;
        boolean failedNeedInputSize = needInputSize;
        if (!twoPassFailureLogged) {
            Log.w(TAG, "Switch FSR two-pass to mobile single-pass, reason=" + reason
                    + ", shaderDir=" + failedDir);
            twoPassFailureLogged = true;
        }
        safeDeleteProgram(easuProgram);
        easuProgram = null;
        safeDeleteProgram(rcasProgram);
        rcasProgram = null;
        deleteFramebuffer();
        pipelineMode = PIPELINE_NONE;
        activeShaderDir = "none";
        if (tryInitMobileSinglePass(failedDir, failedNeedInputSize, true, SHADER_DIR_20.equals(failedDir))) {
            return;
        }
        if (!SHADER_DIR_20.equals(failedDir)) {
            tryInitMobileSinglePass(SHADER_DIR_20, true, true, true);
        }
    }

    private void resetPrograms() {
        safeDeleteProgram(easuProgram);
        easuProgram = null;
        safeDeleteProgram(rcasProgram);
        rcasProgram = null;
        safeDeleteProgram(mobileProgram);
        mobileProgram = null;
        safeDeleteProgram(passthroughProgram);
        passthroughProgram = null;
        safeDeleteProgram(stereoExternalProgram);
        stereoExternalProgram = null;
        safeDeleteProgram(stereoTextureProgram);
        stereoTextureProgram = null;
        pipelineMode = PIPELINE_NONE;
        activeShaderDir = "none";
        needInputSize = true;
        mobileHasSharpness = false;
        mobileHasHdrToneMap = false;
        twoPassFailureLogged = false;
    }

    private boolean checkGlError(String label) {
        try {
            GlUtil.checkGlError(label);
            return true;
        } catch (GlUtil.GlException e) {
            Log.e(TAG, "GL error at " + label, e);
            return false;
        }
    }

    private boolean shouldApplySoftwareHdrToneMap() {
        return hdrToneMappingEnabled && !usingPqWindow && FORCE_SOFTWARE_HDR_TONE_MAP;
    }

    private void detectHdrWindowState() {
        usingPqWindow = false;
        android.opengl.EGLDisplay display = EGL14.eglGetCurrentDisplay();
        android.opengl.EGLSurface drawSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        if (display == null || display == EGL14.EGL_NO_DISPLAY
                || drawSurface == null || drawSurface == EGL14.EGL_NO_SURFACE) {
            return;
        }
        String eglExtensions = EGL14.eglQueryString(display, EGL_EXTENSIONS);
        boolean supportsPqColorspace = eglExtensions != null
                && eglExtensions.contains("EGL_KHR_gl_colorspace")
                && eglExtensions.contains("EGL_EXT_gl_colorspace_bt2020_pq");
        if (!supportsPqColorspace) {
            return;
        }
        int[] colorspace = new int[1];
        if (EGL14.eglQuerySurface(display, drawSurface, EGL_GL_COLORSPACE_KHR, colorspace, 0)) {
            usingPqWindow = colorspace[0] == EGL_GL_COLORSPACE_BT2020_PQ_EXT;
        }
        Log.i(TAG, "HDR surface state: usingPqWindow=" + usingPqWindow);
    }

    private void safeDeleteProgram(GlProgram program) {
        if (program == null) {
            return;
        }
        try {
            program.delete();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to delete GL program", e);
        }
    }
}
