"""诊断湖面区域：颜色、条纹周期性、与天空的对比。"""
from PIL import Image
import numpy as np

import sys

import os
import paths

P = sys.argv[1] if len(sys.argv) > 1 else os.path.join(paths.SHOTS, "preview-latest.png")
im = Image.open(P).convert("RGB")
w, h = im.size
a = np.asarray(im).astype(np.float32) / 255.0


def stats(y0, y1, label):
    reg = a[int(h * y0):int(h * y1), :]
    med = np.median(reg.reshape(-1, 3), axis=0)
    lum = reg @ np.array([0.2126, 0.7152, 0.0722])

    # 桌面截图里有图标/搜索条等高亮 UI，用逐行中位数替代均值，抗污染
    rowlum = np.median(lum, axis=1)
    rowdiff = np.abs(np.diff(rowlum))
    collum = np.median(lum, axis=0)
    coldiff = np.abs(np.diff(collum))
    lum_med = float(np.median(lum))

    print(f"{label}: median=({med[0]:.3f},{med[1]:.3f},{med[2]:.3f}) lum={lum_med:.3f}")
    print(f"    相邻行亮度差 mean={rowdiff.mean():.4f} max={rowdiff.max():.4f}  (横向条纹)")
    print(f"    相邻列亮度差 mean={coldiff.mean():.4f}  (竖向条带/倒影射线)")

    s = rowlum - rowlum.mean()
    if len(s) > 8:
        ac = np.correlate(s, s, mode="full")[len(s) - 1:]
        ac /= (ac[0] + 1e-9)
        peaks = [
            i for i in range(4, min(60, len(ac) - 1))
            if ac[i] > ac[i - 1] and ac[i] > ac[i + 1] and ac[i] > 0.25
        ]
        print(f"    条纹周期候选(px): {peaks[:6]}")
    return med, lum_med


print("== 湖面诊断 ==")
lake, lum_lake = stats(0.50, 0.90, "湖面")
sky, lum_sky = stats(0.15, 0.42, "天空")

print(f"\n湖面/天空亮度比 = {lum_lake / max(lum_sky, 1e-6):.2f}  (真实湖面通常 0.3-0.7)")
print(f"湖面色偏: R-B={lake[0] - lake[2]:+.3f} G-B={lake[1] - lake[2]:+.3f}")
print(f"天空色偏: R-B={sky[0] - sky[2]:+.3f} G-B={sky[1] - sky[2]:+.3f}")
