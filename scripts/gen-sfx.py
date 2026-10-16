#!/usr/bin/env python3
"""SFX KARTRID — synthesised sendiri (bebas lisensi, no sample Nintendo).

Tujuan: bunyi 'kartrid masuk slot' ala console modern: transient klik keras
+ thunk mekanik rendah + latch kedua. Semua dari noise + sine, jadi 100% milik sendiri.

Output:
  assets/sfx/insert.ogg   (buat app: 160 ms)
  assets/sfx/eject.ogg
  assets/sfx/tick.ogg     (navigasi D-pad, pelan)
  assets/sfx/demo.mp3     (insert - jeda - eject, buat preview di chat)
"""
import os
import subprocess
import wave

import numpy as np

SR = 48000
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "assets", "sfx")
os.makedirs(OUT, exist_ok=True)
RNG = np.random.default_rng(7)


def env(n, tau):
    return np.exp(-np.arange(n) / (SR * tau))


def click(dur=0.009, tau=0.0016, amp=1.0, seed=1):
    """Transient plastik: noise di-highpass (first difference) lalu dilowpass ringan."""
    rng = np.random.default_rng(seed)
    n = int(SR * dur)
    x = rng.normal(0, 1, n)
    x = np.diff(np.concatenate([[0.0], x]))          # high-pass
    x = np.convolve(x, np.ones(3) / 3, mode="same")  # low-pass (halus)
    return amp * x * env(n, tau)


def thunk(f=95.0, dur=0.09, tau=0.022, amp=0.6, drop=0.55):
    """Badan mekanik: sine rendah dengan pitch turun cepat."""
    n = int(SR * dur)
    t = np.arange(n) / SR
    f_t = f * (1 + drop * np.exp(-t / 0.005))
    x = np.sin(2 * np.pi * np.cumsum(f_t) / SR)
    return amp * x * env(n, tau)


def place(buf, seg, at):
    i = int(SR * at)
    b = buf
    if i + len(seg) > len(b):
        b = np.concatenate([b, np.zeros(i + len(seg) - len(b))])
    b[i:i + len(seg)] += seg
    return b


def build(events, total):
    buf = np.zeros(int(SR * total))
    for at, seg in events:
        buf = place(buf, seg, at)
    # DC-block + normalisasi lembut
    buf = buf - np.mean(buf)
    buf = np.tanh(buf * 0.9)
    peak = np.max(np.abs(buf)) or 1.0
    return (buf / peak * 0.92 * 32767).astype(np.int16)


def write_wav(path, data):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def ogg(wav, out, quality="5"):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", quality, out], check=True)


# --- insert: click keras -> thunk -> latch kedua ---
insert = build([
    (0.000, click(0.010, 0.0015, 1.00, seed=3)),
    (0.002, thunk(92, 0.10, 0.024, 0.55)),
    (0.031, click(0.007, 0.0012, 0.52, seed=11)),
    (0.034, thunk(186, 0.05, 0.014, 0.26)),
], 0.170)

# --- eject: latch lepas -> spring lebih ringan ---
eject = build([
    (0.000, click(0.007, 0.0013, 0.62, seed=5)),
    (0.012, thunk(132, 0.06, 0.016, 0.30)),
    (0.055, click(0.009, 0.0016, 0.80, seed=8)),
    (0.058, thunk(104, 0.08, 0.020, 0.48)),
], 0.170)

# --- tick: navigasi kartrid (pelan, buat D-pad) ---
tick = build([
    (0.000, click(0.005, 0.0010, 0.34, seed=13)),
    (0.001, thunk(255, 0.025, 0.008, 0.16)),
], 0.045)

for name, data in (("insert", insert), ("eject", eject), ("tick", tick)):
    w = os.path.join(OUT, f"{name}.wav")
    write_wav(w, data)
    ogg(w, os.path.join(OUT, f"{name}.ogg"))
    os.remove(w)

# demo buat didengerin di chat: insert - 0.9s - eject
sil = np.zeros(int(SR * 0.9), dtype=np.int16)
demo = np.concatenate([insert, sil, eject])
dw = os.path.join(OUT, "_demo.wav")
write_wav(dw, demo)
subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", dw, "-c:a", "libmp3lame", "-q:a", "4",
                os.path.join(OUT, "demo.mp3")], check=True)
os.remove(dw)

for f in sorted(os.listdir(OUT)):
    if f.startswith("_"):
        continue
    p = os.path.join(OUT, f)
    print(f"{f:12s} {os.path.getsize(p)/1024:7.1f} KB")
