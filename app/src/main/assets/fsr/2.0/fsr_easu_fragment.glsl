// Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

#extension GL_OES_EGL_image_external : require
precision mediump float;

uniform samplerExternalOES inputTexture;
uniform vec2 inputTextureSize;
uniform vec2 outputTextureSize;

varying vec2 vTexCoord;

float APrxLoRcpF1(float a) {
    return 1.0 / a;
}

float APrxLoRsqF1(float a) {
    return 1.0 / sqrt(a);
}

vec3 AMin3F3(vec3 x, vec3 y, vec3 z) {
    return min(x, min(y, z));
}

vec3 AMax3F3(vec3 x, vec3 y, vec3 z) {
    return max(x, max(y, z));
}

void FsrEasuCon(
    out vec4 con0, out vec4 con1, out vec4 con2, out vec4 con3,
    float inputViewportInPixelsX, float inputViewportInPixelsY,
    float inputSizeInPixelsX, float inputSizeInPixelsY,
    float outputSizeInPixelsX, float outputSizeInPixelsY
) {
    con0 = vec4(
      inputViewportInPixelsX / outputSizeInPixelsX,
      inputViewportInPixelsY / outputSizeInPixelsY,
      0.5 * inputViewportInPixelsX / outputSizeInPixelsX - 0.5,
      0.5 * inputViewportInPixelsY / outputSizeInPixelsY - 0.5
    );

    con1 = vec4(
      1.0 / inputSizeInPixelsX,
      1.0 / inputSizeInPixelsY,
      1.0 / inputSizeInPixelsX,
      -1.0 / inputSizeInPixelsY
    );

    con2 = vec4(
      -1.0 / inputSizeInPixelsX,
      2.0 / inputSizeInPixelsY,
      1.0 / inputSizeInPixelsX,
      2.0 / inputSizeInPixelsY
    );

    con3 = vec4(
      0.0 / inputSizeInPixelsX,
      4.0 / inputSizeInPixelsY,
      0.0,
      0.0
    );
}

