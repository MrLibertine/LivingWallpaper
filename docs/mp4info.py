# -*- coding: utf-8 -*-
"""读取 mp4 的时长与分辨率（本机没有 ffprobe，直接解析 mvhd / tkhd）。

用法：python docs/mp4info.py docs/demo/demo.mp4
"""
import struct
import sys

data = open(sys.argv[1], "rb").read()

p = data.find(b"mvhd") + 4
version = data[p]
if version == 1:
    timescale = struct.unpack(">I", data[p + 20:p + 24])[0]
    duration = struct.unpack(">Q", data[p + 24:p + 32])[0]
else:
    timescale = struct.unpack(">I", data[p + 12:p + 16])[0]
    duration = struct.unpack(">I", data[p + 16:p + 20])[0]
print("duration = %.2f s" % (duration / float(timescale)))

t = data.find(b"tkhd") + 4
v = data[t]
base = t + (92 if v == 1 else 76)
w = struct.unpack(">I", data[base:base + 4])[0] >> 16
h = struct.unpack(">I", data[base + 4:base + 8])[0] >> 16
print("size = %dx%d" % (w, h))
print("bytes = %d" % len(data))
