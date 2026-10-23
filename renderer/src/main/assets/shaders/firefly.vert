#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;
layout(location = 1) in float aPhase;
layout(location = 2) in float aSize;

uniform float uTime;

out float vAlpha;

void main() {
    float blink = sin(uTime * 1.6 + aPhase * 6.2831853) * 0.5 + 0.5;
    blink = blink * blink;
    vAlpha = 0.12 + 0.88 * blink;

    gl_Position = vec4(aPosition, 0.0, 1.0);
    gl_PointSize = aSize;
}
