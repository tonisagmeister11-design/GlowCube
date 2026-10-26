"""Regenerate the example packages in ../examples (run from the repo: python tools/make_examples.py)."""
from __future__ import annotations

import struct
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from makeprank.catalog import EFFECTS, default_action  # noqa: E402
from makeprank.package_format import new_config, save_package  # noqa: E402


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

    everything = new_config("Alle Effekte")
    everything["description"] = "Jeder Effekt einmal, je 4 Sekunden. Enthält Audio (kurz, leise) und ein mitgeliefertes Bild."
    everything["creator_label"] = "MakePrank Beispiel"
    everything["network"] = "pairing"
    everything["actions"] = []
    for kind in EFFECTS:
        action = default_action(kind)
        action["duration_seconds"] = 4
        if kind == "screamer":
            action["params"]["image"] = "resource:schachbrett.png"
        everything["actions"].append(action)
    everything["max_duration_seconds"] = 300
    save_package(out / "AlleEffekte.mpk", everything, {"schachbrett.png": tiny_png()})
    print("Beispiele geschrieben nach", out)


if __name__ == "__main__":
    main()
