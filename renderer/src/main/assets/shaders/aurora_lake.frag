#version 300 es
precision highp float;

in vec2 vNdc;
in vec2 vUv;

uniform float uTime;
uniform float uHorizon;        // 湖平线（NDC y）
uniform vec3 uDeepColor;       // 近处湖水
uniform vec3 uShallowColor;    // 远处湖水
uniform vec3 uHorizonColor;    // 天空地平线色，用于湖天衔接
uniform vec3 uMountainColor;   // 近景雪山色，用于雪山倒影
uniform vec4 uRidge[3];        // 三层山脊：(振幅, 相位1, 相位2, 相位3)
uniform float uRidgeBase;      // 山脊基准高度（NDC y）
uniform float uRidgeShift;     // 山体视差平移，倒影要跟着一起动
uniform float uAuroraStrength;
uniform float uAuroraTintG;    // 极光色调偏移（0=青绿，1=偏紫）
uniform float uWind;

out vec4 fragColor;

// 平面镜：水面 depth 处映到天空的 NDC 高度 = uHorizon + MIRROR * depth
const float MIRROR = 0.84;

// 与 aurora_sky.frag 相同的 hash / 插值（Hoskins hash + quintic）
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

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
    for (int i = 0; i < 3; i++) {
        value += amplitude * noise(p);
        p *= 2.03;
        amplitude *= 0.5;
    }
    return value;
}

/**
 * 天空极光在 (x_uv, y_uv) 处的强度——与 aurora_sky.frag 的形态完全一致，
 * 这样倒影才真的是"天上那道帘幕"的镜像，而不是另一组图案。
 */
float auroraAt(float x, float y, float t, float lakeUv) {
    float center = lakeUv + 0.34 + 0.30 * (fbm(vec2(x * 1.7 + t, 0.7)) - 0.5);
    float d = y - center;
    float body = exp(-abs(d) * 3.2) * smoothstep(-0.34, -0.02, d);
    // 与天空同一道裁剪：只倒映山脊以上的那部分帘幕，
    // 否则水里会多出一截"凭空从山脚长出来"的光柱
    body *= smoothstep(lakeUv + 0.17, lakeUv + 0.33, y);
    float warp = (fbm(vec2(x * 2.2 + t * 0.6, y * 1.1 - t * 0.3)) - 0.5) * 1.6;
    float rays = sin(x * 30.0 + warp + t * 0.8) * 0.5 + 0.5;
    rays *= sin(x * 71.0 + warp * 1.7 + t * 1.4) * 0.30 + 0.70;
    rays *= sin(x * 12.0 - t * 0.7) * 0.28 + 0.72;
    // 水里比天上钝一档：倒影经过水面散射，射线会糊开、不会那么锐。
    // 指数压到 1.4：2.0 时射线太利，和横向波带交叉就是一张方格网
    rays = pow(clamp(rays, 0.0, 1.0), 1.4);
    return body * rays;
}

/** 岸上那排雪山在 x 处的山脊高度——与 CPU 网格同一条曲线。 */
float ridgeAt(float x) {
    float top = -10.0;
    for (int i = 0; i < 3; i++) {
        vec4 r = uRidge[i];
        top = max(top, uRidgeBase + 0.115 * r.x +
            0.075 * r.x * sin(x * 2.1 + r.y) +
            0.045 * r.x * sin(x * 4.7 + r.z) +
            0.022 * r.x * sin(x * 9.3 + r.w));
    }
    return top;
}

