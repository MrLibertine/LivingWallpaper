"""息屏再亮屏，截一张锁屏图（没有桌面图标，便于做像素度量）。

桌面截图会被一排应用图标污染，湖面区域的中位数会被白色图标顶高，
之前据此得到的"湖面比天空还亮"其实是误判。
"""
import subprocess
import sys
import time

import os
import paths

ADB = paths.ADB
OUT = os.path.join(paths.SHOTS, "lock-latest.png")


def sh(cmd):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True).stdout


def main():
    off = float(sys.argv[1]) if len(sys.argv) > 1 else 6.0
    sh("input keyevent KEYCODE_POWER")
    time.sleep(off)
    sh("input keyevent KEYCODE_POWER")
    time.sleep(2.5)
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    if len(out.stdout) < 5000:
        print("!! 截图失败，长度", len(out.stdout))
        sys.exit(1)
    with open(OUT, "wb") as f:
        f.write(out.stdout)
    print("saved", OUT, len(out.stdout))


if __name__ == "__main__":
    main()
