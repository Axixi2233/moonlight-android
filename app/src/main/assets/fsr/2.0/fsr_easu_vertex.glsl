attribute vec4 aPosition;
attribute vec4 aTexCoords;

varying vec2 vTexCoord;

uniform mat4 uTexTransform;

void main() {
    gl_Position = aPosition;
    vTexCoord = (uTexTransform * aTexCoords).xy;
}
