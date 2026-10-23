#version 300 es
precision highp float;

in vec2 vUv;

uniform float uTime;
uniform vec2 uResolution;
uniform vec2 uSkyOffset;
uniform vec3 uTopColor;
uniform vec3 uBottomColor;
uniform float uStarDensity;
uniform float uAuroraStrength;   // 极光强度（由光照/粒子参数驱动）
uniform float uCloudStrength;    // 云量（天气）
uniform float uLakeLine;         // 湖平线所在的 vUv.y，湖面以下不画极光

out vec4 fragColor;

// Dave Hoskins 的 hash：比 sin(dot) 版本分布更均匀，减少高倍频下的重复方块。
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// 五次插值（quintic）替代 smoothstep：二阶连续，消除噪声网格的块状棱角。
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
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

/** 星点：网格抽样 + 随机亮度，靠近地平线的星星被大气削弱。 */
float stars(vec2 uv, float density) {
    vec2 grid = uv * vec2(90.0, 160.0);
    vec2 cell = floor(grid);
    vec2 frac = fract(grid);
    float rnd = hash(cell);
    if (rnd > density * 0.22) return 0.0;
    vec2 center = vec2(hash(cell + 1.7), hash(cell + 4.3));
    float d = distance(frac, center);
    float twinkle = 0.55 + 0.45 * sin(uTime * 1.6 + rnd * 40.0);
    return smoothstep(0.16, 0.0, d) * twinkle;
}

/**
 * 极光帘幕：低频噪声决定帘幕的水平起伏位置，
 * 高频噪声切成竖向射线，整体随时间缓慢横向流动。
 */
vec3 aurora(vec2 uv, float speed, float phase, float yBase, float width) {
    float t = uTime * speed + phase;
    float center = yBase + width * (fbm(vec2(uv.x * 1.7 + t, 0.7)) - 0.5);
    float d = uv.y - center;
    // 帘幕下缘拖尾更长（真实极光的帘幕从峰顶向下垂落）
    float body = exp(-abs(d) * 3.2) * smoothstep(-0.34, -0.02, d);
    // 必须在雪山脊线之前淡出：帘幕原来的下缘正好切在水平线上，
    // 可见部分就终止在脊线上，看上去像流光"长"在山顶
    body *= smoothstep(uLakeLine + 0.17, uLakeLine + 0.33, uv.y);
    // 射线用多频正弦叠加：正弦无限平滑，不会出现噪声网格的方块；
    // 相位再被低频噪声扭曲，得到自然的弯曲与疏密变化
    float warp = (fbm(vec2(uv.x * 2.2 + t * 0.6, uv.y * 1.1 - t * 0.3)) - 0.5) * 1.6;
    float rays = sin(uv.x * 30.0 + warp + t * 0.8) * 0.5 + 0.5;
    rays *= sin(uv.x * 71.0 + warp * 1.7 + t * 1.4) * 0.30 + 0.70;
    rays *= sin(uv.x * 12.0 - t * 0.7) * 0.28 + 0.72;
    rays = pow(clamp(rays, 0.0, 1.0), 2.2);
    // 横向包络：帘幕有"尽头"，某些区域几乎无极光，露出星空
    float env = 0.30 + 0.85 * fbm(vec2(uv.x * 1.2 - t * 0.7, 2.3));
    float intensity = body * rays * env * 2.1;
    // 底部青绿 → 上部偏紫，是极光的典型色序；下缘拖尾也带一点紫
    vec3 tint = mix(
        vec3(0.16, 1.00, 0.62),
        vec3(0.62, 0.28, 0.95),
        smoothstep(center - 0.28, center + 0.45, uv.y)
    );
    return tint * intensity;
}

void main() {
    vec3 sky = mix(uBottomColor, uTopColor, smoothstep(0.0, 1.0, vUv.y));

    // 星星（地平线附近衰减）
    float starFade = smoothstep(uLakeLine - 0.05, uLakeLine + 0.30, vUv.y);
    sky += vec3(0.85, 0.90, 1.0) * stars(vUv + uSkyOffset, uStarDensity) * starFade * 0.9;

    // 极光：两道帘幕，只在地平线以上；主体抬向天空上部，给星空留出余地
    vec3 a = aurora(vUv, 0.055, 0.0, uLakeLine + 0.34, 0.30);
    vec3 b = aurora(vUv, 0.038, 3.7, uLakeLine + 0.52, 0.24) * 0.7;
    float aboveLake = smoothstep(uLakeLine - 0.02, uLakeLine + 0.12, vUv.y);
    sky += (a + b) * uAuroraStrength * aboveLake;

    // 云：极光下的薄云，靠近地平线更明显
    float clouds = fbm(vec2(vUv.x * 2.6 + uTime * 0.012, vUv.y * 4.4));
    clouds = smoothstep(0.52, 0.90, clouds) * uCloudStrength * 0.35;
    sky = mix(sky, mix(uBottomColor, vec3(0.30, 0.36, 0.52), 0.5), clouds);

    // 地平线附近一层冷雾，让山脚与天空衔接
    float haze = exp(-max(vUv.y - uLakeLine, 0.0) * 7.0);
    sky = mix(sky, uBottomColor * 1.25, haze * 0.22);

    fragColor = vec4(min(sky, vec3(1.0)), 1.0);
}
