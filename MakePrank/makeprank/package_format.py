"""Versioned .mpk package format, validator, summaries and consent store.

A ``.mpk`` is a ZIP container that holds data only:

* ``manifest.json``  – strictly validated JSON (format version 2)
* ``NOTICE.txt``     – human readable note
* ``resources/*``    – optional PNG/GIF images (size, count and dimensions limited)

Nothing in a package is ever executed, imported, unpickled or evaluated.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import struct
import tempfile
import zipfile
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .audio import MAX_CLIP_SECONDS, inspect_wav
from .catalog import (
    EFFECTS, MAX_ACTION_DURATION_SECONDS, MAX_ACTIONS, MAX_SESSION_DURATION_SECONDS,
    MAX_TEXT_LENGTH, MAX_TITLE_LENGTH, V1_ALIASES, ParamError, action_audio_name,
    action_has_audio, action_resource_name, clean_text, default_action, get_effect,
)

FORMAT_ID = "makeprank-package"
FORMAT_VERSION = 2
SUPPORTED_READ_VERSIONS = (1, 2)
PACKAGE_MANIFEST = "manifest.json"
PACKAGE_README = "NOTICE.txt"
RESOURCE_DIR = "resources/"

MAX_PACKAGE_FILE_BYTES = 12 * 1024 * 1024
MAX_MANIFEST_BYTES = 256_000
MAX_RESOURCES = 6
MAX_IMAGE_BYTES = 1024 * 1024
MAX_AUDIO_BYTES = 2 * 1024 * 1024
MAX_RESOURCE_BYTES = MAX_AUDIO_BYTES  # overall per-file read cap
MAX_TOTAL_RESOURCE_BYTES = 6 * 1024 * 1024
MAX_IMAGE_WIDTH = 1920
MAX_IMAGE_HEIGHT = 1080
MAX_ZIP_ENTRIES = 2 + MAX_RESOURCES

ALLOWED_NAME = re.compile(r"^[\w .()\-]{1,80}$", re.UNICODE)
RESOURCE_NAME = re.compile(r"^[a-z0-9_\-]{1,40}\.(png|gif|wav)$")
ALLOWED_MIME = {"png": "image/png", "gif": "image/gif", "wav": "audio/wav"}
NETWORK_MODES = ("none", "pairing")


class PackageError(ValueError):
    """Raised when a package is malformed, too large or contains unsupported content."""


@dataclass
class LoadedPackage:
    config: dict[str, Any]
    resources: dict[str, bytes] = field(default_factory=dict)
    file_sha256: str = ""
    path: Path | None = None


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def new_config(name: str = "Mein Prank") -> dict[str, Any]:
    return {
        "format": FORMAT_ID,
        "format_version": FORMAT_VERSION,
        "name": name,
        "description": "Eine sichtbare, jederzeit beendbare MakePrank-Simulation.",
        "creator_label": "",
        "created_utc": utc_now(),
        "max_duration_seconds": 300,
        "safe_mode": True,
        "network": "none",
        "actions": [default_action("error_dialog")],
    }


# --------------------------------------------------------------------------- images

def inspect_image(name: str, data: bytes) -> tuple[str, int, int]:
    """Return ``(mime, width, height)`` for a PNG/GIF or raise :class:`PackageError`."""
    match = RESOURCE_NAME.fullmatch(name)
    if not match or match.group(1) == "wav":
        raise PackageError(f"Ressourcenname {name!r} ist kein Bild (nur a-z, 0-9, _, - und .png/.gif).")
    ext = match.group(1)
    if len(data) > MAX_IMAGE_BYTES:
        raise PackageError(f"Bild {name} ist größer als {MAX_IMAGE_BYTES // 1024} KB.")
    if ext == "png":
        if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
            raise PackageError(f"Ressource {name} ist kein gültiges PNG.")
        width, height = struct.unpack(">II", data[16:24])
    else:
        if len(data) < 10 or data[:6] not in (b"GIF87a", b"GIF89a"):
            raise PackageError(f"Ressource {name} ist kein gültiges GIF.")
        width, height = struct.unpack("<HH", data[6:10])
    if not (1 <= width <= MAX_IMAGE_WIDTH and 1 <= height <= MAX_IMAGE_HEIGHT):
        raise PackageError(f"Ressource {name}: Bildgröße {width}x{height} außerhalb von {MAX_IMAGE_WIDTH}x{MAX_IMAGE_HEIGHT}.")
    return ALLOWED_MIME[ext], width, height


def inspect_resource(name: str, data: bytes) -> dict[str, Any]:
    """Validate one resource (image or WAV audio) and return its manifest entry."""
    match = RESOURCE_NAME.fullmatch(name)
    if not match:
        raise PackageError(f"Ressourcenname {name!r} ist nicht erlaubt (nur a-z, 0-9, _, - und .png/.gif/.wav).")
    ext = match.group(1)
    base = {"name": name, "mime": ALLOWED_MIME[ext], "size": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    if ext == "wav":
        if len(data) > MAX_AUDIO_BYTES:
            raise PackageError(f"Sound {name} ist größer als {MAX_AUDIO_BYTES // 1024} KB.")
        try:
            seconds, channels, framerate = inspect_wav(name, data)
        except ValueError as exc:
            raise PackageError(str(exc)) from exc
        base.update(kind="audio", seconds=round(seconds, 3), channels=channels, framerate=framerate)
        return base
    mime, width, height = inspect_image(name, data)
    base.update(kind="image", width=width, height=height)
    return base


def _resource_entries(resources: dict[str, bytes]) -> list[dict[str, Any]]:
    if len(resources) > MAX_RESOURCES:
        raise PackageError(f"Höchstens {MAX_RESOURCES} Ressourcen erlaubt.")
    total = 0
    entries = []
    for name in sorted(resources):
        data = resources[name]
        if not isinstance(data, (bytes, bytearray)):
            raise PackageError(f"Ressource {name}: ungültiger Datentyp.")
        entries.append(inspect_resource(name, bytes(data)))
        total += len(data)
    if total > MAX_TOTAL_RESOURCE_BYTES:
        raise PackageError(f"Ressourcen insgesamt größer als {MAX_TOTAL_RESOURCE_BYTES // 1024} KB.")
    return entries


# --------------------------------------------------------------------------- validation

def content_hash(config: dict[str, Any]) -> str:
    """SHA-256 over the canonical manifest without its own ``content_sha256`` field.

    This detects accidental or partial modification. It is *not* proof of who
    created a package (there are no signatures in format version 2).
    """
    body = {key: value for key, value in config.items() if key != "content_sha256"}
    canonical = json.dumps(body, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _migrate_v1(config: dict[str, Any]) -> dict[str, Any]:
    actions = []
    for action in config.get("actions", []) if isinstance(config.get("actions"), list) else []:
        if not isinstance(action, dict):
            actions.append(action)
            continue
        kind = action.get("type")
        mapped = V1_ALIASES.get(kind) if isinstance(kind, str) else None
        if mapped is None:
            actions.append(action)
            continue
        spec = EFFECTS[mapped]
        params = spec.default_params()
        actions.append({
            "type": mapped, "title": action.get("title"), "text": action.get("text", ""),
            "duration_seconds": action.get("duration_seconds", 8), "params": params,
        })
    migrated = dict(config)
    migrated["format_version"] = FORMAT_VERSION
    migrated["actions"] = actions
    migrated.setdefault("creator_label", "")
    migrated.setdefault("network", "none")
    migrated.pop("content_sha256", None)
    try:
        total = sum(a["duration_seconds"] for a in actions if isinstance(a, dict) and isinstance(a.get("duration_seconds"), int))
        old = migrated.get("max_duration_seconds", 300)
        if isinstance(old, int) and not isinstance(old, bool):
            migrated["max_duration_seconds"] = min(MAX_SESSION_DURATION_SECONDS, max(old, total))
    except (TypeError, KeyError):
        pass
    return migrated


def validate_config(config: Any, resources: dict[str, bytes] | None = None) -> dict[str, Any]:
    """Validate ``config`` strictly and return a clean, normalised copy.

    Derived fields (``audio``, ``estimated_duration_seconds``, ``resources`` and
    ``content_sha256``) are recomputed; a supplied ``content_sha256`` that does
    not match raises :class:`PackageError`.
    """
    if not isinstance(config, dict):
        raise PackageError("Die Konfiguration muss ein JSON-Objekt sein.")
    if config.get("format") != FORMAT_ID:
        raise PackageError("Unbekanntes Paketformat.")
    version = config.get("format_version")
    if isinstance(version, bool) or version not in SUPPORTED_READ_VERSIONS:
        raise PackageError(
            f"Diese Paketversion ({version!r}) wird nicht unterstützt. Unterstützt: {', '.join(map(str, SUPPORTED_READ_VERSIONS))}. "
            "Bitte aktualisiere MakePrank oder lass das Paket neu exportieren."
        )
    if version == 1:
        config = _migrate_v1(config)
    allowed_top = {
        "format", "format_version", "name", "description", "creator_label", "created_utc",
        "max_duration_seconds", "safe_mode", "network", "actions", "resources",
        "audio", "estimated_duration_seconds", "content_sha256",
    }
    unknown = set(config) - allowed_top
    if unknown:
        raise PackageError(f"Unbekannte Felder im Manifest: {', '.join(sorted(map(str, unknown)))}.")

    name = config.get("name")
    if not isinstance(name, str) or not ALLOWED_NAME.fullmatch(name.strip()):
        raise PackageError("Der Paketname muss 1–80 gültige Zeichen enthalten.")
    try:
        description = clean_text(config.get("description", ""), "Beschreibung", MAX_TEXT_LENGTH)
        creator = clean_text(config.get("creator_label", ""), "Creator-Label", 60)
    except ParamError as exc:
        raise PackageError(str(exc)) from exc
    if config.get("safe_mode") is not True:
        raise PackageError("Es ist ausschließlich Safe Mode zugelassen.")
    network = config.get("network", "none")
    if network not in NETWORK_MODES:
        raise PackageError(f"Ungültiger Netzwerkmodus {network!r}.")
    max_duration = config.get("max_duration_seconds", 300)
    if not isinstance(max_duration, int) or isinstance(max_duration, bool) or not 1 <= max_duration <= MAX_SESSION_DURATION_SECONDS:
        raise PackageError(f"Sitzungsdauer muss zwischen 1 und {MAX_SESSION_DURATION_SECONDS} Sekunden liegen.")
    created = config.get("created_utc") or utc_now()
    if not isinstance(created, str) or len(created) > 64:
        raise PackageError("Ungültiges Erstellungsdatum.")

    raw_actions = config.get("actions")
    if not isinstance(raw_actions, list) or not 1 <= len(raw_actions) <= MAX_ACTIONS:
        raise PackageError(f"Eine Konfiguration benötigt 1–{MAX_ACTIONS} Aktionen.")
    actions = [_clean_action(index, raw) for index, raw in enumerate(raw_actions, 1)]

    estimated = sum(a["duration_seconds"] for a in actions)
    if estimated > max_duration:
        raise PackageError(
            f"Die Gesamtdauer der Aktionen ({estimated} s) überschreitet die maximale Sitzungsdauer ({max_duration} s)."
        )

    if resources is not None:
        entries = _resource_entries(resources)
    else:
        entries = _validate_resource_entries(config.get("resources", []))
    available = {entry["name"] for entry in entries}
    audio_names = {entry["name"] for entry in entries if entry.get("kind") == "audio"}
    image_names = {entry["name"] for entry in entries if entry.get("kind") == "image"}
    for index, action in enumerate(actions, 1):
        ref = action_resource_name(action)
        if ref is not None and ref not in image_names:
            raise PackageError(f"Aktion {index}: Bildressource {ref!r} ist im Paket nicht vorhanden.")
        clip = action_audio_name(action)
        if clip is not None and clip not in audio_names:
            raise PackageError(f"Aktion {index}: Sound-Ressource {clip!r} ist im Paket nicht vorhanden.")

    clean = {
        "format": FORMAT_ID,
        "format_version": FORMAT_VERSION,
        "name": name.strip(),
        "description": description,
        "creator_label": creator,
        "created_utc": created,
        "max_duration_seconds": max_duration,
        "safe_mode": True,
        "network": network,
        "audio": any(action_has_audio(a) for a in actions),
        "estimated_duration_seconds": estimated,
        "resources": entries,
        "actions": actions,
    }
    digest = content_hash(clean)
    supplied = config.get("content_sha256")
    if version == FORMAT_VERSION:
        if supplied is not None and supplied != digest:
            raise PackageError("Der Inhalts-Hash stimmt nicht überein: das Manifest wurde verändert.")
        # A hand-written v2 manifest without derived fields is still accepted when
        # the derived values we computed agree with whatever it declared.
        for key in ("audio", "estimated_duration_seconds"):
            if key in config and config[key] != clean[key]:
                raise PackageError(f"Feld {key!r} passt nicht zum tatsächlichen Inhalt.")
    clean["content_sha256"] = digest
    return clean


def _validate_resource_entries(raw: Any) -> list[dict[str, Any]]:
    if not isinstance(raw, list) or len(raw) > MAX_RESOURCES:
        raise PackageError("Ungültige Ressourcenliste.")
    entries = []
    for entry in raw:
        if not isinstance(entry, dict):
            raise PackageError("Ungültiger Ressourceneintrag.")
        name = entry.get("name")
        if not isinstance(name, str) or not RESOURCE_NAME.fullmatch(name):
            raise PackageError("Ungültiger Ressourcenname.")
        ext = name.rsplit(".", 1)[1]
        expected = ({"name", "kind", "mime", "size", "seconds", "channels", "framerate", "sha256"} if ext == "wav"
                    else {"name", "kind", "mime", "size", "width", "height", "sha256"})
        if set(entry) != expected:
            raise PackageError(f"Ressource {name}: Felder passen nicht zum Typ.")
        if entry["kind"] != ("audio" if ext == "wav" else "image") or entry["mime"] != ALLOWED_MIME[ext]:
            raise PackageError(f"Ressource {name}: Typ/MIME nicht erlaubt.")
        if not isinstance(entry["size"], int) or isinstance(entry["size"], bool) or entry["size"] < 1:
            raise PackageError(f"Ressource {name}: ungültige Größe.")
        if not isinstance(entry["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            raise PackageError(f"Ressource {name}: ungültiger Hash.")
        if ext == "wav":
            if entry["size"] > MAX_AUDIO_BYTES:
                raise PackageError(f"Sound {name} überschreitet die Größe.")
            for key in ("channels", "framerate"):
                if not isinstance(entry[key], int) or isinstance(entry[key], bool) or entry[key] < 1:
                    raise PackageError(f"Ressource {name}: ungültiges Feld {key}.")
            if not isinstance(entry["seconds"], (int, float)) or isinstance(entry["seconds"], bool) or not 0 < entry["seconds"] <= MAX_CLIP_SECONDS:
                raise PackageError(f"Sound {name}: Länge außerhalb von 0–{MAX_CLIP_SECONDS:.0f} s.")
        else:
            for key in ("width", "height"):
                if not isinstance(entry[key], int) or isinstance(entry[key], bool) or entry[key] < 1:
                    raise PackageError(f"Ressource {name}: ungültiges Feld {key}.")
            if entry["size"] > MAX_IMAGE_BYTES or entry["width"] > MAX_IMAGE_WIDTH or entry["height"] > MAX_IMAGE_HEIGHT:
                raise PackageError(f"Ressource {name} überschreitet die Limits.")
        entries.append(dict(entry))
    if sum(e["size"] for e in entries) > MAX_TOTAL_RESOURCE_BYTES:
        raise PackageError("Ressourcen insgesamt zu groß.")
    return entries


def _clean_action(index: int, action: Any) -> dict[str, Any]:
    if not isinstance(action, dict):
        raise PackageError(f"Aktion {index}: ungültiger Datentyp.")
    unknown = set(action) - {"type", "title", "text", "duration_seconds", "params"}
    if unknown:
        raise PackageError(f"Aktion {index}: unbekannte Felder {', '.join(sorted(map(str, unknown)))}.")
    kind = action.get("type")
    try:
        spec = get_effect(kind)
    except ParamError as exc:
        raise PackageError(f"Aktion {index}: {exc}.") from exc
    title = action.get("title", spec.default_title)
    duration = action.get("duration_seconds", spec.default_duration)
    if not isinstance(title, str) or not title.strip() or len(title) > MAX_TITLE_LENGTH:
        raise PackageError(f"Aktion {index}: Titel fehlt oder ist zu lang.")
    if any(ord(ch) < 32 or ord(ch) == 127 for ch in title):
        raise PackageError(f"Aktion {index}: Steuerzeichen im Titel sind nicht erlaubt.")
    try:
        text = clean_text(action.get("text", ""), "Text", MAX_TEXT_LENGTH)
    except ParamError as exc:
        raise PackageError(f"Aktion {index}: {exc}") from exc
    if not isinstance(duration, int) or isinstance(duration, bool) or not 1 <= duration <= MAX_ACTION_DURATION_SECONDS:
        raise PackageError(f"Aktion {index}: Dauer muss 1–{MAX_ACTION_DURATION_SECONDS} Sekunden betragen.")
    try:
        params = spec.clean_params(action.get("params"))
    except ParamError as exc:
        raise PackageError(f"Aktion {index}: {exc}") from exc
    return {"type": kind, "title": title.strip(), "text": text, "duration_seconds": duration, "params": params}


def clean_action(raw: Any) -> dict[str, Any]:
    """Validate a single action (used by the editor before it is added to a package)."""
    return _clean_action(1, raw)


# --------------------------------------------------------------------------- summary

def summarize_config(config: dict[str, Any]) -> str:
    """Readable summary shown before export and before the recipient consents."""
    lines = [
        f"Paket: {config['name']}",
        f"Von: {config.get('creator_label') or '(kein Absender angegeben)'}",
        f"Beschreibung: {config.get('description') or '-'}",
        f"Effekte: {len(config['actions'])}",
        f"Geschätzte Dauer: {config['estimated_duration_seconds']} s (Maximum {config['max_duration_seconds']} s)",
        f"Audio: {'JA – es werden Töne/Sounds abgespielt' if config['audio'] else 'nein'}",
        f"Bilder im Paket: {sum(1 for r in config['resources'] if r.get('kind') == 'image')}",
        f"Sound-Clips im Paket: {sum(1 for r in config['resources'] if r.get('kind') == 'audio')}",
        "Netzwerk: " + ("Fernsteuerung über gekoppeltes Pairing ist erlaubt (nur diese Effekte, jederzeit trennbar)"
                        if config["network"] == "pairing" else "kein Netzwerk"),
        "",
    ]
    for index, action in enumerate(config["actions"], 1):
        spec = EFFECTS[action["type"]]
        flags = []
        if action_has_audio(action):
            flags.append("AUDIO")
        if action_resource_name(action):
            flags.append("eigenes Bild")
        if action_audio_name(action):
            flags.append(f"Sound-Clip: {action_audio_name(action)}")
        suffix = f"  [{', '.join(flags)}]" if flags else ""
        lines.append(f"{index}. {spec.label} – „{action['title']}“ ({action['duration_seconds']} s){suffix}")
        if action["text"]:
            shortened = action["text"].replace("\n", " ⏎ ")
            lines.append(f"     {shortened[:160]}")
        lines.append(f"     Hinweis: {spec.risk_note}")
    lines += [
        "",
        "Grenzen: Keine Systembefehle, keine Dateiänderungen, kein Herunterfahren, keine Eingabe- oder "
        "Fenstersteuerung außerhalb von MakePrank. STOPP, Esc und Fenster schließen beenden alles sofort.",
    ]
    return "\n".join(lines)


def _notice_text(config: dict[str, Any]) -> str:
    return (
        "MakePrank-Paket\n"
        f"Name: {config['name']}\n"
        f"Format: {FORMAT_ID} v{config['format_version']}\n"
        f"Inhalts-Hash: {config['content_sha256']}\n\n"
        "Dieses Paket enthält nur Daten und sichtbare Simulationen innerhalb der MakePrank-Anwendung.\n"
        "Es führt keine Systembefehle aus, startet keine externen Programme, öffnet keine echten URLs,\n"
        "ändert keine Systemeinstellungen und installiert keine Hintergrunddienste.\n"
        "Die Sitzung kann jederzeit in MakePrank über STOPP, Esc oder Schließen beendet werden.\n"
    )


# --------------------------------------------------------------------------- save / load

def save_package(path: str | os.PathLike[str], config: dict[str, Any],
                 resources: dict[str, bytes] | None = None) -> Path:
    dest = Path(path)
    if dest.suffix.lower() != ".mpk":
        dest = dest.with_suffix(".mpk")
    resources = dict(resources or {})
    # Derived fields are always recomputed on export, never trusted from the editor.
    editable = {key: value for key, value in config.items()
                if key not in ("content_sha256", "audio", "estimated_duration_seconds", "resources")}
    clean = validate_config(editable, resources)
    dest.parent.mkdir(parents=True, exist_ok=True)
    manifest = json.dumps(clean, ensure_ascii=False, indent=2).encode("utf-8")
    with tempfile.NamedTemporaryFile(prefix="makeprank-", suffix=".tmp", dir=dest.parent, delete=False) as tmp:
        temp_path = Path(tmp.name)
    try:
        with zipfile.ZipFile(temp_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(PACKAGE_MANIFEST, manifest)
            zf.writestr(PACKAGE_README, _notice_text(clean))
            for res_name in sorted(resources):
                zf.writestr(RESOURCE_DIR + res_name, bytes(resources[res_name]))
        temp_path.replace(dest)
        try:
            os.chmod(dest, 0o644)
        except OSError:
            pass  # Windows ACLs, rather than POSIX mode bits, govern access there.
    finally:
        temp_path.unlink(missing_ok=True)
    return dest


def _read_member(zf: zipfile.ZipFile, info: zipfile.ZipInfo, limit: int) -> bytes:
    if info.file_size > limit:
        raise PackageError(f"{info.filename} ist zu groß.")
    with zf.open(info) as handle:
        data = handle.read(limit + 1)
    if len(data) != info.file_size:
        raise PackageError(f"{info.filename}: Größenangabe im Archiv stimmt nicht.")
    return data


def load_package_full(path: str | os.PathLike[str]) -> LoadedPackage:
    source = Path(path)
    try:
        size = source.stat().st_size
    except OSError as exc:
        raise PackageError(f"Paket konnte nicht gelesen werden: {exc}") from exc
    if size > MAX_PACKAGE_FILE_BYTES:
        raise PackageError("Die Paketdatei ist zu groß.")
    resources: dict[str, bytes] = {}
    try:
        with zipfile.ZipFile(source, "r") as zf:
            infos = zf.infolist()
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                raise PackageError("Das Paket enthält doppelte Dateinamen.")
            if len(names) > MAX_ZIP_ENTRIES:
                raise PackageError("Das Paket enthält zu viele Dateien.")
            if names.count(PACKAGE_MANIFEST) != 1:
                raise PackageError("Das Paket muss genau eine manifest.json enthalten.")
            total_uncompressed = 0
            for info in infos:
                name = info.filename
                if info.is_dir() or info.flag_bits & 0x1:
                    raise PackageError(f"Unerlaubter Archiveintrag {name!r}.")
                if stat.S_ISLNK(info.external_attr >> 16):
                    raise PackageError(f"Symbolische Verknüpfung {name!r} ist nicht erlaubt.")
                if info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
                    raise PackageError(f"Kompressionsverfahren von {name!r} ist nicht erlaubt.")
                parts = Path(name.replace("\\", "/")).parts
                if name.startswith(("/", "\\")) or ".." in parts or ":" in name or "\\" in name:
                    raise PackageError(f"Unsicherer Pfad im Paket: {name!r}.")
                total_uncompressed += info.file_size
                if total_uncompressed > MAX_MANIFEST_BYTES + MAX_TOTAL_RESOURCE_BYTES + 16_384:
                    raise PackageError("Das entpackte Paket wäre zu groß.")
                if name in (PACKAGE_MANIFEST, PACKAGE_README):
                    continue
                if name.startswith(RESOURCE_DIR) and RESOURCE_NAME.fullmatch(name[len(RESOURCE_DIR):]):
                    continue
                raise PackageError(f"Unerwartete Datei im Paket: {name!r}.")
            raw = _read_member(zf, zf.getinfo(PACKAGE_MANIFEST), MAX_MANIFEST_BYTES)
            for info in infos:
                if info.filename.startswith(RESOURCE_DIR):
                    resources[info.filename[len(RESOURCE_DIR):]] = _read_member(zf, info, MAX_RESOURCE_BYTES)
    except (zipfile.BadZipFile, OSError, KeyError, RuntimeError, NotImplementedError) as exc:
        raise PackageError(f"Paket konnte nicht gelesen werden: {exc}") from exc
    try:
        parsed = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PackageError("manifest.json ist kein gültiges UTF-8-JSON.") from exc
    config = validate_config(parsed, resources)
    # Declared resource hashes must match the actual bytes.
    if isinstance(parsed, dict) and parsed.get("format_version") == FORMAT_VERSION:
        declared = {entry.get("name"): entry.get("sha256") for entry in parsed.get("resources", []) if isinstance(entry, dict)}
        for entry in config["resources"]:
            if declared.get(entry["name"]) != entry["sha256"]:
                raise PackageError(f"Ressource {entry['name']}: Hash weicht vom Manifest ab.")
    return LoadedPackage(config=config, resources=resources, file_sha256=file_sha256(source), path=source)


def load_package(path: str | os.PathLike[str]) -> dict[str, Any]:
    """Load and validate a package; return only the clean configuration."""
    return load_package_full(path).config


def file_sha256(path: str | os.PathLike[str]) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


# --------------------------------------------------------------------------- consent

class ConsentStore:
    """Remembers approval per exact package-file hash on the local user account.

    A changed file has a different hash and therefore asks for consent again.
    Network consent is stored separately from consent to run effects locally.
    """

    def __init__(self, path: str | os.PathLike[str] | None = None):
        if path is None:
            path = Path.home() / ".makeprank" / "consents.json"
        self.path = Path(path)

    def _read(self) -> dict[str, Any]:
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
            return payload if isinstance(payload, dict) else {}
        except (OSError, json.JSONDecodeError):
            return {}

    def _write(self, data: dict[str, Any]) -> None:
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temp = self.path.with_suffix(".tmp")
        temp.write_text(json.dumps(data, indent=2), encoding="utf-8")
        temp.replace(self.path)

    def is_accepted(self, package_hash: str) -> bool:
        record = self._read().get(package_hash)
        return isinstance(record, dict) and record.get("accepted") is True

    def is_network_accepted(self, package_hash: str) -> bool:
        record = self._read().get(package_hash)
        return isinstance(record, dict) and record.get("accepted") is True and record.get("network") is True

    def accept(self, package_hash: str, network: bool = False) -> None:
        data = self._read()
        data[package_hash] = {"accepted": True, "network": bool(network), "accepted_utc": utc_now()}
        self._write(data)

    def revoke(self, package_hash: str) -> None:
        data = self._read()
        if data.pop(package_hash, None) is not None:
            self._write(data)
