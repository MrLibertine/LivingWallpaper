# -*- coding: utf-8 -*-
"""docs/ 下脚本共用的路径常量。

不用把本机绝对路径散落在每个脚本里：默认值按仓库位置推导，
换机器时用环境变量覆盖即可。

  LW_REPO   仓库根目录（默认按本文件位置推导）
  ADB       adb 可执行文件路径
"""
import os

REPO_ROOT = os.environ.get("LW_REPO") or os.path.dirname(
    os.path.dirname(os.path.abspath(__file__))
)
DOCS = os.path.join(REPO_ROOT, "docs")
SHOTS = os.path.join(DOCS, "shots")
APK = os.path.join(REPO_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
GRADLEW = os.path.join(REPO_ROOT, "gradlew.bat" if os.name == "nt" else "gradlew")

ADB = os.environ.get("ADB") or os.path.join(
    os.environ.get("ANDROID_HOME", r"D:\Android\Sdk"),
    "platform-tools",
    "adb.exe" if os.name == "nt" else "adb",
)
