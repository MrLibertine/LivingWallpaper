# -*- coding: utf-8 -*-
"""在真机上测各场景 / 各性能档位的实测帧率与内存。

为什么这样测：性能页与预览页是不同 Tab，切到性能页时预览 Surface 已被销毁、
渲染停止，统计值会"冻住"。正确做法是——先设好场景与档位，回预览页渲染若干秒，
再回性能页读取（读到的就是刚才那段渲染的真实统计）。

用法：python docs/measure_perf.py
输出：docs/shots/perf-<tag>.png 与 docs/perf-raw.txt
"""
import io
import re
import subprocess
import time

import os
import paths

ADB = paths.ADB
SHOTS = paths.SHOTS
RAW = os.path.join(paths.DOCS, "perf-raw.txt")

TAB_PREVIEW = (108, 2280)
TAB_SCENES = (324, 2280)
TAB_PERF = (972, 2280)

SCENES = {"forest": 510, "aurora": 720, "star": 915}
LEVELS = {"eco": 183, "balanced": 381, "high": 561}

RENDER_SECONDS = 8

RUNS = [
    ("forest", "balanced"),
    ("aurora", "balanced"),
    ("star", "balanced"),
    ("aurora", "eco"),
    ("aurora", "high"),
]


def sh(cmd):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True).stdout


def tap(xy):
    sh("input tap %d %d" % xy)
    time.sleep(0.9)


def screenshot(path):
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    with open(path, "wb") as f:
        f.write(out.stdout)


def total_pss():
    txt = sh("dumpsys meminfo com.livingwallpaper.app")
    m = re.search(r"TOTAL\s+(\d+)", txt)
    return int(m.group(1)) if m else -1


def main():
    lines = []
    for scene, level in RUNS:
        tag = "%s-%s" % (scene, level)
        tap(TAB_SCENES)
        tap((540, SCENES[scene]))
        tap(TAB_PERF)
        tap((LEVELS[level], 516))
        tap(TAB_PREVIEW)
        time.sleep(RENDER_SECONDS)
        pss = total_pss()
        tap(TAB_PERF)
        time.sleep(1.5)
        screenshot(SHOTS + "\\perf-" + tag + ".png")
        lines.append("%s\tPSS=%dKB" % (tag, pss))
        print(tag, "PSS", pss)
    io.open(RAW, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    print("done")


if __name__ == "__main__":
    main()
