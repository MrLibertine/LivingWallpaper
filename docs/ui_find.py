# -*- coding: utf-8 -*-
"""实时 dump 当前界面并打印所有可见文本的点击坐标。

parse_ui.py 只解析已有 xml 文件，容易读到过期坐标；这个脚本每次都重新 dump。

用法：python docs/ui_find.py [关键字]
"""
import io
import re
import subprocess
import sys

import os
import paths

ADB = paths.ADB
LOCAL = os.path.join(paths.DOCS, "_ui_now.xml")


def main():
    key = sys.argv[1] if len(sys.argv) > 1 else None
    subprocess.run([ADB, "shell", "uiautomator dump /sdcard/_ui_now.xml"],
                   capture_output=True)
    subprocess.run([ADB, "pull", "/sdcard/_ui_now.xml", LOCAL], capture_output=True)
    content = io.open(LOCAL, encoding="utf-8", errors="ignore").read()
    pat = re.compile(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', re.S)
    seen = set()
    for m in pat.finditer(content):
        text = m.group(1)
        if not text.strip():
            continue
        x1, y1, x2, y2 = map(int, m.groups()[1:])
        if key and key not in text:
            continue
        k = (text, x1, y1)
        if k in seen:
            continue
        seen.add(k)
        print("%-30s center=(%d,%d)" % (text, (x1 + x2) // 2, (y1 + y2) // 2))


if __name__ == "__main__":
    main()
