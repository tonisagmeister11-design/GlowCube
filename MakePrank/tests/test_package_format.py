import json
import struct
import zipfile

import pytest

from makeprank.catalog import default_action
from makeprank.package_format import (
    MAX_RESOURCE_BYTES, ConsentStore, PackageError, file_sha256, load_package, load_package_full,
    new_config, save_package, summarize_config, validate_config,
)

from helpers import make_png


def cfg(**kw):
    config = new_config("Test")
    config.update(kw)
    return config


def test_round_trip_package_with_all_fields(tmp_path):
    config = cfg(creator_label="Toni", network="pairing")
    config["actions"] = [default_action("toast"), default_action("sound_effect")]
    path = save_package(tmp_path / "demo.mpk", config)
    loaded = load_package(path)
    assert loaded["name"] == "Test" and loaded["creator_label"] == "Toni"
    assert loaded["audio"] is True and loaded["network"] == "pairing"
    assert loaded["estimated_duration_seconds"] == sum(a["duration_seconds"] for a in loaded["actions"])
    assert len(loaded["content_sha256"]) == 64
    assert [a["type"] for a in loaded["actions"]] == ["toast", "sound_effect"]


def test_round_trip_with_image_resource(tmp_path):
    config = cfg()
    action = default_action("screamer")
    action["params"]["image"] = "resource:bild.png"
    config["actions"] = [action]
    path = save_package(tmp_path / "img.mpk", config, {"bild.png": make_png(32, 16)})
    pkg = load_package_full(path)
    assert pkg.resources["bild.png"] == make_png(32, 16)
    assert pkg.config["resources"][0]["width"] == 32
    assert pkg.file_sha256 == file_sha256(path)


def test_save_adds_mpk_suffix(tmp_path):
    assert save_package(tmp_path / "sample", cfg()).suffix == ".mpk"


@pytest.mark.parametrize("mutate", [
    lambda c: c.update(actions=[{"type": "powershell", "title": "x", "text": "whoami", "duration_seconds": 1}]),
    lambda c: c.update(safe_mode=False),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": 0}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": 121}]),
    lambda c: c.update(actions=[{"type": [], "title": "x", "text": "y", "duration_seconds": 1}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": True}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y" * 4001, "duration_seconds": 1}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "", "text": "y", "duration_seconds": 1}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x\x00", "text": "y", "duration_seconds": 1}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": 1, "cmd": "calc"}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": 1, "params": {"position": "mars"}}]),
    lambda c: c.update(actions=[{"type": "toast", "title": "x", "text": "y", "duration_seconds": 1, "params": {"url": "http://x"}}]),
    lambda c: c.update(actions=[{"type": "sound_effect", "title": "x", "text": "", "duration_seconds": 1, "params": {"volume": 100}}]),
    lambda c: c.update(actions=[{"type": "sound_effect", "title": "x", "text": "", "duration_seconds": 1, "params": {"milliseconds": 99999}}]),
    lambda c: c.update(actions=[{"type": "text_overlay", "title": "x", "text": "", "duration_seconds": 1, "params": {"color": "red"}}]),
    lambda c: c.update(actions=[{"type": "screamer", "title": "x", "text": "", "duration_seconds": 1, "params": {"image": "C:/x.png"}}]),
    lambda c: c.update(actions=[{"type": "screamer", "title": "x", "text": "", "duration_seconds": 1, "params": {"image": "resource:fehlt.png"}}]),
    lambda c: c.update(actions=[]),
    lambda c: c.update(actions=[default_action("toast")] * 31),
    lambda c: c.update(max_duration_seconds=0),
    lambda c: c.update(max_duration_seconds=1801),
    lambda c: c.update(max_duration_seconds=5, actions=[default_action("toast"), default_action("toast")]),
    lambda c: c.update(network="internet"),
    lambda c: c.update(name="../evil"),
    lambda c: c.update(format="other"),
    lambda c: c.update(format_version=99),
    lambda c: c.update(format_version=True),
    lambda c: c.update(extra="x"),
    lambda c: c.update(audio=False, actions=[default_action("sound_effect")]),
])
def test_invalid_configs_rejected(mutate):
    config = cfg()
    mutate(config)
    with pytest.raises(PackageError):
        validate_config(config)


def test_non_dict_rejected():
    for bad in (None, [], "x", 5):
        with pytest.raises(PackageError):
            validate_config(bad)


def test_unsupported_version_message_is_understandable():
    with pytest.raises(PackageError, match="aktualisiere MakePrank"):
        validate_config(cfg(format_version=7))


