"""离屏渲染着色器预览图（PC 端，开发期自检用）。

用途：不装 APK 也能看到全屏着色器的画面，便于迭代视觉。
真机行为仍然以设备截图为准（这里用的是桌面 GL 3.3，GLSL ES 3.0 源码做了最小改写）。

用法：
    python docs/shader_preview.py shaders/meadow_glass.frag [输出.png] [宽] [高] [时间秒]
参数异常时默认 1080x2400（与手机一致）。
给不同的时间秒数可以渲染多帧，用来确认"画面真的在动"。

依赖：moderngl、numpy、Pillow（pip install moderngl numpy pillow）。
"""

from __future__ import annotations

import sys
from pathlib import Path

import moderngl
import numpy as np
from PIL import Image

DEFAULT_FRAG = "shaders/meadow_glass.frag"
VERT = """
#version 330 core
layout(location = 0) in vec2 aPosition;
out vec2 vNdc;
out vec2 vUv;
void main() {
    vNdc = aPosition;
    vUv = aPosition * 0.5 + 0.5;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""


def to_desktop_glsl(source: str) -> str:
    """把 GLSL ES 3.0 全屏片元着色器改写成桌面 GL 3.3 可编译的版本。"""
    lines: list[str] = []
    for line in source.splitlines():
        stripped = line.strip()
        if stripped.startswith("#version"):
            lines.append("#version 330 core")
            continue
        # precision 限定符桌面 GLSL 不接受
        if stripped.startswith("precision "):
            continue
        lines.append(line)
    return "\n".join(lines)


def parse_uniforms(source: str) -> dict[str, tuple[str, object]]:
    """从源码里提取 uniform 名与类型，给它们填一组"合理默认值"。"""
    uniforms: dict[str, tuple[str, object]] = {}
    for line in source.splitlines():
        parts = line.strip().rstrip(";").split()
        if len(parts) == 3 and parts[0] == "uniform":
            gl_type, name = parts[1], parts[2]
            uniforms[name] = (gl_type, uniform_value(gl_type))
    return uniforms


# 预览用的默认 uniform 取值（模拟 SceneConfig 的默认档位）
OVERRIDES = {
    "uTime": 12.5,
    "uOffset": (0.0, 0.0),
    "uSkyTop": (0.055, 0.065, 0.150),
    "uSkyHorizon": (0.42, 0.27, 0.26),
    "uGlowColor": (1.0, 0.66, 0.34),
    "uLight": 0.55,
    "uRain": 0.6,
    "uWind": 0.4,
    "uHaze": 0.5,
}


def uniform_value(gl_type: str):
    if gl_type == "float":
        return 0.5
    if gl_type == "vec2":
        return (0.0, 0.0)
    if gl_type == "vec3":
        return (0.5, 0.5, 0.5)
    if gl_type == "vec4":
        return (0.5, 0.5, 0.5, 1.0)
    return 0.0


def find_base_image(frag_path: Path) -> Path | None:
    """定位底图：着色器放在 assets/shaders/ 下，底图固定在 assets/textures/。"""
    guess = frag_path.parent.parent / "textures" / "meadow_bg.jpg"
    if guess.exists():
        return guess
    root = Path(__file__).resolve().parents[1]
    guess = root / "renderer/src/main/assets/textures/meadow_bg.jpg"
    return guess if guess.exists() else None


def run(frag_path: Path, out_path: Path, width: int, height: int, time_s: float) -> None:
    source = to_desktop_glsl(frag_path.read_text(encoding="utf-8"))
    ctx = moderngl.create_standalone_context(require=330)
    program = ctx.program(vertex_shader=VERT, fragment_shader=source)

    for name, (gl_type, value) in parse_uniforms(frag_path.read_text(encoding="utf-8")).items():
        if name not in program:
            continue
        value = OVERRIDES.get(name, value)
        if gl_type == "float":
            program[name].value = float(value)
        elif gl_type == "vec2":
            program[name].value = tuple(value)
        elif gl_type == "vec3":
            program[name].value = tuple(value)
        elif gl_type == "vec4":
            program[name].value = tuple(value)

    # 与 App 一致：分辨率 uniform 用真实像素值
    if "uResolution" in program:
        program["uResolution"].value = (float(width), float(height))
    if "uTime" in program:
        program["uTime"].value = float(time_s)

    # 贴图：着色器里声明了 uBase 就把同目录上层的 textures/meadow_bg.jpg 挂上去，
    # 采样方向与 App 的 GLUtils.texImage2D 一致（图片第一行 = 纹理 v=0）
    base_texture = None
    if "uBase" in program:
        base_path = find_base_image(frag_path)
        if base_path is None:
            raise SystemExit("着色器需要 uBase，但没找到底图 textures/meadow_bg.jpg")
        base_image = Image.open(base_path).convert("RGB")
        base_texture = ctx.texture(base_image.size, 3, base_image.tobytes())
        base_texture.build_mipmaps()
        base_texture.use(0)
        program["uBase"].value = 0
        if "uBaseSize" in program:
            program["uBaseSize"].value = (float(base_image.width), float(base_image.height))

    quad = np.array([-1, -1, 3, -1, -1, 3], dtype="f4")
    vbo = ctx.buffer(quad.tobytes())
    vao = ctx.vertex_array(program, [(vbo, "2f", "aPosition")])

    texture = ctx.texture((width, height), 4)
    framebuffer = ctx.framebuffer(color_attachments=[texture])
    framebuffer.use()
    ctx.clear(0.0, 0.0, 0.0, 1.0)
    vao.render(moderngl.TRIANGLES)

    data = np.frombuffer(framebuffer.read(components=3), dtype=np.uint8)
    image = Image.fromarray(data.reshape((height, width, 3))[::-1])
    out_path.parent.mkdir(parents=True, exist_ok=True)
    image.save(out_path)
    print(f"saved {out_path} ({width}x{height})")


def main(argv: list[str]) -> int:
    root = Path(__file__).resolve().parents[1]
    frag = root / "renderer/src/main/assets" / (argv[1] if len(argv) > 1 else DEFAULT_FRAG)
    out = Path(argv[2]) if len(argv) > 2 else root / "docs/shots/shader-preview.png"
    width = int(argv[3]) if len(argv) > 3 else 1080
    height = int(argv[4]) if len(argv) > 4 else 2400
    time_s = float(argv[5]) if len(argv) > 5 else float(OVERRIDES["uTime"])
    run(frag, out, width, height, time_s)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
