# -*- coding: utf-8 -*-
"""录制 60–90 秒演示视频（对应计划书 6.1 的分镜）。

流程：首页预览 → 三场景切换 → 参数调整 → 自然语言指令 → 设置为系统壁纸
      → 回到桌面看壁纸 → 性能面板。

录屏用 adb shell screenrecord，脚本通过 input tap / swipe 驱动 UI，
不依赖人工操作，可重复执行。

用法：python docs/record_demo.py
输出：docs/demo/demo.mp4
"""
import os
import re
import subprocess
import time

import paths

ADB = paths.ADB
PKG = "com.livingwallpaper.app"
OUT_DIR = os.path.join(paths.DOCS, "demo")
DEVICE_FILE = "/sdcard/demo.mp4"
DUMP = os.path.join(paths.DOCS, "_dump_demo.xml")

# 坐标由 docs/ui_find.py 实时 dump 得到，改 UI 后必须重新核对
TAB_PREVIEW = (99, 2337)
TAB_SCENES = (320, 2337)
TAB_PARAMS = (540, 2337)
TAB_AI = (760, 2337)
TAB_PERF = (980, 2337)

SCENE_FOREST = (540, 510)
SCENE_AURORA = (540, 720)
SCENE_STAR = (540, 915)

WEATHER_RAIN = (288, 609)
WIND_SLIDER_Y = 1587
AI_CHIP_SKY = (276, 1212)
BUTTON_GENERATE = (540, 819)
BUTTON_APPLY = (246, 1710)
BUTTON_SET_WALLPAPER = (540, 1212)
PICKER_APPLY = (540, 2312)


def sh(cmd, timeout=30):
    return subprocess.run(
        [ADB, "shell", cmd], capture_output=True, text=True, timeout=timeout
    ).stdout


def tap(xy, wait=1.0):
    sh("input tap %d %d" % xy)
    time.sleep(wait)


def swipe(x1, y1, x2, y2, ms=400, wait=1.0):
    sh("input swipe %d %d %d %d %d" % (x1, y1, x2, y2, ms))
    time.sleep(wait)


def top_activity():
    return sh("dumpsys activity activities | grep -i topResumedActivity")


def nodes():
    sh("uiautomator dump /sdcard/_d.xml")
    subprocess.run([ADB, "pull", "/sdcard/_d.xml", DUMP], capture_output=True)
    try:
        with open(DUMP, encoding="utf-8", errors="ignore") as f:
            content = f.read()
    except OSError:
        return []
    pat = re.compile(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', re.S)
    out = []
    for m in pat.finditer(content):
        t = m.group(1)
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        if t.strip():
            out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def find(target):
    """精确匹配文本，返回点击坐标。"""
    for label, cx, cy in nodes():
        if label.strip() == target:
            return (cx, cy)
    return None


def find_contains(part):
    """子串匹配（列表项文案通常是"灵境动态壁纸"，精确匹配会找不到）。"""
    for label, cx, cy in nodes():
        if part in label:
            return (cx, cy)
    return None


SHOT_DIR = paths.SHOTS


def shot(name):
    """录屏过程中同步留一张静态图，用于事后核对视频内容。"""
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    path = os.path.join(SHOT_DIR, "demo-%s.png" % name)
    with open(path, "wb") as f:
        f.write(out.stdout)
    return path


def is_black(path, threshold=0.02):
    """判断画面是否几乎全黑（系统预览页偶发解析不出壁纸时会这样）。"""
    try:
        from PIL import Image
    except ImportError:
        return False
    import numpy as np
    im = Image.open(path).convert("RGB")
    return float(np.asarray(im).astype(float).mean() / 255.0) < threshold


def open_picker():
    """打开系统壁纸预览。

    应用内的「设置为壁纸」走 ACTION_CHANGE_LIVE_WALLPAPER，
    在本机 HyperOS 上偶发解析不出壁纸（预览页全黑），此时退回
    系统壁纸列表路径（与从「设置 → 壁纸」进入等价）。
    """
    # 直接走系统壁纸列表：应用内的 ACTION_CHANGE_LIVE_WALLPAPER 入口
    # 在本机 HyperOS 上偶发解析不出壁纸（预览全黑，见 docs/performance.md 已知问题），
    # 列表入口稳定。
    sh("am start -S -n com.android.wallpaper.livepicker/.LiveWallpaperActivity")
    time.sleep(5)
    item = find_contains("灵境")
    if item:
        tap(item, wait=6.0)
    path = shot("6-picker")
    return "LiveWallpaperPreview" in top_activity() and not is_black(path)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    sh("am start -S -n %s/.MainActivity" % PKG)
    time.sleep(5)

    # 开始录屏（后台运行，--time-limit 兜底）
    rec = subprocess.Popen(
        [ADB, "shell", "screenrecord", "--time-limit", "150",
         "--bit-rate", "8000000", DEVICE_FILE],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    time.sleep(2)

    # 1) 首页预览
    tap(TAB_PREVIEW)
    time.sleep(8)
    shot("1-preview")

    # 2) 三场景切换（最后停在极光雪山湖）
    tap(TAB_SCENES)
    for card in (SCENE_FOREST, SCENE_STAR, SCENE_AURORA):
        tap(card, wait=2.6)
    shot("2-scenes")

    # 3) 参数：切雨天 + 拉风速（Tab 切换有动画，等稳再点）
    tap(TAB_PARAMS)
    time.sleep(2.0)
    rain = find("雨") or find_contains("雨") or WEATHER_RAIN
    tap(rain, wait=2.0)
    swipe(540, WIND_SLIDER_Y, 820, WIND_SLIDER_Y)
    time.sleep(2.5)
    shot("3-params")

    # 4) 自然语言指令
    tap(TAB_AI)
    tap(AI_CHIP_SKY, wait=1.0)
    tap(BUTTON_GENERATE, wait=2.0)
    shot("4-ai")
    tap(BUTTON_APPLY, wait=2.0)

    # 5) 回预览看效果
    tap(TAB_PREVIEW)
    time.sleep(7)
    shot("5-preview-after")

    # 6) 设置为系统壁纸
    if open_picker():
        tap(PICKER_APPLY, wait=2.5)         # 触发范围选择
        home = find("主屏幕")
        if home:
            tap(home, wait=2.0)
        confirm = find("设置壁纸")
        if confirm:
            tap(confirm, wait=5.0)
    else:
        print("!! 未能进入可用的壁纸预览")

    # 7) 桌面看壁纸
    sh("input keyevent KEYCODE_HOME")
    time.sleep(6)
    shot("7-home")

    # 8) 性能面板
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(4)
    tap(TAB_PERF)
    time.sleep(7)
    shot("8-perf")

    # 结束录屏并取回
    sh("pkill -INT screenrecord")
    time.sleep(3)
    rec.poll()
    path = os.path.join(OUT_DIR, "demo.mp4")
    subprocess.run([ADB, "pull", DEVICE_FILE, path], capture_output=True)
    size = os.path.getsize(path) if os.path.exists(path) else 0
    print("saved", path, size)


if __name__ == "__main__":
    main()
