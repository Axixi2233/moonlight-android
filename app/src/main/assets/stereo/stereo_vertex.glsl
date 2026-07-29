attribute vec4 aPosition;
attribute vec4 aTexCoords;

varying vec2 vOutputCoord;

void main() {
    gl_Position = aPosition;
    vOutputCoord = aTexCoords.xy;
}
