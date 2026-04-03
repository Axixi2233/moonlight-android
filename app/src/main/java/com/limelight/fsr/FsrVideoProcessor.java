package com.limelight.fsr;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.IOException;
import java.nio.FloatBuffer;

public class FsrVideoProcessor implements VideoProcessingGLSurfaceView.VideoProcessor {
    private static final String TAG = "FsrVideoProcessor";
    private static final String FSR_VERTEX_SHADER = "fsr/2.0/opt_fsr_vertex.glsl";
    private static final String FSR_FRAGMENT_SHADER = "fsr/2.0/opt_fsr_fragment.glsl";
    private static final String FSR_EASU_VERTEX_SHADER = "fsr/2.0/fsr_easu_vertex.glsl";
    private static final String FSR_EASU_FRAGMENT_SHADER = "fsr/2.0/fsr_easu_fragment.glsl";
    private static final String FSR_RCAS_VERTEX_SHADER = "fsr/2.0/fsr_rcas_vertex.glsl";
    private static final String FSR_RCAS_FRAGMENT_SHADER = "fsr/2.0/fsr_rcas_fragment.glsl";
    private static final String PASSTHROUGH_FRAGMENT_SHADER = "fsr/2.0/passthrough_fragment.glsl";

    private final Context context;
    private final FloatBuffer fullscreenVertices = GlUtil.getFullscreenVertices();
    private final FloatBuffer fullscreenTexCoords = GlUtil.getFullscreenTexCoords();
    private final int[] intermediateFramebuffer = new int[1];
    private final int[] intermediateTexture = new int[1];

    private GlProgram twoPassEasuProgram;
    private GlProgram twoPassRcasProgram;
    private GlProgram singlePassProgram;
    private GlProgram passthroughProgram;

    private boolean fsrEnabled = true;
    private boolean hdrToneMappingEnabled;
    private float sharpness = 1.0f;
    private float hdrWhiteScale = 1.0f;
    private float hdrShadowLiftScale = 1.0f;
    private int outputWidth = -1;
    private int outputHeight = -1;
    private boolean twoPassInitializationFailed;
    private boolean singlePassInitializationFailed;

    public FsrVideoProcessor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public void initialize(int glMajorVersion, int glMinorVersion, String extensions) {
        release();

        try {
            twoPassEasuProgram = buildProgram(FSR_EASU_VERTEX_SHADER, FSR_EASU_FRAGMENT_SHADER);
            twoPassRcasProgram = buildProgram(FSR_RCAS_VERTEX_SHADER, FSR_RCAS_FRAGMENT_SHADER);
        } catch (IOException | GlUtil.GlException e) {
            twoPassInitializationFailed = true;
            safeDelete(twoPassEasuProgram);
            safeDelete(twoPassRcasProgram);
            twoPassEasuProgram = null;
            twoPassRcasProgram = null;
        }

        try {
            singlePassProgram = buildProgram(FSR_VERTEX_SHADER, FSR_FRAGMENT_SHADER);
        } catch (IOException | GlUtil.GlException e) {
            singlePassInitializationFailed = true;
            safeDelete(singlePassProgram);
            singlePassProgram = null;
        }

        try {
            passthroughProgram = buildProgram(FSR_VERTEX_SHADER, PASSTHROUGH_FRAGMENT_SHADER);
        } catch (IOException e) {
            throw new GlUtil.GlException("Unable to create passthrough shader program", e);
        }

        recreateIntermediateFramebuffer();
    }

    @Override
    public void setSurfaceSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (outputWidth == width && outputHeight == height) {
            return;
        }

