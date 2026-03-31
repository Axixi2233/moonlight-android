#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES inputTexture;
uniform float uHdrToneMap;
uniform float uHdrWhiteScale;
uniform float uHdrShadowLiftScale;
uniform vec2 inputTextureSize;

varying vec2 vTexCoord;

vec3 applyHdrToneMap(vec3 color) {
    if (uHdrToneMap < 0.5) {
        return color;
    }

    const float m1 = 0.1593017578125;
    const float m2 = 78.84375;
    const float c1 = 0.8359375;
    const float c2 = 18.8515625;
    const float c3 = 18.6875;

    vec3 powered = pow(max(color, vec3(0.0)), vec3(1.0 / m2));
    vec3 numerator = max(powered - vec3(c1), vec3(0.0));
    vec3 denominator = max(vec3(c2) - vec3(c3) * powered, vec3(1e-6));

    vec3 linearHdr2020 = pow(numerator / denominator, vec3(1.0 / m1)) * 10000.0;
    vec3 linearHdr709 = max(vec3(
        dot(linearHdr2020, vec3(1.6605, -0.5876, -0.0728)),
        dot(linearHdr2020, vec3(-0.1246, 1.1329, -0.0083)),
        dot(linearHdr2020, vec3(-0.0182, -0.1006, 1.1187))
    ), vec3(0.0));
    float hdrWhiteScale = max(uHdrWhiteScale, 0.1);
    vec3 linearScene = linearHdr709 / (240.0 * hdrWhiteScale);
    vec3 linearSdr = (linearScene * (2.51 * linearScene + 0.03)) /
            (linearScene * (2.43 * linearScene + 0.59) + 0.14);
    linearSdr = pow(max(linearSdr, vec3(0.0)), vec3(0.94));
    vec3 shadowLift = smoothstep(vec3(0.02), vec3(0.10), linearSdr) *
            (vec3(1.0) - smoothstep(vec3(0.10), vec3(0.32), linearSdr)) * (0.010 * uHdrShadowLiftScale);
    vec3 highlightRollOff = smoothstep(vec3(0.40), vec3(0.92), linearSdr) * 0.125;
    vec3 highlightCompression = smoothstep(vec3(0.62), vec3(1.0), linearSdr) * 0.070;
    linearSdr = linearSdr + shadowLift - highlightRollOff - highlightCompression;
    linearSdr = mix(linearSdr, linearSdr * 0.87, smoothstep(vec3(0.74), vec3(1.0), linearSdr));
    linearSdr = clamp(linearSdr, 0.0, 1.0);

    vec3 lower = linearSdr * 12.92;
    vec3 higher = 1.055 * pow(max(linearSdr, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    vec3 cutoff = step(vec3(0.0031308), linearSdr);
    return mix(lower, higher, cutoff);
}

vec3 sampleToneMapped(vec2 uv) {
    vec3 color = texture2D(inputTexture, uv).rgb;
    return applyHdrToneMap(color);
}

void main() {
    vec3 center = sampleToneMapped(vTexCoord);
    if (uHdrToneMap < 0.5 || inputTextureSize.x <= 0.0 || inputTextureSize.y <= 0.0) {
        gl_FragColor = vec4(center, 1.0);
        return;
    }

    vec2 texel = vec2(1.0 / inputTextureSize.x, 1.0 / inputTextureSize.y);
    vec3 north = sampleToneMapped(vTexCoord + vec2(0.0, -texel.y));
    vec3 south = sampleToneMapped(vTexCoord + vec2(0.0, texel.y));
    vec3 west = sampleToneMapped(vTexCoord + vec2(-texel.x, 0.0));
    vec3 east = sampleToneMapped(vTexCoord + vec2(texel.x, 0.0));

    vec3 neighborhoodMin = min(center, min(min(north, south), min(west, east)));
    vec3 neighborhoodMax = max(center, max(max(north, south), max(west, east)));
    vec3 edge = center - (north + south + west + east) * 0.25;
    float luma = dot(center, vec3(0.2126, 0.7152, 0.0722));
    float highlightFade = 1.0 - smoothstep(0.55, 0.88, luma);
    vec3 sharpened = center + edge * (0.08 * highlightFade);
    sharpened = clamp(sharpened, neighborhoodMin - 0.015, neighborhoodMax + 0.015);

    gl_FragColor = vec4(sharpened, 1.0);
}
