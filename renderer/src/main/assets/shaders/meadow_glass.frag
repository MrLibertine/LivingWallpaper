#version 300 es
/*
 * 雨玻璃 · 竖玻璃上往下爬的大水滴
 * ---------------------------------------------------------------------------
 * 物理前提：玻璃是竖着的，重力沿玻璃面往下。所以：
 *
 *   1) 形状不是圆珠，是"上尖下圆"的泪滴 —— 底部被重力撑圆，往上收成尾巴。
 *      俯视水平面才是圆的，圆形一滴上去就露馅。
 *   2) 不会匀速滑。表面张力先把它钉住，攒够重量再猛地窜一段：
 *      位移由速度曲线 speed = 1 - A·cos(2πkτ) 积分而来（A ≤ 1 保证不倒车）。
 *   3) 身后拖一条水膜：越靠上（离开水头越久）越窄越淡。
 *   4) 新雨点不会溅同心涟漪（那是水平面的波动），所以这里没有环。
 *
 * 画面主体是照片：只放几颗大水珠往下爬，其余就是原图。
 *
 * 单位约定：
 *   uv 空间 —— 0 在顶部、往下增（main 里已把 vUv 翻转）
 *   横竖都用 uv 单位：uv.x、uv.y 覆盖整屏，1.0 = 屏幕宽/高。
 *   所以横向距离不用再乘 aspect —— 一乘就把屏幕宽度压成 0.45，
 *   列数、水珠间距全都会缩水（水滴变少就是这么来的）。
 *   水珠半径 r 也用 uv 单位，但竖向是主导：泪滴的 y 归一化按 r 算。
 */
precision highp float;

in vec2 vUv;
out vec4 fragColor;

uniform vec2 uResolution;
uniform float uTime;
uniform vec2 uOffset;
uniform sampler2D uBase;
uniform vec2 uBaseSize;
uniform float uLight;
uniform float uRain;
uniform float uWind;

/* ---------------------------------------------------------------- 噪声 */

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

/* ------------------------------------------------------------ 底图取样 */

vec3 photo(vec2 uv, float lod) {
    float sa = uResolution.x / max(uResolution.y, 1.0);
    float ia = uBaseSize.x / max(uBaseSize.y, 1.0);
    vec2 s = ia > sa ? vec2(sa / ia, 1.0) : vec2(1.0, ia / sa);
    vec2 t = (uv - 0.5) * s + 0.5;
    return textureLod(uBase, clamp(t, vec2(0.0005), vec2(0.9995)), lod).rgb;
}

/* 泪滴的归一化半径：d = 相对水头中心的位移（uv 单位，y 向下为正），r = 底部半径。
 * q < 1 在形状内部，q = 1 正好是轮廓。
 *
 * 形状 = "宽度随高度收窄的超椭圆"：底下按圆算（y/r），往上按尾巴长度算，
 * 横向宽度按 up² 平滑收窄。并集拼圆会在轮廓上留下扇贝状锯齿，
 * 这个写法是解析的，轮廓一定光滑。
 */
float tearQ(vec2 d, float r) {
    float tail = 1.60 * r;                       // 尾巴有多长
    float yUp = -d.y;                            // 向上为正
    float w;                                     // 该高度上的半宽
    float yn;                                    // 该方向上的归一化长度
    if (yUp > 0.0) {
        float k = clamp(yUp / tail, 0.0, 1.0);
        w = r * (1.0 - 0.72 * k);                // 往上线性收窄
        yn = yUp / tail;
    } else {
        w = r;                                   // 底部是圆的
        yn = -yUp / r;
    }
    // 注意 d.x 必须带符号：用 abs 等于把左半边削掉
    return sqrt((d.x * d.x) / (w * w) + yn * yn);
}

/* ------------------------------------------------------------- 玻璃层
 * 返回：xy = 折射偏移（uv 单位）、z = 亮部、w = 明暗（负数为压暗）
 */