def test_manifest_tampering_detected_by_content_hash(tmp_path):
    path = save_package(tmp_path / "a.mpk", cfg())
    with zipfile.ZipFile(path) as zf:
        manifest = json.loads(zf.read("manifest.json"))
    manifest["actions"][0]["text"] = "heimlich geändert"
    forged = tmp_path / "forged.mpk"
    with zipfile.ZipFile(forged, "w") as zf:
        zf.writestr("manifest.json", json.dumps(manifest))
    with pytest.raises(PackageError, match="Hash"):
        load_package(forged)


def test_v1_package_is_migrated(tmp_path):
    v1 = {"format": "makeprank-package", "format_version": 1, "name": "Alt", "description": "x",
          "max_duration_seconds": 60, "safe_mode": True,
          "actions": [{"type": "message", "title": "Hi", "text": "t", "duration_seconds": 3},
                      {"type": "shake_simulation", "title": "S", "text": "", "duration_seconds": 3}]}
    path = tmp_path / "v1.mpk"
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("manifest.json", json.dumps(v1))
    loaded = load_package(path)
    assert loaded["format_version"] == 2
    assert [a["type"] for a in loaded["actions"]] == ["error_dialog", "window_shake"]


def test_v1_unknown_action_still_rejected():
    v1 = {"format": "makeprank-package", "format_version": 1, "name": "Alt", "safe_mode": True,
          "actions": [{"type": "powershell", "title": "x", "text": "", "duration_seconds": 1}]}
    with pytest.raises(PackageError):
        validate_config(v1)


def test_corrupt_and_missing_files(tmp_path):
    bad = tmp_path / "bad.mpk"
    bad.write_bytes(b"this is not a zip")
    with pytest.raises(PackageError):
        load_package(bad)
    with pytest.raises(PackageError):
        load_package(tmp_path / "gibt-es-nicht.mpk")


def test_invalid_json_and_missing_manifest(tmp_path):
    p = tmp_path / "j.mpk"
    with zipfile.ZipFile(p, "w") as zf:
        zf.writestr("manifest.json", "{nicht json")
    with pytest.raises(PackageError, match="JSON"):
        load_package(p)
    q = tmp_path / "m.mpk"
    with zipfile.ZipFile(q, "w") as zf:
        zf.writestr("NOTICE.txt", "x")
    with pytest.raises(PackageError, match="manifest"):
        load_package(q)


def _zip_with(tmp_path, extra_name, data=b"x", name="z.mpk"):
    path = tmp_path / name
    manifest = json.dumps(validate_config(cfg()))
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("manifest.json", manifest)
        zf.writestr(extra_name, data)
    return path


@pytest.mark.parametrize("evil", ["../evil.txt", "/abs.txt", "resources/../../x.png", "resources\\..\\x.png",
                                  "C:/x.png", "script.py", "payload.exe", "resources/a.exe", "resources/Ab.png",
                                  "resources/sub/a.png", "resources/a.svg", "resources/a.png.exe"])
def test_zip_slip_and_unexpected_names_rejected(tmp_path, evil):
    with pytest.raises(PackageError):
        load_package(_zip_with(tmp_path, evil))


def test_duplicate_names_rejected(tmp_path):
    path = tmp_path / "dup.mpk"
    import warnings
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        with zipfile.ZipFile(path, "w") as zf:
            zf.writestr("manifest.json", json.dumps(validate_config(cfg())))
            zf.writestr("manifest.json", "{}")
    with pytest.raises(PackageError):
        load_package(path)


def test_symlink_entry_rejected(tmp_path):
    path = tmp_path / "ln.mpk"
    with zipfile.ZipFile(path, "w") as zf:
        zf.writestr("manifest.json", json.dumps(validate_config(cfg())))
        info = zipfile.ZipInfo("resources/a.png")
        info.external_attr = (0o120777 << 16)
        zf.writestr(info, b"../../etc/passwd")
    with pytest.raises(PackageError):
        load_package(path)