        outputWidth = width;
        outputHeight = height;
        recreateIntermediateFramebuffer();
    }

    @Override
    public void draw(int frameTexture,
                     long frameTimestampUs,
                     int frameWidth,
                     int frameHeight,
                     float[] transformMatrix) {
        GLES20.glViewport(0, 0, Math.max(outputWidth, 1), Math.max(outputHeight, 1));
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (!fsrEnabled) {
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }

        if (hdrToneMappingEnabled) {
            if (canUseSinglePass(frameWidth, frameHeight)
                    && drawSinglePass(frameTexture, frameWidth, frameHeight, transformMatrix, true)) {
                return;
            }
            drawPassthrough(frameTexture, transformMatrix);
            return;
        }

        if (canUseTwoPass(frameWidth, frameHeight) && drawTwoPass(frameTexture, frameWidth, frameHeight, transformMatrix)) {
            return;
        }

        if (canUseSinglePass(frameWidth, frameHeight) && drawSinglePass(frameTexture, frameWidth, frameHeight, transformMatrix, false)) {
            return;
        }

        drawPassthrough(frameTexture, transformMatrix);
    }

    @Override
    public void release() {
        safeDelete(twoPassEasuProgram);
        safeDelete(twoPassRcasProgram);
        safeDelete(singlePassProgram);
        safeDelete(passthroughProgram);
        twoPassEasuProgram = null;
        twoPassRcasProgram = null;
        singlePassProgram = null;
        passthroughProgram = null;
        deleteIntermediateFramebuffer();
        twoPassInitializationFailed = false;
        singlePassInitializationFailed = false;
    }

    public void setFsrEnabled(boolean enabled) {
        fsrEnabled = enabled;
    }

    public boolean isFsrEnabled() {
        return fsrEnabled;
    }

    public void setHdrToneMappingEnabled(boolean enabled) {
        hdrToneMappingEnabled = enabled;
    }

    public void setSharpness(float sharpness) {
        this.sharpness = Math.max(0.0f, Math.min(2.0f, sharpness));
    }

    public void setHdrWhiteScale(float hdrWhiteScale) {
        this.hdrWhiteScale = Math.max(0.7f, Math.min(1.6f, hdrWhiteScale));
    }

    public void setHdrShadowLiftScale(float hdrShadowLiftScale) {
        this.hdrShadowLiftScale = Math.max(0.0f, Math.min(1.5f, hdrShadowLiftScale));
    }

    private boolean canUseTwoPass(int frameWidth, int frameHeight) {
        return !hdrToneMappingEnabled
                && !twoPassInitializationFailed
                && twoPassEasuProgram != null
                && twoPassRcasProgram != null
                && intermediateFramebuffer[0] != 0
                && intermediateTexture[0] != 0
                && outputWidth > 0
                && outputHeight > 0
                && frameWidth > 0
                && frameHeight > 0
                && outputWidth >= frameWidth
                && outputHeight >= frameHeight;
    }

    private boolean canUseSinglePass(int frameWidth, int frameHeight) {
        return !singlePassInitializationFailed
                && singlePassProgram != null
                && outputWidth > 0
                && outputHeight > 0
                && frameWidth > 0
                && frameHeight > 0;
    }

    private boolean drawTwoPass(int frameTexture,
                                int frameWidth,
                                int frameHeight,
                                float[] transformMatrix) {
        GlProgram easu = twoPassEasuProgram;
        GlProgram rcas = twoPassRcasProgram;
        if (easu == null || rcas == null) {
            return false;
        }

        try {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, intermediateFramebuffer[0]);
            GLES20.glViewport(0, 0, outputWidth, outputHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            easu.setSamplerTexIdUniform("inputTexture", frameTexture, 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            easu.setMatrix4Uniform("uTexTransform", transformMatrix);
            easu.setFloatsUniform("inputTextureSize", new float[] {frameWidth, frameHeight});
            easu.setFloatsUniform("outputTextureSize", new float[] {outputWidth, outputHeight});
            easu.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GlUtil.checkGlError("glDrawArrays(twoPassEasu)");

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(0, 0, outputWidth, outputHeight);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            rcas.setSamplerTexIdUniform("inputTexture", intermediateTexture[0], 0, GLES20.GL_TEXTURE_2D);
            rcas.setFloatsUniform("inputTextureSize", new float[] {outputWidth, outputHeight});
            rcas.setFloatUniform("sharpness", getRcasSharpness());
            rcas.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GlUtil.checkGlError("glDrawArrays(twoPassRcas)");
            return true;
        } catch (GlUtil.GlException e) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            return false;
        }
    }

    private boolean drawSinglePass(int frameTexture,
                                   int frameWidth,
                                   int frameHeight,
                                   float[] transformMatrix,
                                   boolean hdrMode) {
        GlProgram program = singlePassProgram;
        if (program == null) {
            return false;
        }

        try {
            program.setSamplerTexIdUniform("inputTexture", frameTexture, 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
            program.setMatrix4Uniform("uTexTransform", transformMatrix);
            program.setFloatUniform("uHdrToneMap", hdrMode ? 1.0f : 0.0f);
            program.setFloatUniform("uHdrWhiteScale", hdrWhiteScale);
            program.setFloatUniform("uHdrShadowLiftScale", hdrShadowLiftScale);
            program.setFloatsUniform("inputTextureSize", new float[] {frameWidth, frameHeight});
            program.setFloatsUniform("outputTextureSize", new float[] {outputWidth, outputHeight});
            program.setFloatUniform("sharpness", hdrMode ? getHdrSinglePassSharpness() : sharpness);
            program.bindAttributesAndUniforms();
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            GlUtil.checkGlError("glDrawArrays(singlePassFsr)");
            return true;
        } catch (GlUtil.GlException e) {
            return false;
        }
    }

    private void drawPassthrough(int frameTexture, float[] transformMatrix) {
        GlProgram program = passthroughProgram;
        if (program == null) {
            throw new GlUtil.GlException("No GL program available for video processing");
        }

        program.setSamplerTexIdUniform("inputTexture", frameTexture, 0, GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        program.setMatrix4Uniform("uTexTransform", transformMatrix);
        program.setFloatUniform("uHdrToneMap", hdrToneMappingEnabled ? 1.0f : 0.0f);
        program.setFloatUniform("uHdrWhiteScale", hdrWhiteScale);
        program.setFloatUniform("uHdrShadowLiftScale", hdrShadowLiftScale);
        if (outputWidth > 0 && outputHeight > 0) {
            program.setFloatsUniform("inputTextureSize", new float[] {outputWidth, outputHeight});
        }
        program.bindAttributesAndUniforms();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkGlError("glDrawArrays(passthrough)");
    }

    private GlProgram buildProgram(String vertexShaderAsset, String fragmentShaderAsset) throws IOException {
        GlProgram program = new GlProgram(context, vertexShaderAsset, fragmentShaderAsset);
        program.setBufferAttribute("aPosition", fullscreenVertices, 2);
        program.setBufferAttribute("aTexCoords", fullscreenTexCoords, 2);
        return program;
    }

    private float getRcasSharpness() {
        return 2.0f - sharpness;
    }

    private float getHdrSinglePassSharpness() {
        return Math.max(0.15f, Math.min(0.6f, sharpness * 0.45f));
    }

    private void recreateIntermediateFramebuffer() {
        deleteIntermediateFramebuffer();

        if (twoPassInitializationFailed || twoPassEasuProgram == null || twoPassRcasProgram == null
                || outputWidth <= 0 || outputHeight <= 0) {
            return;
        }

        try {
            GLES20.glGenFramebuffers(1, intermediateFramebuffer, 0);
            GlUtil.checkGlError("glGenFramebuffers");

            intermediateTexture[0] = GlUtil.createTexture2D();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, intermediateTexture[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    outputWidth, outputHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, null);
            GlUtil.checkGlError("glTexImage2D(intermediate)");

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, intermediateFramebuffer[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, intermediateTexture[0], 0);
            GlUtil.checkGlError("glFramebufferTexture2D");

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw new GlUtil.GlException("Framebuffer creation failed with status 0x" + Integer.toHexString(status));
            }

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        } catch (GlUtil.GlException e) {
            Log.w(TAG, "Disabling two-pass FSR framebuffer path after GL init failure", e);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            deleteIntermediateFramebuffer();
            twoPassInitializationFailed = true;
        }
    }

    private void deleteIntermediateFramebuffer() {
        if (intermediateFramebuffer[0] != 0) {
            GLES20.glDeleteFramebuffers(1, intermediateFramebuffer, 0);
            intermediateFramebuffer[0] = 0;
        }

        if (intermediateTexture[0] != 0) {
            GLES20.glDeleteTextures(1, intermediateTexture, 0);
            intermediateTexture[0] = 0;
        }
    }

    private void safeDelete(GlProgram program) {
        if (program != null) {
            program.delete();
        }
    }
}