void main() {
    // depth：0 在湖尽头（地平线），1 在屏幕底部（最近）
    float depth = clamp((uHorizon - vNdc.y) / max(uHorizon + 1.0, 0.001), 0.0, 1.0);
    float lakeUv = (uHorizon + 1.0) * 0.5;
    float x = vUv.x;
    float t = uTime * 0.055;

    // ---- 波浪 ----
    // 掠射角下的水纹是"细长横向条"，所以噪声要横向拉长、纵向压密。
    // 之前用正弦叠加：等距 → 梯子；等向噪声 → 斑块；拉长噪声才是水纹。
    float rip = noise(vec2(x * 7.0 + uTime * 0.10, depth * 30.0 - uTime * 0.30)) * 0.65;
    rip += noise(vec2(x * 15.0 - uTime * 0.16, depth * 58.0 + uTime * 0.24)) * 0.35;
    // 越近的波纹越大越疏（透视），远处密而细
    float ripScale = mix(0.50, 1.0, depth);
    float crest = smoothstep(0.56, 0.86, rip) * ripScale;
    float trough = smoothstep(0.44, 0.14, rip) * ripScale;

    // 倒影被波纹撕开：以横向位移为主，纵向只加一点点
    float chop = (rip - 0.5) * 0.10 * (0.4 + depth);
    chop += sin(x * 9.0 + depth * 44.0 - uTime * 1.8) * 0.010 * (0.35 + depth);

    // ---- 水体本色 ----
    // 远湖亮、近水暗：这条纵向梯度是"有深度的水"最直接的信号，
    // 也让雪山倒影（暗）能在亮带上切出形状。整体仍比天空暗（天亮水暗）。
    vec3 shallowToDeep = mix(uShallowColor, uDeepColor, smoothstep(0.08, 0.80, depth));
    vec3 color = mix(uHorizonColor * 0.95, shallowToDeep, smoothstep(0.02, 0.16, depth));
    // 波峰受光：暗底上做乘法看不出层次，横向亮带必须加色才读得出来
    color += vec3(0.050, 0.070, 0.098) * crest;
    // 波谷压暗，拉开波峰波谷的对比
    color *= 1.0 - 0.14 * trough;

    // ---- 菲涅尔 ----
    // 远湖是掠射（接近镜面），近处是俯视（几乎只看到水体本色）
    float fres = 0.25 + 0.75 * exp(-depth * 1.0);

    // ---- 雪山倒影 ----
    // 这是"这是水面"最强的一个信号：岸上那排山以湖平线为轴翻下来，
    // 被波纹切碎。之前只有天空倒影，湖面就成了一块没内容的渐变。
    float ridge = ridgeAt(vNdc.x - uRidgeShift);
    float mDepth = (ridge - uHorizon) / MIRROR + chop * 2.2;
    float mtn = smoothstep(mDepth + 0.045, mDepth - 0.015, depth);
    // 与山上本体几乎同色、略暗（水面吸光），山和倒影在湖平线处无缝相连
    vec3 mtnRefl = uMountainColor * 0.75;
    color = mix(color, mtnRefl, clamp(mtn * fres, 0.0, 1.0) * 0.92);

    // ---- 极光倒影 ----
    // 真镜像：水面 depth 处反射天空高度 uHorizon + MIRROR * depth，
    // 于是远湖映的是山与地平线，近处水面映的是高空那道帘幕
    float mirrorY = lakeUv + depth * (MIRROR * 0.5);
    // 采样点横向随波扭动：倒影被水面揉开，不会是一条条笔直的"栅栏"
    float refX = x + chop * 1.4;
    float mirror = auroraAt(refX, mirrorY + chop, t, lakeUv);
    // 第二道帘幕（与天空一致），弱一些
    mirror += auroraAt(refX * 1.03, mirrorY + chop + 0.18, t * 0.69 + 3.7, lakeUv) * 0.5;
    // 大尺度强弱：有的水面泛起一片绿，有的地方几乎全黑，破掉等距感
    mirror *= 0.45 + 0.85 * fbm(vec2(x * 0.9 - t * 0.5, 3.1));
    // 镜面高光：倒影亮处再提亮，形成水面的"光柱"，这是"水"的关键视觉符号
    float specular = mirror * (1.0 + 0.85 * smoothstep(0.45, 0.95, mirror));
    vec3 auroraTint = mix(
        vec3(0.16, 1.00, 0.62),
        vec3(0.62, 0.28, 0.95),
        clamp(uAuroraTintG + depth * 0.25, 0.0, 1.0)
    );
    // 帘幕整体位置的漫射辉光：射线之间也要有一层均匀的绿，
    // 否则倒影只剩几条细线，看上去还是"没内容"
    float curtainY = lakeUv + 0.34 + 0.30 * (fbm(vec2(x * 1.7 + t, 0.7)) - 0.5);
    float glow = exp(-abs(mirrorY + chop - curtainY) * 1.6) * 0.042;
    float skyRefl = (1.0 - clamp(mtn, 0.0, 1.0) * 0.85) * fres;
    // 倒影只能"点睛"，不能盖过水体本色——之前加得太狠，湖面成了
    // 一面比天空还亮的紫光幕布，那正是"不像湖"的直接原因
    color += auroraTint * (specular * 0.55 + glow) * skyRefl * uAuroraStrength;

    // ---- 星点倒影 ----
    // 点状（有距离衰减，避免网格方块），只出现在远湖
    vec2 starGrid = vec2(vNdc.x * 60.0, depth * 60.0);
    vec2 starCell = floor(starGrid);
    vec2 starFrac = fract(starGrid);
    vec2 starCenter = vec2(hash(starCell + 1.7), hash(starCell + 4.3));
    float starD = distance(starFrac, starCenter);
    float star = smoothstep(0.18, 0.02, starD) * step(0.992, hash(starCell + 9.1)) *
        exp(-depth * 4.0) * (1.0 - clamp(mtn, 0.0, 1.0));
    color += vec3(0.85, 0.92, 1.0) * star * 0.7;

    fragColor = vec4(min(color, vec3(1.0)), 1.0);
}
