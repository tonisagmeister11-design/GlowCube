"""Regenerate the example packages in ../examples (run from the repo: python tools/make_examples.py).

All media is generated here from scratch (a drawn PNG and a synthesised WAV); no
third-party image or sound files are shipped.
"""
from __future__ import annotations

import io
import math
import struct
import sys
import wave
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from makeprank.catalog import EFFECTS, default_action  # noqa: E402
from makeprank.package_format import new_config, save_package  # noqa: E402


def honk_wav(seconds: float = 0.9, framerate: int = 22050) -> bytes:
    """A short, buzzy two-note 'honk' (sawtooth) – a recognisable prank horn, generated here."""
    frames = int(seconds * framerate)
    fade = int(0.01 * framerate)
    samples = bytearray()
    for n in range(frames):
        freq = 196.0 if n < frames * 0.5 else 262.0  # G then C
        phase = (n % (framerate / freq)) / (framerate / freq)
        saw = 2.0 * phase - 1.0  # -1..1 sawtooth
        gain = 0.45
        if n < fade:
            gain *= n / fade
        elif n > frames - fade:
            gain *= max(0.0, (frames - n) / fade)
        samples += struct.pack("<h", int(gain * 32767 * saw))
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(framerate)
        handle.writeframes(bytes(samples))
    return buffer.getvalue()


def tiny_png(width: int = 96, height: int = 96) -> bytes:
    """A small generated checker image (no external image files are shipped)."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
    rows = b""
    for y in range(height):
        rows += b"\x00" + b"".join(bytes((230, 60, 60) if (x // 12 + y // 12) % 2 else (250, 250, 250)) for x in range(width))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def main() -> None:
    out = ROOT / "examples"
    out.mkdir(exist_ok=True)

    welcome = new_config("Willkommen")
    welcome["description"] = "Kleine, harmlose Demo: Dialog, Toast und ein Testbild."
    welcome["creator_label"] = "MakePrank Beispiel"
    welcome["network"] = "pairing"
    welcome["actions"] = [default_action("toast"), default_action("error_dialog"), default_action("color_bars")]
    welcome["max_duration_seconds"] = 120
    save_package(out / "WelcomeDemo.mpk", welcome)

    # A sound-focused prank: a dialog, then a honk clip, then a toast – audio is the point here.
    sound = new_config("Sound-Prank")
    sound["description"] = "Zeigt einen Dialog und spielt dann einen kurzen Hupton ab. Der Sound ist selbst erzeugt."
    sound["creator_label"] = "MakePrank Beispiel"
    sound["network"] = "pairing"
    dialog = default_action("error_dialog")
    dialog["title"] = "Achtung"
    dialog["text"] = "Gleich passiert etwas …"
    dialog["duration_seconds"] = 3
    clip = default_action("sound_clip")
    clip["title"] = "HUP!"
    clip["text"] = "🔊 HUUUP!"
    clip["params"]["clip"] = "resource:hupe.wav"
    clip["duration_seconds"] = 4
    toast = default_action("toast")
    toast["title"] = "Reingelegt"
    toast["text"] = "War nur ein Scherz 😄"
    sound["actions"] = [dialog, clip, toast]
    sound["max_duration_seconds"] = 60
    save_package(out / "SoundPrank.mpk", sound, {"hupe.wav": honk_wav()})

    everything = new_config("Alle Effekte")
    everything["description"] = "Jeder Effekt einmal, je 4 Sekunden. Enthält Audio (kurz) und ein mitgeliefertes Bild und einen Sound-Clip."
    everything["creator_label"] = "MakePrank Beispiel"
    everything["network"] = "pairing"
    everything["actions"] = []
    for kind in EFFECTS:
        action = default_action(kind)
        action["duration_seconds"] = 4
        if kind == "screamer":
            action["params"]["image"] = "resource:schachbrett.png"
        if kind == "sound_clip":
            action["params"]["clip"] = "resource:hupe.wav"
        everything["actions"].append(action)
    everything["max_duration_seconds"] = 300
    save_package(out / "AlleEffekte.mpk", everything, {"schachbrett.png": tiny_png(), "hupe.wav": honk_wav()})
    print("Beispiele geschrieben nach", out)


if __name__ == "__main__":
    main()
