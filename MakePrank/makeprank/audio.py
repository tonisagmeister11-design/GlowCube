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


MAX_CLIP_SECONDS = 10.0
MIN_FRAMERATE = 8000
MAX_FRAMERATE = 48000


def inspect_wav(name: str, data: bytes) -> tuple[float, int, int]:
    """Validate an imported WAV clip and return ``(seconds, channels, framerate)``.

    Accepts only uncompressed PCM (8/16-bit, mono/stereo) within a sane sample-rate
    and length range. Raises :class:`ValueError` otherwise. No decoding library is
    used; Python's own ``wave`` reader parses the header.
    """
    try:
        with wave.open(io.BytesIO(data), "rb") as handle:
            channels = handle.getnchannels()
            width = handle.getsampwidth()
            framerate = handle.getframerate()
            frames = handle.getnframes()
            compression = handle.getcomptype()
    except (wave.Error, EOFError, OSError, struct.error) as exc:
        raise ValueError(f"{name}: keine gültige WAV-Datei ({exc}).") from exc
    if compression != "NONE":
        raise ValueError(f"{name}: nur unkomprimiertes PCM-WAV ist erlaubt.")
    if channels not in (1, 2) or width not in (1, 2):
        raise ValueError(f"{name}: nur 8/16-Bit Mono oder Stereo.")
    if not MIN_FRAMERATE <= framerate <= MAX_FRAMERATE:
        raise ValueError(f"{name}: Abtastrate {framerate} Hz außerhalb von {MIN_FRAMERATE}–{MAX_FRAMERATE}.")
    seconds = frames / framerate if framerate else 0.0
    if not 0 < seconds <= MAX_CLIP_SECONDS:
        raise ValueError(f"{name}: Länge {seconds:.1f} s außerhalb von 0–{MAX_CLIP_SECONDS:.0f} s.")
    return seconds, channels, framerate


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

    def play_clip(self, wav: bytes) -> bool:
        """Play an imported WAV clip once (no looping). Returns False when muted."""
        if self.muted:
            return False
        self.backend.play(wav)
        self.playing = True
        return True

    def stop(self) -> None:
        if self.playing:
            self.backend.stop()
            self.playing = False
