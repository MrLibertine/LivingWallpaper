# -*- coding: utf-8 -*-
"""截取应用界面（任意页面），用于文档与坐标确认。

用法：python docs/shot_app.py <输出名> [点击序列]
点击序列形如 "972,2175;324,2175"，按顺序点击后再截图。
"""
import os
import subprocess
import sys
import time

import paths

ADB = paths.ADB
OUT_DIR = paths.SHOTS


def sh(cmd):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True).stdout


def main():
    name = sys.argv[1] if len(sys.argv) > 1 else "app"
    taps = sys.argv[2] if len(sys.argv) > 2 else ""
    if taps:
        for pair in taps.split(";"):
            x, y = pair.split(",")
            sh(f"input tap {int(x)} {int(y)}")
            time.sleep(1.2)
        time.sleep(1.5)
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    path = os.path.join(OUT_DIR, name + ".png")
    with open(path, "wb") as f:
        f.write(out.stdout)
    print("saved", path, len(out.stdout))


if __name__ == "__main__":
    main()
