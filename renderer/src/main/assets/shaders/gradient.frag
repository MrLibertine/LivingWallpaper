#version 300 es
precision highp float;

in vec2 vUv;

uniform float uTime;
uniform vec2 uResolution;
uniform vec3 uTopColor;
uniform vec3 uBottomColor;
uniform vec3 uAccentColor;

out vec4 fragColor;

void main() {
    float aspect = uResolution.x / max(uResolution.y, 1.0);

    // 1) 垂直基础渐变（夜空由下至上由亮转暗）
    vec3 color = mix(uBottomColor, uTopColor, smoothstep(0.0, 1.0, vUv.y));

    vec2 p = vec2(vUv.x * aspect, vUv.y);

    // 2) 缓慢横向流动的光带：证明画面是逐帧实时计算，而不是静态图或视频
    float band = sin(vUv.y * 6.0 - uTime * 0.35) * 0.5 + 0.5;
    band = pow(band, 8.0);
    color += uAccentColor * band * 0.16;

    // 3) 缓慢移动的光晕
    vec2 glowCenter = vec2(aspect * (0.5 + 0.28 * sin(uTime * 0.22)), 0.74);
    float glow = smoothstep(0.55, 0.0, distance(p, glowCenter));
    color += uAccentColor * glow * 0.22;

    // 4) 轻微暗角
    float vignette = smoothstep(1.25, 0.25, length((vUv - 0.5) * vec2(aspect, 1.0)));
    color *= mix(0.72, 1.0, vignette);

    fragColor = vec4(color, 1.0);
}
