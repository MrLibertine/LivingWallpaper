import sys
from PIL import Image
import numpy as np

path = sys.argv[1]
im = Image.open(path).convert("RGB")
w = 120
im2 = im.resize((w, int(w * im.height / im.width)), Image.LANCZOS)
a = np.asarray(im2).astype(float) / 255.0
h = a.shape[0]
print(f"{path}  {im.size[0]}x{im.size[1]}")
print("avg", a.reshape(-1, 3).mean(axis=0).round(3))
for frac in [0.08, 0.2, 0.35, 0.5, 0.65, 0.8, 0.95]:
    y = min(int(h * frac), h - 1)
    print(f"  y={frac:.2f} {a[y].mean(axis=0).round(3)}")
