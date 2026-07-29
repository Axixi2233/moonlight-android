package com.limelight.stereo3d;

public final class NoOpStereo3dBackend implements Stereo3dBackend {
    public static final NoOpStereo3dBackend INSTANCE = new NoOpStereo3dBackend();

    private NoOpStereo3dBackend() {
    }

    @Override
    public boolean initialize(int glMajorVersion, int glMinorVersion, String extensions) {
        return false;
    }

    @Override
    public void setSurfaceSize(int width, int height) {
    }

    @Override
    public void submitDepthSource(int textureId, int textureTarget, long timestampUs,
                                  int sourceWidth, int sourceHeight, float[] textureTransform) {
    }

    @Override
    public boolean renderStereo(int colorTextureId, int colorTextureTarget,
                                int sourceWidth, int sourceHeight, float[] textureTransform,
                                int outputWidth, int outputHeight, float depthStrength,
                                float convergence, boolean swapEyes) {
        return false;
    }

    @Override
    public String getDisplayName() {
        return "内置兼容模式";
    }

    @Override
    public void release() {
    }
}
