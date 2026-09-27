"""Spoken NPC voice lines ("Help me!", "Watch out!", ...) with espeak-ng + MBROLA voices.

Text-to-speech only (no recorded audio). Lines come from Game/data/voice/lines.json; each line
is rendered with a male and a female voice, shouted (faster, higher, compressed), and saved as
Game/Audio/SFX/Voice/line_<category>_<m|f>_<index>.wav. Skipped when espeak-ng is missing.

Usage: python Tools/Audio/generate_voice.py
"""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
GAME = os.path.normpath(os.path.join(HERE, "..", "..", "Game"))
OUT = os.path.join(GAME, "Audio", "SFX", "Voice")
SR = 22050
# (espeak voice, fallback, pitch, speed)
VOICES = {"m": [("mb-us2", "en-us+m3", 58, 172), ("mb-us3", "en-us+m1", 52, 168)],
          "f": [("mb-us1", "en-us+f3", 72, 175)]}


def render(text, voice, fallback, pitch, speed):
    fd, path = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    for v in (voice, fallback):
        r = subprocess.run(["espeak-ng", "-v", v, "-p", str(pitch), "-s", str(speed), "-a", "180", "-w", path, text],
                           capture_output=True)
        if r.returncode == 0 and os.path.getsize(path) > 2000:
            break
    with wave.open(path) as w:
        sr = w.getframerate()
        x = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float64) / 32768.0
    os.remove(path)
    if sr != SR:
        n = int(len(x) * SR / sr)
        x = np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x)
    return x


def shout(x):
    # trim silence, compress, gentle saturation and a short room tail
    a = np.abs(x)
    idx = np.where(a > 0.02)[0]
    if len(idx):
        x = x[max(0, idx[0] - 200): idx[-1] + 400]
    x = x / (np.max(np.abs(x)) or 1.0)
    x = np.tanh(x * 2.2) / np.tanh(2.2)
    ir_n = int(SR * 0.18)
    ir = np.random.default_rng(3).standard_normal(ir_n) * np.exp(-np.arange(ir_n) / SR * 22.0)
    wet = np.convolve(x, ir)[: len(x) + ir_n]
    out = np.zeros(len(wet))
    out[: len(x)] += x
    out += wet / (np.max(np.abs(wet)) or 1.0) * 0.12
    fade = min(len(out), 300)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out / (np.max(np.abs(out)) or 1.0) * 0.9


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def main():
    if shutil.which("espeak-ng") is None:
        print("espeak-ng not found - voice lines skipped")
        return 0
    with open(os.path.join(GAME, "data", "voice", "lines.json")) as fh:
        lines = json.load(fh)
    n = 0
    for cat, texts in lines.items():
        for i, text in enumerate(texts):
            for g, voices in VOICES.items():
                v = voices[i % len(voices)]
                save(f"line_{cat}_{g}_{i}", shout(render(text, *v)))
                n += 1
    print("voice lines:", n)
    return 0


if __name__ == "__main__":
    sys.exit(main())
