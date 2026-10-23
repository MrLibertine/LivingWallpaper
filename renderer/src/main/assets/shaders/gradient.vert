#version 300 es
precision highp float;

layout(location = 0) in vec2 aPosition;

out vec2 vUv;

void main() {
    // 使用覆盖裁剪空间的大三角形，避免索引缓冲与额外顶点。
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
