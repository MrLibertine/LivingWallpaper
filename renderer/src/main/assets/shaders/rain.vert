#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;
layout(location = 1) in float aAlpha;

out float vAlpha;

void main() {
    vAlpha = aAlpha;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
