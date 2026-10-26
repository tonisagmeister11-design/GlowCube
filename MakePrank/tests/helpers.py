"""Shared test helpers: a fake host with controllable time and a recording runner."""
from __future__ import annotations

import struct
import zlib
from typing import Any, Callable


class FakeHost:
    """SessionHost whose clock only moves when the test says so."""

    def __init__(self) -> None:
        self.time = 0.0
        self.scheduled: dict[int, Callable[[], None]] = {}
        self._next = 1

    def schedule(self, delay_ms: int, callback: Callable[[], None]) -> int:
        handle = self._next
        self._next += 1
        self.scheduled[handle] = callback
        return handle

    def cancel(self, handle: Any) -> None:
        self.scheduled.pop(handle, None)

    def now(self) -> float:
        return self.time


class RecordingRunner:
    """EffectRunner that records starts and registers a cleanup per action."""

    def __init__(self, fail_on: int | None = None) -> None:
        self.started: list[str] = []
        self.cleaned: list[str] = []
        self.fail_on = fail_on
        self.scopes: list[Any] = []

    def start(self, scope: Any, action: dict[str, Any]) -> None:
        if self.fail_on is not None and len(self.started) == self.fail_on:
            raise RuntimeError("Testfehler")
        self.started.append(action["type"])
        self.scopes.append(scope)
        scope.add_cleanup(lambda t=action["type"]: self.cleaned.append(t))


def make_png(width: int = 8, height: int = 8, color: tuple[int, int, int] = (200, 30, 30)) -> bytes:
    """Build a small valid PNG without any imaging library."""
    def chunk(tag: bytes, data: bytes) -> bytes:
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + bytes(color) * width for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def make_wav(seconds: float = 0.5, framerate: int = 22050, channels: int = 1) -> bytes:
    """Build a small valid 16-bit PCM WAV (a quiet sine) without any audio library."""
    import io
    import math
    import wave

    frames = int(seconds * framerate)
    samples = bytearray()
    for n in range(frames):
        value = int(8000 * math.sin(2 * math.pi * 440 * n / framerate))
        samples += struct.pack("<h", value) * channels
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as handle:
        handle.setnchannels(channels)
        handle.setsampwidth(2)
        handle.setframerate(framerate)
        handle.writeframes(bytes(samples))
    return buffer.getvalue()


def run_for(session: Any, seconds: float, step: float = 0.04) -> None:
    """Advance a session on virtual time."""
    elapsed = 0.0
    while elapsed < seconds and session.active:
        session.tick(step)
        elapsed += step
