#version 300 es
precision highp float;

in float vAlpha;

uniform vec3 uColor;

out vec4 fragColor;

void main() {
    // 线段头端不透明、尾端渐隐，由 CPU 侧写入的 alpha 控制
    fragColor = vec4(uColor, vAlpha);
}
