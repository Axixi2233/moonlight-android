#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif

uniform samplerExternalOES inputTexture;
uniform mat4 uTexTransform;
uniform float uSourceAspect;
uniform float uEyeAspect;
uniform float uDepthStrength;
uniform float uConvergence;
uniform float uSwapEyes;

varying vec2 vOutputCoord;

vec2 transformUv(vec2 uv) {
    return (uTexTransform * vec4(uv, 0.0, 1.0)).xy;
}

vec3 sampleSource(vec2 uv) {
    return texture2D(inputTexture, transformUv(uv)).rgb;
}

bool mapEyeUv(vec2 eyeUv, out vec2 sourceUv) {
    sourceUv = eyeUv;
    if (uEyeAspect > uSourceAspect) {
        float widthFraction = uSourceAspect / uEyeAspect;
        float left = 0.5 - widthFraction * 0.5;
        if (eyeUv.x < left || eyeUv.x > left + widthFraction) {
            return false;
        }
        sourceUv.x = (eyeUv.x - left) / widthFraction;
    }
    else {
        float heightFraction = uEyeAspect / uSourceAspect;
        float top = 0.5 - heightFraction * 0.5;
        if (eyeUv.y < top || eyeUv.y > top + heightFraction) {
            return false;
        }
        sourceUv.y = (eyeUv.y - top) / heightFraction;
    }

    // Reserve a little horizontal room for parallax without changing vertical
    // geometry. Scaling Y here makes the top and bottom look pulled inward in AR.
    sourceUv.x = (sourceUv.x - 0.5) * 0.965 + 0.5;
    return true;
}

float estimateDepth(vec3 color, float detailRange) {
    // Stable broad regions can carry stronger depth. Text, edges, and textured
    // regions retain the conservative gain that keeps their geometry readable.
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    float stability = 1.0 - smoothstep(0.06, 0.22, detailRange);
    float lumaGain = mix(0.18, 0.30, stability);
    float warmthCue = (color.r - color.b) * 0.035 * stability;
    return clamp(0.50 + (luma - 0.50) * lumaGain + warmthCue, 0.38, 0.62);
}

vec3 sampleLowFrequencyDepthColor(vec2 uv, out float detailRange) {
    // Sample four diagonal points about 32 source pixels away at 1920x1080.
    // Avoiding same-row taps keeps neighbouring glyphs from bending each other,
    // while the wider footprint makes a whole text run share one shallow layer.
    const vec2 depthOffset = vec2(0.0167, 0.0296);
    vec2 minUv = vec2(0.002);
    vec2 maxUv = vec2(0.998);
    vec3 c0 = sampleSource(clamp(uv - depthOffset, minUv, maxUv));
    vec3 c1 = sampleSource(clamp(
            uv + vec2(depthOffset.x, -depthOffset.y), minUv, maxUv));
    vec3 c2 = sampleSource(clamp(
            uv + vec2(-depthOffset.x, depthOffset.y), minUv, maxUv));
    vec3 c3 = sampleSource(clamp(uv + depthOffset, minUv, maxUv));

    vec3 minColor = min(min(c0, c1), min(c2, c3));
    vec3 maxColor = max(max(c0, c1), max(c2, c3));
    vec3 colorRange = maxColor - minColor;
    float chromaRange = max(colorRange.r, max(colorRange.g, colorRange.b));
    float l0 = dot(c0, vec3(0.2126, 0.7152, 0.0722));
    float l1 = dot(c1, vec3(0.2126, 0.7152, 0.0722));
    float l2 = dot(c2, vec3(0.2126, 0.7152, 0.0722));
    float l3 = dot(c3, vec3(0.2126, 0.7152, 0.0722));
    float minLuma = min(min(l0, l1), min(l2, l3));
    float maxLuma = max(max(l0, l1), max(l2, l3));
    detailRange = max(maxLuma - minLuma, chromaRange * 0.50);
    return (c0 + c1 + c2 + c3) * 0.25;
}

void main() {
    bool leftEye = vOutputCoord.x < 0.5;
    vec2 eyeUv = vec2(leftEye ? vOutputCoord.x * 2.0
                             : (vOutputCoord.x - 0.5) * 2.0,
                      vOutputCoord.y);
    vec2 sourceUv;
    if (!mapEyeUv(eyeUv, sourceUv)) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    float detailRange;
    vec3 depthColor = sampleLowFrequencyDepthColor(sourceUv, detailRange);
    float depth = estimateDepth(depthColor, detailRange);
    float eyeSign = leftEye ? -1.0 : 1.0;
    if (uSwapEyes > 0.5) {
        eyeSign = -eyeSign;
    }
    sourceUv.x += eyeSign * (depth - uConvergence) * uDepthStrength;
    sourceUv.x = clamp(sourceUv.x, 0.002, 0.998);
    gl_FragColor = vec4(sampleSource(sourceUv), 1.0);
}
