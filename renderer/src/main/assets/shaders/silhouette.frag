#version 300 es
precision highp float;

in float vSway;
in float vY;

uniform vec3 uColor;
uniform float uAlpha;
uniform float uFadeBase;  // 底部雾化起点（NDC y），0 以下不启用
uniform float uFadeRange;

out vec4 fragColor;

void main() {
    // 越靠近树冠越亮一点，形成层次感
    vec3 color = uColor * (0.82 + 0.45 * vSway);
    // 底部雾化：山脚接近海平线时透明度衰减，消除与海面交界的硬边
    float fade = 1.0;
    if (uFadeRange > 0.0) {
        fade = mix(0.35, 1.0, clamp((vY - uFadeBase) / uFadeRange, 0.0, 1.0));
    }
    fragColor = vec4(color, uAlpha * fade);
}
