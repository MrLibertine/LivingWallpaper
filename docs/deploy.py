"""部署 APK 并把本应用重设为动态壁纸，然后截图。

HyperOS 上每次 adb install 后壁纸会被系统重置，且 livepicker 列表状态不稳定，
因此这里全部按 UI 文字精确匹配点击，并校验最终 mWallpaperComponent。
"""
import re
import subprocess
import sys
import time

import os
import paths

ADB = paths.ADB
APK = paths.APK
PKG = "com.livingwallpaper.app"
DUMP_LOCAL = os.path.join(paths.DOCS, "_dump.xml")


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True)


def sh(cmd):
    return adb("shell", cmd).stdout


def nodes():
    sh("uiautomator dump /sdcard/_d.xml")
    adb("pull", "/sdcard/_d.xml", DUMP_LOCAL)
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


def find(items, exact=None, contains=None):
    for t, cx, cy in items:
        if exact is not None and t == exact:
            return cx, cy
        if contains is not None and contains in t:
            return cx, cy
    return None


def tap(x, y, wait=1.0):
    sh(f"input tap {x} {y}")
    time.sleep(wait)


def top_activity():
    return sh("dumpsys activity activities | grep -i topResumedActivity")


def current_wallpaper():
    return sh("dumpsys wallpaper | grep mWallpaperComponent")


def reapply():
    """通过 livepicker 列表 → 预览 → 设置壁纸 重新应用（HyperOS 流程）。"""
    sh("am start -S -n com.android.wallpaper.livepicker/.LiveWallpaperActivity")
    time.sleep(6)

    target = find(nodes(), contains="灵境")
    if target is None:
        print("!! 列表里找不到本应用条目，当前可见项：")
        for t, cx, cy in nodes():
            print("   ", t, cx, cy)
        sys.exit(1)
    print(f"    找到条目 @ {target}")
    # 用整行中心：文字节点的 x 中心可能偏左，落在行外则点不中；
    # 该 ROM 上列表跳转偶发失败，重试几次
    for attempt in range(6):
        act = top_activity()
        if "LiveWallpaperActivity" not in act:
            sh("am start -S -n com.android.wallpaper.livepicker/.LiveWallpaperActivity")
            time.sleep(5)
            act = top_activity()
            if "LiveWallpaperActivity" not in act:
                continue
        # 每次重新定位：列表可能重排，旧坐标会失效
        found = find(nodes(), contains="灵境")
        y = found[1] if found else target[1]
        tap(540, y, wait=4)
        if "LiveWallpaperPreview" in top_activity():
            break
    else:
        print("!! 未进入预览页：", top_activity())
        sys.exit(1)

    print("[4] 设置壁纸")
    tap(540, 2312, wait=2)
    home = find(nodes(), exact="主屏幕")
    if home is None:
        print("!! 未出现范围选择：", nodes())
        sys.exit(1)
    tap(*home, wait=1)
    confirm = find(nodes(), exact="设置壁纸")
    if confirm:
        tap(*confirm, wait=3)


def main():
    print("[1] build")
    r = subprocess.run(
        [paths.GRADLEW, "-p", paths.REPO_ROOT,
         ":app:assembleDebug", "--console=plain"],
        capture_output=True, text=True,
    )
    if "BUILD SUCCESSFUL" not in r.stdout:
        print(r.stdout[-3000:])
        sys.exit(1)

    print("[2] install")
    print(adb("install", "-r", APK).stdout.strip())

    # install 覆盖后壁纸不一定被重置：仍是本应用就跳过（livepicker 流程不稳定）
    if PKG in current_wallpaper():
        print("[3] wallpaper still ours, skip re-apply")
    else:
        reapply()

    comp = current_wallpaper()
    print("[5] 当前壁纸：", comp.splitlines()[0] if comp else "?")

    comp = current_wallpaper()
    print("[5] 当前壁纸：", comp.splitlines()[0] if comp else "?")
    if PKG not in comp:
        print("!! 壁纸未切换为本应用")
        sys.exit(1)

    print("[6] 回桌面截图")
    sh("input keyevent KEYCODE_HOME")
    time.sleep(5)
    out = subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True)
    shot = os.path.join(paths.SHOTS, "home-latest.png")
    with open(shot, "wb") as f:
        f.write(out.stdout)
    print("saved", shot)


if __name__ == "__main__":
    main()
