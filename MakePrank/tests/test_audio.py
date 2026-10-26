import io
import wave

import pytest

from makeprank.audio import MAX_AMPLITUDE, AudioPlayer, NullBackend, clamp_tone, make_tone_wav
from makeprank.catalog import MAX_TONE_MILLISECONDS, MAX_VOLUME_PERCENT


def read(wav):
    with wave.open(io.BytesIO(wav)) as handle:
        return handle.getnframes() / handle.getframerate(), handle.readframes(handle.getnframes())


@pytest.mark.parametrize("tone", ["beep", "asterisk", "exclamation", "hand", "tv_tone"])
def test_tone_length_is_capped(tone):
    seconds, _ = read(make_tone_wav(tone, 999999, 100))
    assert seconds <= MAX_TONE_MILLISECONDS / 1000 + 0.01


def test_volume_is_capped():
    import struct
    _, frames = read(make_tone_wav("tv_tone", 500, 100))
    peak = max(abs(v) for (v,) in struct.iter_unpack("<h", frames))
    assert peak <= MAX_AMPLITUDE * (MAX_VOLUME_PERCENT / 100) * 32767 + 2


def test_unknown_tone_rejected():
    with pytest.raises(ValueError):
        make_tone_wav("sirene", 500, 10)


def test_clamp_and_mute_and_stop():
    assert clamp_tone(1, 0) == (50, 1)
    backend = NullBackend()
    player = AudioPlayer(backend, muted=True)
    assert player.play_tone("beep", 500, 10) is False and backend.played == []
    player.muted = False
    assert player.play_tone("beep", 500, 10) is True
    player.stop()
    player.stop()
    assert backend.stops == 1
