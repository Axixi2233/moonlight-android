attribute vec4 aPosition;
attribute vec4 aTexCoords;

varying vec2 vTexCoord;

void main() {
    gl_Position = aPosition;
    vTexCoord = aTexCoords.xy;
}
