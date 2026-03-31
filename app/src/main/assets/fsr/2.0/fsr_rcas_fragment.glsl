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

precision mediump float;

uniform sampler2D inputTexture;
uniform vec2 inputTextureSize;
uniform float sharpness;

varying vec2 vTexCoord;

float APrxMedRcpF1(float a) {
    return 1.0 / a;
}

float AMin3F1(float x, float y, float z) {
    return min(x, min(y, z));
}

float AMax3F1(float x, float y, float z) {
    return max(x, max(y, z));
}

#define FSR_RCAS_LIMIT (0.25-(1.0/16.0))

void FsrRcasCon(out vec4 con, float userSharpness) {
    float linearSharpness = exp2(-userSharpness);
    con = vec4(linearSharpness, 0.0, 0.0, 0.0);
}

void FsrRcasF(out vec3 pix, vec2 ip, vec4 con, sampler2D tex) {
    vec3 b = texture2D(tex, (ip + vec2(0.0, -1.0)) / inputTextureSize).rgb;
    vec3 d = texture2D(tex, (ip + vec2(-1.0, 0.0)) / inputTextureSize).rgb;
    vec3 e = texture2D(tex, ip / inputTextureSize).rgb;
    vec3 f = texture2D(tex, (ip + vec2(1.0, 0.0)) / inputTextureSize).rgb;
    vec3 h = texture2D(tex, (ip + vec2(0.0, 1.0)) / inputTextureSize).rgb;

    float bR = b.r;
    float bG = b.g;
    float bB = b.b;
    float dR = d.r;
    float dG = d.g;
    float dB = d.b;
    float eR = e.r;
    float eG = e.g;
    float eB = e.b;
    float fR = f.r;
    float fG = f.g;
    float fB = f.b;
    float hR = h.r;
    float hG = h.g;
    float hB = h.b;

    float bL = bB * 0.5 + (bR * 0.5 + bG);
    float dL = dB * 0.5 + (dR * 0.5 + dG);
    float eL = eB * 0.5 + (eR * 0.5 + eG);
    float fL = fB * 0.5 + (fR * 0.5 + fG);
    float hL = hB * 0.5 + (hR * 0.5 + hG);

    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    nz = clamp(abs(nz) * APrxMedRcpF1(AMax3F1(AMax3F1(bL, dL, eL), fL, hL) - AMin3F1(AMin3F1(bL, dL, eL), fL, hL)), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    float mn4R = min(AMin3F1(bR, dR, fR), hR);
    float mn4G = min(AMin3F1(bG, dG, fG), hG);
    float mn4B = min(AMin3F1(bB, dB, fB), hB);
    float mx4R = max(AMax3F1(bR, dR, fR), hR);
    float mx4G = max(AMax3F1(bG, dG, fG), hG);
    float mx4B = max(AMax3F1(bB, dB, fB), hB);

    vec2 peakC = vec2(1.0, -4.0);

    float hitMinR = min(mn4R, eR) / (4.0 * mx4R);
    float hitMinG = min(mn4G, eG) / (4.0 * mx4G);
    float hitMinB = min(mn4B, eB) / (4.0 * mx4B);
    float hitMaxR = (peakC.x - max(mx4R, eR)) / (4.0 * mn4R + peakC.y);
    float hitMaxG = (peakC.x - max(mx4G, eG)) / (4.0 * mn4G + peakC.y);
    float hitMaxB = (peakC.x - max(mx4B, eB)) / (4.0 * mn4B + peakC.y);
    float lobeR = max(-hitMinR, hitMaxR);
    float lobeG = max(-hitMinG, hitMaxG);
    float lobeB = max(-hitMinB, hitMaxB);
    float lobe = max(-FSR_RCAS_LIMIT, min(AMax3F1(lobeR, lobeG, lobeB), 0.0)) * con.x;

    float rcpL = APrxMedRcpF1(4.0 * lobe + 1.0);
    float pixR = (lobe * bR + lobe * dR + lobe * hR + lobe * fR + eR) * rcpL;
    float pixG = (lobe * bG + lobe * dG + lobe * hG + lobe * fG + eG) * rcpL;
    float pixB = (lobe * bB + lobe * dB + lobe * hB + lobe * fB + eB) * rcpL;
    pix = vec3(pixR, pixG, pixB);
}

void main() {
    vec2 ip = vTexCoord.xy * inputTextureSize;
    vec3 pix;
    vec4 con;
    FsrRcasCon(con, sharpness);
    FsrRcasF(pix, ip, con, inputTexture);
    gl_FragColor = vec4(pix, 1.0);
}
