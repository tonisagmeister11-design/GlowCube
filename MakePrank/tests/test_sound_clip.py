"""Imported WAV sound clips: validation, package round-trip, and real playback."""
import io
import wave

import pytest

from makeprank.audio import MAX_CLIP_SECONDS, AudioPlayer, NullBackend, inspect_wav
from makeprank.catalog import action_audio_name, action_has_audio, default_action
from makeprank.package_format import (
    MAX_AUDIO_BYTES, PackageError, inspect_resource, load_package_full, new_config, save_package, summarize_config,
    validate_config,
)

from helpers import make_wav, make_png


# ---------------------------------------------------------------- WAV validation

def test_inspect_wav_accepts_valid_clip():
    seconds, channels, framerate = inspect_wav("a.wav", make_wav(0.5))
    assert 0.4 < seconds < 0.6 and channels == 1 and framerate == 22050


@pytest.mark.parametrize("data", [
    b"not a wav",
    b"RIFF\x00\x00\x00\x00WAVEjunk",
    make_png(8, 8),              # an image is not audio
])
def test_inspect_wav_rejects_garbage(data):
    with pytest.raises(ValueError):
        inspect_wav("x.wav", data)


def test_inspect_wav_rejects_too_long():
    with pytest.raises(ValueError, match="Länge"):
        inspect_wav("long.wav", make_wav(MAX_CLIP_SECONDS + 2))


def test_inspect_wav_rejects_odd_samplerate():
    with pytest.raises(ValueError, match="Abtastrate"):
        inspect_wav("sr.wav", make_wav(0.3, framerate=96000))


# ---------------------------------------------------------------- package format

def cfg_with_clip(clip_name="sound.wav"):
    action = default_action("sound_clip")
    action["params"]["clip"] = f"resource:{clip_name}"
    config = new_config("Mit Ton")
    config["actions"] = [action]
    return config


def test_resource_entry_has_audio_kind_and_seconds():
    entry = inspect_resource("sound.wav", make_wav(0.5))
    assert entry["kind"] == "audio" and entry["mime"] == "audio/wav"
    assert 0.4 < entry["seconds"] < 0.6 and entry["channels"] == 1


def test_round_trip_with_audio_resource(tmp_path):
    path = save_package(tmp_path / "ton.mpk", cfg_with_clip(), {"sound.wav": make_wav(0.5)})
    pkg = load_package_full(path)
    assert pkg.resources["sound.wav"] == make_wav(0.5)
    assert pkg.config["audio"] is True
    entry = next(r for r in pkg.config["resources"] if r["name"] == "sound.wav")
    assert entry["kind"] == "audio"
    assert action_audio_name(pkg.config["actions"][0]) == "sound.wav"


def test_summary_mentions_sound_clip():
    config = validate_config(cfg_with_clip(), {"sound.wav": make_wav(0.5)})
    text = summarize_config(config)
    assert "Audio: JA" in text and "Sound-Clip" in text and "Sound-Clips im Paket: 1" in text


def test_missing_clip_reference_is_rejected():
    with pytest.raises(PackageError, match="Sound-Ressource"):
        validate_config(cfg_with_clip("fehlt.wav"), {})


def test_images_and_audio_can_coexist(tmp_path):
    action_img = default_action("screamer")
    action_img["params"]["image"] = "resource:bild.png"
    action_snd = default_action("sound_clip")
    action_snd["params"]["clip"] = "resource:ton.wav"
    config = new_config("Gemischt")
    config["actions"] = [action_img, action_snd]
    path = save_package(tmp_path / "mix.mpk", config, {"bild.png": make_png(16, 16), "ton.wav": make_wav(0.4)})
    pkg = load_package_full(path)
    kinds = {r["name"]: r["kind"] for r in pkg.config["resources"]}
    assert kinds == {"bild.png": "image", "ton.wav": "audio"}


def test_oversized_audio_rejected(tmp_path):
    big = make_wav(0.1) + b"\0" * (MAX_AUDIO_BYTES + 1)
    with pytest.raises(PackageError):
        save_package(tmp_path / "big.mpk", cfg_with_clip(), {"sound.wav": big})


def test_tampered_audio_bytes_detected(tmp_path):
    import zipfile
    path = save_package(tmp_path / "ok.mpk", cfg_with_clip(), {"sound.wav": make_wav(0.5)})
    with zipfile.ZipFile(path) as zf:
        manifest = zf.read("manifest.json")
    swapped = tmp_path / "swap.mpk"
    with zipfile.ZipFile(swapped, "w") as zf:
        zf.writestr("manifest.json", manifest)
        zf.writestr("resources/sound.wav", make_wav(0.6))  # different bytes than the manifest hash
    with pytest.raises(PackageError):
        load_package_full(swapped)


# ---------------------------------------------------------------- playback

def test_player_plays_clip_once_and_respects_mute():
    backend = NullBackend()
    player = AudioPlayer(backend, muted=True)
    assert player.play_clip(make_wav(0.3)) is False and backend.played == []
    player.muted = False
    assert player.play_clip(make_wav(0.3)) is True
    assert len(backend.played) == 1
    player.stop()
    assert backend.stops == 1