vec4 glassField(vec2 uv) {
    vec2 warp = vec2(0.0);
    float bright = 0.0;
    float shade = 0.0;

    /* ---- 往下爬的大水滴 ----
     * 每条水道一颗，大部分水道空着 —— 画面要"基本清楚"，水不能铺满。
     * 半径压到 0.018~0.034（大约 40~80 像素），一眼能看出是水珠。
     *
     * 列数直接按 uv.x 切，也就是"屏幕上真正有几列"：
     * 早先按 p.x = uv.x * aspect 切，aspect≈0.45，等于把列数又压掉一半，
     * 4 列只剩 1.8 列落在屏幕里，水滴自然少得可怜。
     * 三层 4 / 6 / 8 列叠着撒点；列越密越容易糊满，所以出现概率逐层压。
     */
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float cols = 4.0 + fi * 2.0;                    // 4 / 6 / 8 列
        float lane = floor(uv.x * cols);
        float fx = fract(uv.x * cols) - 0.5;            // 列内位置 ±0.5
        float dx = abs(fx) / cols;                      // uv 单位的横向距离

        float seed = hash11(lane * 7.3 + fi * 31.7);
        if (seed < 0.42 - 0.10 * uRain + fi * 0.10) continue;   // 稀疏，越细越稀

        // 黏-滑：表面张力先把水钉住，攒够重量再窜一段。
        // 匀速下滑看着像动画，走走停停才像水。
        //
        // 注意"钉住"只能让速度变小，绝不能让位移往回走：
        // 直接往位移里减一个正弦（tau - 0.13·sin）会让速度出现负值，
        // 水珠就会倒着往上爬——雨滴不会这么动。
        // 这里改成对速度曲线 speed = 1 - A·cos(2πk·tau) 积分：
        //   travel = tau - A/(2πk)·sin(2πk·tau)
        // A = 0.88 ≤ 1 保证 speed ≥ 0.12，一次行程里停两次，但只会向下走。
        float rate = 0.045 + 0.040 * hash11(lane * 2.3 + fi);
        float tau = fract(uTime * rate + seed * 7.0);
        float travel = tau - 0.070 * sin(12.566 * tau);
        float headY = travel * 1.40 - 0.22;                // 从上方进、下方出

        // 横向极轻的摆动：水顺着玻璃上最小的不平处走
        float swayX = (valueNoise(vec2(uv.y * 2.6 + seed * 9.0, uTime * 0.15)) - 0.5)
            * (0.004 + 0.010 * uWind);

        float r = (0.018 + 0.016 * hash11(lane + fi * 5.1)) * (1.0 - fi * 0.30);
        vec2 d = vec2(dx - swayX, uv.y - headY);

        // ---- 水头：泪滴 ----
        float q = tearQ(d, r);
        float cov = smoothstep(1.02, 0.90, q);
        if (cov > 0.001) {
            // 凸透镜：珠内是缩小的一张图（取样点跑得比像素快），
            // 再叠一点靠边更狠的挤压，边缘才会把四周的景压成一圈
            vec2 nrm = normalize(d + vec2(1e-5));
            warp += (d * 1.60 + nrm * (q * q) * r * 0.90) * cov;

            // 接触线：水和玻璃交界处光被折走，是一圈细暗线。
            // 少了这一圈，水珠就融进背景，看不出边界
            shade -= cov * 0.30 * smoothstep(0.84, 0.98, q);

            // 体积：光从上面来，穿过水珠后被折到下方。
            // 于是珠内上半压暗、下半提亮 —— 只描一圈轮廓的话，
            // 水珠就是一张线稿，看不出是鼓起来的一团水
            float gy = clamp(0.5 - d.y / (r * 1.1), 0.0, 1.0);   // 上 0 → 下 1
            shade -= cov * 0.20 * (1.0 - gy);
            bright += cov * 0.16 * gy * gy;
            // 暗线外侧再挂一圈极细的亮边：水的表面把天光反射回来，
            // 水珠才从背景里"鼓"出来，而不是被描了个边
            bright += smoothstep(0.90, 1.00, q) * (1.0 - smoothstep(1.00, 1.09, q)) * 0.22;
            // 底部聚光：光从上往下穿过水，在贴近下缘处聚成一道细亮弧
            bright += cov * smoothstep(0.93, 1.0, q) * max(nrm.y, 0.0) * 0.85;
            // 天光在左上角的一点镜面
            float hl = pow(max(dot(nrm, normalize(vec2(-0.38, -0.93))), 0.0), 140.0);
            bright += cov * hl * (0.70 + 0.60 * uLight);
        }

        // ---- 身后那条水膜 ----
        // tt = 0 在水头上，往上增大
        float tt = headY - uv.y;
        float trailLen = 0.30 + 0.30 * seed;
        // 这里不能用 fract：水头拖着膜跑出屏幕后，fract 会把这段膜绕回屏幕另一头，
        // 画面上就出现一条"没有水头的孤儿水痕"。tt < 0 是水头下方，不该有水膜。
        float trail = tt <= 0.0 ? 0.0 : smoothstep(trailLen, 0.0, tt);
        if (trail > 0.001) {
            float k = clamp(tt / trailLen, 0.0, 1.0);
            float wT = r * (0.58 - 0.46 * k);          // 离开水头越久越窄
            float body = smoothstep(wT, wT * 0.35, dx) * trail;

            // 水膜是一条"柱面透镜"：把画面沿着水膜中线往两边推开（放大）。
            // 位移必须和离中线的距离成正比 —— 用 sign() 给常数位移，
            // 中线两侧会各跳一步，画面上就是一条能看见的缝
            float dxSigned = fx / cols;
            warp.x += dxSigned * body * 0.55;
            // 水膜比干玻璃亮一点点，两侧各一道细高光
            bright += body * 0.028;
            bright += smoothstep(wT * 0.70, wT * 1.00, dx)
                    * (1.0 - smoothstep(wT * 1.00, wT * 2.00, dx)) * trail * 0.050;
        }
    }

    return vec4(warp, bright, shade);
}

void main() {
    // vUv.y 在 GL 里 1 是屏幕顶部，翻成"0 在顶部"的图片坐标
    vec2 uv = vec2(vUv.x, 1.0 - vUv.y);
    float aspect = clamp(uResolution.x / max(uResolution.y, 1.0), 0.2, 3.0);

    vec4 gl = glassField(uv);

    // 底图：极慢的手持漂移 + 陀螺仪视差 + 水珠折射。
    // 折射越大说明越在珠子里，那里的照片本来就该更虚
    vec2 drift = vec2(sin(uTime * 0.11) * 0.0016, sin(uTime * 0.077 + 1.3) * 0.0022);
    vec2 bg = uv + drift + uOffset * 0.012 + gl.xy;
    vec3 col = photo(bg, clamp(length(gl.xy) * 26.0, 0.0, 1.2));

    col *= 1.0 + gl.w;
    col += vec3(0.90, 0.95, 1.00) * gl.z;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
