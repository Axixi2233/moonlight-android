package com.limelight.stereo3d;

/**
 * Optional AI-assisted 2D-to-3D renderer contract.
 *
 * <p>All methods are invoked on the stream's GL thread. Implementations must keep
 * {@link #submitDepthSource} non-blocking: copy or enqueue the newest low-resolution
 * input and discard stale inference work instead of delaying video presentation.</p>
 */
public interface Stereo3dBackend {
    /**
     * Creates GL resources and starts any asynchronous inference worker.
     *
     * @return true only when the enhanced renderer can accept frames
     */
    boolean initialize(int glMajorVersion, int glMinorVersion, String extensions);

    /**
     * Receives the actual output surface dimensions.
     */
    void setSurfaceSize(int width, int height);

    /**
     * Offers the decoded OES frame as the depth-estimation source.
     *
     * <p>This call must never wait for model inference. The texture and transform are
     * only valid for the duration of the call.</p>
     */
    void submitDepthSource(int textureId,
                           int textureTarget,
                           long timestampUs,
                           int sourceWidth,
                           int sourceHeight,
                           float[] textureTransform);

    /**
     * Attempts to draw a complete SBS frame into the current output framebuffer.
     *
     * <p>The color source may be the decoded external texture or a 2D texture produced
     * by FSR. Returning false tells the host to immediately use its built-in stereo
     * shader for this frame. Implementations should return false before drawing when
     * no valid depth result is available. {@code outputLayout} describes whether each
     * eye keeps full horizontal resolution or is stored at half width for anamorphic
     * expansion by the target display.</p>
     */
    boolean renderStereo(int colorTextureId,
                         int colorTextureTarget,
                         int sourceWidth,
                         int sourceHeight,
                         float[] textureTransform,
                         int outputWidth,
                         int outputHeight,
                         int outputLayout,
                         float depthStrength,
                         float convergence,
                         boolean swapEyes);

    String getDisplayName();

    void release();
}
