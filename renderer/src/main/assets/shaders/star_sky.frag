#version 300 es
precision highp float;

in vec2 vUv;

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uSkyOffset;
uniform vec3 uTopColor;
uniform vec3 uBottomColor;
uniform float uStarDensity;
uniform float uCloudStrength;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x),
        u.y
    );
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return value;
}

void main() {
    float aspect = uResolution.x / max(uResolution.y, 1.0);

    // 1) 夜空渐变
    vec3 sky = mix(uBottomColor, uTopColor, smoothstep(0.0, 1.0, vUv.y));

    // 2) 星星：网格哈希定位 + 独立相位闪烁
    vec2 gridUv = vec2(vUv.x * aspect, vUv.y) * 95.0;
    vec2 cell = fract(gridUv) - 0.5;
    float h = hash(floor(gridUv));
    float exists = step(1.0 - uStarDensity * 0.055, h);
    float twinkle = 0.45 + 0.55 * sin(uTime * 2.1 + h * 41.0);
    float point = smoothstep(0.34, 0.0, length(cell));
    sky += vec3(0.90, 0.94, 1.0) * exists * point * twinkle;

    // 3) 缓慢云层：只在中上部出现，避免遮挡山体
    vec2 cloudUv = vec2(vUv.x + uSkyOffset.x, vUv.y + uSkyOffset.y);
    float n = fbm(vec2(cloudUv.x * 2.2 + uTime * 0.010, cloudUv.y * 4.0));
    float clouds = smoothstep(0.50, 0.92, n) * uCloudStrength;
    float band = smoothstep(0.30, 0.75, vUv.y);
    sky = mix(sky, vec3(0.062, 0.078, 0.125), clouds * band);

    fragColor = vec4(sky, 1.0);
}
