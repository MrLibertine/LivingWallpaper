#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;
layout(location = 1) in float aSway;

uniform vec2 uOffset;   // 视差偏移（NDC）
uniform float uTime;
uniform float uSway;    // 当前摆动幅度

out float vSway;
out float vY;

void main() {
    // 两个不同频率的正弦叠加，避免所有树同步摆动
    float osc = sin(uTime * 1.15 + aPosition.x * 3.7) * 0.6
              + sin(uTime * 0.63 + aPosition.x * 1.9) * 0.4;

    vec2 pos = aPosition;
    pos.x += uOffset.x + osc * uSway * aSway;
    pos.y += uOffset.y;

    vSway = aSway;
    vY = pos.y;
    gl_Position = vec4(pos, 0.0, 1.0);
}