def test_resource_limits(tmp_path):
    big = make_png(8, 8) + b"\0" * (MAX_RESOURCE_BYTES + 1)
    with pytest.raises(PackageError):
        save_package(tmp_path / "a.mpk", cfg(), {"a.png": big})
    with pytest.raises(PackageError, match="Bildgröße"):
        save_package(tmp_path / "b.mpk", cfg(), {"a.png": make_png(4000, 10)})
    with pytest.raises(PackageError):
        save_package(tmp_path / "c.mpk", cfg(), {f"r{i}.png": make_png() for i in range(7)})
    with pytest.raises(PackageError):
        save_package(tmp_path / "d.mpk", cfg(), {"x.png": b"MZ\x90\x00 not a png"})
    with pytest.raises(PackageError):
        save_package(tmp_path / "e.mpk", cfg(), {"x.exe": make_png()})
    with pytest.raises(PackageError):
        save_package(tmp_path / "f.mpk", cfg(), {"x.gif": make_png()})  # mime/extension mismatch


def test_gif_header_is_validated(tmp_path):
    gif = b"GIF89a" + struct.pack("<HH", 10, 10) + b"\0" * 20
    save_package(tmp_path / "g.mpk", cfg(), {"a.gif": gif})


def test_declared_size_lie_is_detected(tmp_path):
    path = tmp_path / "huge.mpk"
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("manifest.json", json.dumps(validate_config(cfg())))
        zf.writestr("resources/a.png", make_png() + b"\0" * (4 * 1024 * 1024))
    with pytest.raises(PackageError):
        load_package(path)


def test_oversized_package_file_rejected(tmp_path):
    path = tmp_path / "big.mpk"
    path.write_bytes(b"\0" * (12 * 1024 * 1024 + 1))
    with pytest.raises(PackageError, match="zu groß"):
        load_package(path)


def test_resource_hash_mismatch_detected(tmp_path):
    action = default_action("screamer")
    action["params"]["image"] = "resource:a.png"
    config = cfg(actions=[action])
    path = save_package(tmp_path / "ok.mpk", config, {"a.png": make_png(8, 8)})
    with zipfile.ZipFile(path) as zf:
        manifest = zf.read("manifest.json")
    swapped = tmp_path / "swap.mpk"
    with zipfile.ZipFile(swapped, "w") as zf:
        zf.writestr("manifest.json", manifest)
        zf.writestr("resources/a.png", make_png(8, 8, (1, 2, 3)))
    with pytest.raises(PackageError):
        load_package(swapped)


def test_consent_is_bound_to_exact_hash_and_revocable(tmp_path):
    first = save_package(tmp_path / "one.mpk", cfg())
    second_config = cfg()
    second_config["actions"][0]["text"] = "Changed action"
    second = save_package(tmp_path / "two.mpk", second_config)
    h1, h2 = file_sha256(first), file_sha256(second)
    assert h1 != h2
    store = ConsentStore(tmp_path / "consents.json")
    assert not store.is_accepted(h1)
    store.accept(h1)
    assert store.is_accepted(h1) and not store.is_network_accepted(h1)
    assert not store.is_accepted(h2)
    store.accept(h1, network=True)
    assert store.is_network_accepted(h1)
    store.revoke(h1)
    assert not store.is_accepted(h1)


def test_corrupt_consent_file_means_no_consent(tmp_path):
    path = tmp_path / "c.json"
    path.write_text("{kaputt")
    assert not ConsentStore(path).is_accepted("abc")


def test_summary_lists_effects_duration_audio_and_network():
    config = validate_config(cfg(network="pairing", actions=[default_action("sound_effect"), default_action("toast")]))
    text = summarize_config(config)
    assert "Audio: JA" in text and "Fernsteuerung" in text and "Geschätzte Dauer" in text
    assert "Soundeffekt" in text and "Toast" in text


def test_no_dynamic_code_or_process_apis_anywhere_in_the_app():
    """No module of the app may import or call code-loading / process / URL-opening APIs."""
    import ast
    from pathlib import Path
    forbidden_modules = {"pickle", "marshal", "subprocess", "importlib", "webbrowser", "ctypes", "shelve"}
    bare_calls = {"eval", "exec", "compile", "__import__"}
    method_calls = {"system", "popen", "startfile"}
    for path in Path(__file__).resolve().parents[1].joinpath("makeprank").glob("*.py"):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                assert not {a.name.split(".")[0] for a in node.names} & forbidden_modules, path.name
            elif isinstance(node, ast.ImportFrom):
                assert (node.module or "").split(".")[0] not in forbidden_modules, path.name
            elif isinstance(node, ast.Call):
                func = node.func
                if isinstance(func, ast.Name):
                    assert func.id not in bare_calls, f"{path.name}: {func.id}()"
                elif isinstance(func, ast.Attribute):
                    assert func.attr not in method_calls, f"{path.name}: .{func.attr}()"
