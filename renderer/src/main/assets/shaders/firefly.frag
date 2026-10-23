#version 300 es
precision highp float;

in float vAlpha;

uniform vec3 uColor;

out vec4 fragColor;

void main() {
    vec2 d = gl_PointCoord - vec2(0.5);
    float r = length(d);
    if (r > 0.5) discard;

    float halo = smoothstep(0.5, 0.0, r);
    float core = smoothstep(0.22, 0.0, r);

    float alpha = vAlpha * halo * halo;
    fragColor = vec4(uColor * (0.65 + 1.35 * core), alpha);
}