void FsrEasuTapF(
    inout vec3 aC,
    inout float aW,
    vec2 off,
    vec2 dir,
    vec2 len,
    float lob,
    float clp,
    vec3 c
) {
    vec2 v;
    v.x = (off.x * dir.x) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len;
    float d2 = v.x * v.x + v.y * v.y;
    d2 = min(d2, clp);
    float wB = float(2.0 / 5.0) * d2 + -1.0;
    float wA = lob * d2 + -1.0;
    wB *= wB;
    wA *= wA;
    wB = float(25.0 / 16.0) * wB + float(-(25.0 / 16.0 - 1.0));
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void FsrEasuSetF(
    inout vec2 dir,
    inout float len,
    vec2 pp,
    bool biS, bool biT, bool biU, bool biV,
    float lA, float lB, float lC, float lD, float lE
) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w = pp.x * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) * pp.y;
    if (biV) w = pp.x * pp.y;

    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = APrxLoRcpF1(lenX);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;

    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = APrxLoRcpF1(lenY);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void FsrEasuF(
    out vec3 pix,
    vec2 ip,
    vec4 con0, vec4 con1, vec4 con2, vec4 con3,
    samplerExternalOES tex
) {
    vec2 pp = ip * con0.xy + con0.zw;
    vec2 fp = floor(pp);
    pp -= fp;

    vec3 b = texture2D(tex, (fp + vec2(0.5, -0.5)) * con1.xy).rgb;
    vec3 c = texture2D(tex, (fp + vec2(1.5, -0.5)) * con1.xy).rgb;
    vec3 e = texture2D(tex, (fp + vec2(-0.5, 0.5)) * con1.xy).rgb;
    vec3 f = texture2D(tex, (fp + vec2(0.5, 0.5)) * con1.xy).rgb;
    vec3 g = texture2D(tex, (fp + vec2(1.5, 0.5)) * con1.xy).rgb;
    vec3 h = texture2D(tex, (fp + vec2(2.5, 0.5)) * con1.xy).rgb;
    vec3 i = texture2D(tex, (fp + vec2(-0.5, 1.5)) * con1.xy).rgb;
    vec3 j = texture2D(tex, (fp + vec2(0.5, 1.5)) * con1.xy).rgb;
    vec3 k = texture2D(tex, (fp + vec2(1.5, 1.5)) * con1.xy).rgb;
    vec3 l = texture2D(tex, (fp + vec2(2.5, 1.5)) * con1.xy).rgb;
    vec3 n = texture2D(tex, (fp + vec2(0.5, 2.5)) * con1.xy).rgb;
    vec3 o = texture2D(tex, (fp + vec2(1.5, 2.5)) * con1.xy).rgb;

    vec4 bczzR = vec4(b.r, c.r, 0.0, 0.0);
    vec4 bczzG = vec4(b.g, c.g, 0.0, 0.0);
    vec4 bczzB = vec4(b.b, c.b, 0.0, 0.0);
    vec4 ijfeR = vec4(i.r, j.r, f.r, e.r);
    vec4 ijfeG = vec4(i.g, j.g, f.g, e.g);
    vec4 ijfeB = vec4(i.b, j.b, f.b, e.b);
    vec4 klhgR = vec4(k.r, l.r, h.r, g.r);
    vec4 klhgG = vec4(k.g, l.g, h.g, g.g);
    vec4 klhgB = vec4(k.b, l.b, h.b, g.b);
    vec4 zzonR = vec4(0.0, 0.0, o.r, n.r);
    vec4 zzonG = vec4(0.0, 0.0, o.g, n.g);
    vec4 zzonB = vec4(0.0, 0.0, o.b, n.b);

    vec4 bczzL = bczzB * 0.5 + bczzG * 0.5 + bczzR;
    vec4 ijfeL = ijfeB * 0.5 + ijfeG * 0.5 + ijfeR;
    vec4 klhgL = klhgB * 0.5 + klhgG * 0.5 + klhgR;
    vec4 zzonL = zzonB * 0.5 + zzonG * 0.5 + zzonR;

    float bL = bczzL.x;
    float cL = bczzL.y;
    float iL = ijfeL.x;
    float jL = ijfeL.y;
    float fL = ijfeL.z;
    float eL = ijfeL.w;
    float kL = klhgL.x;
    float lL = klhgL.y;
    float hL = klhgL.z;
    float gL = klhgL.w;
    float oL = zzonL.z;
    float nL = zzonL.w;

    vec2 dir = vec2(0.0);
    float len = 0.0;
    FsrEasuSetF(dir, len, pp, true, false, false, false, bL, eL, fL, gL, jL);
    FsrEasuSetF(dir, len, pp, false, true, false, false, cL, fL, gL, hL, kL);
    FsrEasuSetF(dir, len, pp, false, false, true, false, fL, iL, jL, kL, nL);
    FsrEasuSetF(dir, len, pp, false, false, false, true, gL, jL, kL, lL, oL);

    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < float(1.0 / 32768.0);
    dirR = APrxLoRsqF1(dirR);
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= vec2(dirR);

    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) * APrxLoRcpF1(max(abs(dir.x), abs(dir.y)));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 + -0.5 * len);
    float lob = 0.5 + float((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = APrxLoRcpF1(lob);

    vec3 min4 = min(AMin3F3(
        vec3(ijfeR.z, ijfeG.z, ijfeB.z),
        vec3(klhgR.w, klhgG.w, klhgB.w),
        vec3(ijfeR.y, ijfeG.y, ijfeB.y)),
        vec3(klhgR.x, klhgG.x, klhgB.x)
    );
    vec3 max4 = max(AMax3F3(
        vec3(ijfeR.z, ijfeG.z, ijfeB.z),
        vec3(klhgR.w, klhgG.w, klhgB.w),
        vec3(ijfeR.y, ijfeG.y, ijfeB.y)),
        vec3(klhgR.x, klhgG.x, klhgB.x)
    );
    vec3 aC = vec3(0.0);
    float aW = 0.0;
    FsrEasuTapF(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, vec3(bczzR.x, bczzG.x, bczzB.x));
    FsrEasuTapF(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, vec3(bczzR.y, bczzG.y, bczzB.y));
    FsrEasuTapF(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, vec3(ijfeR.x, ijfeG.x, ijfeB.x));
    FsrEasuTapF(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, vec3(ijfeR.y, ijfeG.y, ijfeB.y));
    FsrEasuTapF(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, vec3(ijfeR.z, ijfeG.z, ijfeB.z));
    FsrEasuTapF(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, vec3(ijfeR.w, ijfeG.w, ijfeB.w));
    FsrEasuTapF(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, vec3(klhgR.x, klhgG.x, klhgB.x));
    FsrEasuTapF(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, vec3(klhgR.y, klhgG.y, klhgB.y));
    FsrEasuTapF(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, vec3(klhgR.z, klhgG.z, klhgB.z));
    FsrEasuTapF(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, vec3(klhgR.w, klhgG.w, klhgB.w));
    FsrEasuTapF(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, vec3(zzonR.z, zzonG.z, zzonB.z));
    FsrEasuTapF(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, vec3(zzonR.w, zzonG.w, zzonB.w));

    pix = min(max4, max(min4, aC * vec3((1.0 / aW))));
}

void main() {
    vec4 con0;
    vec4 con1;
    vec4 con2;
    vec4 con3;
    FsrEasuCon(con0, con1, con2, con3,
            inputTextureSize.x, inputTextureSize.y,
            inputTextureSize.x, inputTextureSize.y,
            outputTextureSize.x, outputTextureSize.y);
    vec3 pix;
    vec2 ip = floor(vTexCoord * outputTextureSize);
    FsrEasuF(pix, ip, con0, con1, con2, con3, inputTexture);
    gl_FragColor = vec4(pix, 1.0);
}
