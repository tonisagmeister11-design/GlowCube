"""Bounded tone generation and playback.

Tones are synthesised in memory (no audio files are shipped or loaded). Length
and volume are hard-capped, every tone fades in/out, nothing loops, and a global
mute switch overrides everything. Playback uses ``winsound`` on Windows; other
platforms use a no-op backend (tests inject their own).
"""
from __future__ import annotations

import io
import logging
import math
import struct
import sys
import wave
from typing import Protocol

from .catalog import MAX_TONE_MILLISECONDS, MAX_VOLUME_PERCENT

logger = logging.getLogger("makeprank")

SAMPLE_RATE = 22050
FADE_SECONDS = 0.02
MAX_AMPLITUDE = 0.35  # of full scale, at 100 % of the already capped volume

# tone name -> list of (frequency Hz, share of total length)
TONES: dict[str, list[tuple[float, float]]] = {
    "beep": [(880.0, 1.0)],
    "asterisk": [(660.0, 0.5), (880.0, 0.5)],
    "exclamation": [(523.0, 0.35), (392.0, 0.65)],
    "hand": [(300.0, 0.6), (220.0, 0.4)],
    "tv_tone": [(1000.0, 1.0)],
}


def clamp_tone(milliseconds: int, volume_percent: int) -> tuple[int, int]:
    return (max(50, min(int(milliseconds), MAX_TONE_MILLISECONDS)),
            max(1, min(int(volume_percent), MAX_VOLUME_PERCENT)))


def make_tone_wav(tone: str, milliseconds: int, volume_percent: int) -> bytes:
    """Return a mono 16-bit WAV for ``tone`` with enforced length/volume caps."""
    if tone not in TONES:
        raise ValueError(f"Unbekannter Ton {tone!r}")
    milliseconds, volume_percent = clamp_tone(milliseconds, volume_percent)
    total = int(SAMPLE_RATE * milliseconds / 1000)
    amplitude = MAX_AMPLITUDE * (volume_percent / 100.0) * 32767
    fade = int(SAMPLE_RATE * FADE_SECONDS)
    samples = bytearray()
    index = 0
    segments = TONES[tone]
    for position, (freq, share) in enumerate(segments):
        count = total - index if position == len(segments) - 1 else int(total * share)
        for n in range(count):
            gain = 1.0
            if index < fade:
                gain = index / fade
            elif index > total - fade:
                gain = max(0.0, (total - index) / fade)
            value = int(amplitude * gain * math.sin(2 * math.pi * freq * n / SAMPLE_RATE))
            samples += struct.pack("<h", value)
            index += 1
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(bytes(samples))
    return buffer.getvalue()


class AudioBackend(Protocol):
    def play(self, wav: bytes) -> None: ...
    def stop(self) -> None: ...


class NullBackend:
    """Used where no audio output exists. Records calls for tests and diagnostics."""

    def __init__(self) -> None:
        self.played: list[bytes] = []
        self.stops = 0

    def play(self, wav: bytes) -> None:
        self.played.append(wav)

    def stop(self) -> None:
        self.stops += 1


class WinsoundBackend:
    """Windows-only. Plays from memory, asynchronously, once; ``stop`` purges it."""

    def __init__(self) -> None:
        import winsound  # type: ignore[import-not-found]
        self._winsound = winsound

    def play(self, wav: bytes) -> None:
        self._winsound.PlaySound(wav, self._winsound.SND_MEMORY | self._winsound.SND_ASYNC)

    def stop(self) -> None:
        self._winsound.PlaySound(None, self._winsound.SND_PURGE)


def default_backend() -> AudioBackend:
    if sys.platform == "win32":
        try:
            return WinsoundBackend()
        except ImportError:
            logger.warning("winsound nicht verfügbar – Audio deaktiviert")
    return NullBackend()


class AudioPlayer:
    def __init__(self, backend: AudioBackend | None = None, muted: bool = False):
        self.backend = backend or default_backend()
        self.muted = muted
        self.playing = False

    def play_tone(self, tone: str, milliseconds: int, volume_percent: int) -> bool:
        """Play a tone once. Returns False when muted."""
        if self.muted:
            return False
        self.backend.play(make_tone_wav(tone, milliseconds, volume_percent))
        self.playing = True
        return True

    def stop(self) -> None:
        if self.playing:
            self.backend.stop()
            self.playing = False
