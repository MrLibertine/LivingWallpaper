"""打开系统的动态壁纸预览页截一张全屏干净的图。

桌面截图会被图标/搜索条污染，锁屏在这台机器上用的是 MIUI 画报壁纸，
只有 livepicker 的预览页是铺满全屏、无 UI 遮挡的壁纸画面，适合做像素度量。
"""
import re
import subprocess
import sys
import time

import os
import paths

ADB = paths.ADB
DUMP_LOCAL = os.path.join(paths.DOCS, "_dump.xml")
OUT = os.path.join(paths.SHOTS, "preview-latest.png")


def sh(cmd):
    return subprocess.run([ADB, "shell", cmd], capture_output=True, text=True).stdout


def nodes():
    sh("uiautomator dump /sdcard/_d.xml")
    subprocess.run([ADB, "pull", "/sdcard/_d.xml", DUMP_LOCAL], capture_output=True)
    with open(DUMP_LOCAL, encoding="utf-8", errors="ignore") as f:
        content = f.read()
    pat = re.compile(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', re.S)
    out = []
    for m in pat.finditer(content):
        t = m.group(1)
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        if t.strip():
            out.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return out


def top():
    return sh("dumpsys activity activities | grep -i topResumedActivity")


def main():
    sh("am start -S -n com.android.wallpaper.livepicker/.LiveWallpaperActivity")
    time.sleep(6)
    item = None
    for _ in range(6):
        if "LiveWallpaperActivity" not in top():
            sh("am start -S -n com.android.wallpaper.livepicker/.LiveWallpaperActivity")
            time.sleep(5)
        found = [n for n in nodes() if "灵境" in n[0]]
        if found:
            item = found[0]
            break
    if item is None:
        print("!! 找不到条目")
        for n in nodes():
            print("   ", n)
        sys.exit(1)
    for attempt in range(6):
        sh(f"input tap 540 {item[2]}")
        time.sleep(4)
        if "LiveWallpaperPreview" in top():
            break
        # 没进预览：可能又退回列表，重新定位并再点
        found = [n for n in nodes() if "灵境" in n[0]]
        if found:
            item = found[0]
    if "LiveWallpaperPreview" not in top():
        print("!! 未进入预览页：", top())
        sys.exit(1)
    time.sleep(3)
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    with open(OUT, "wb") as f:
        f.write(out.stdout)
    print("saved", OUT, len(out.stdout))


if __name__ == "__main__":
    main()
