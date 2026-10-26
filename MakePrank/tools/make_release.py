"""Assemble the release folder + ZIP from PyInstaller output.

Writes: release/MakePrank-<platform>/{Creator,Client folders, docs, BUILD_REPORT.txt, SHA256SUMS.txt}
and release/MakePrank-<platform>.zip. Reports honestly which platform the build ran on.
"""
from __future__ import annotations

import hashlib
import platform
import shutil
import sys
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from makeprank import __version__  # noqa: E402


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv: list[str]) -> int:
    test_summary = argv[1] if len(argv) > 1 else "(nicht angegeben)"
    smoke_summary = argv[2] if len(argv) > 2 else "(nicht angegeben)"
    dist = ROOT / "dist"
    folders = [dist / "MakePrank-Creator", dist / "MakePrank-Client"]
    missing = [str(f) for f in folders if not f.is_dir()]
    if missing:
        print("Build-Ordner fehlen:", ", ".join(missing))
        return 1
    system = platform.system()
    name = f"MakePrank-{__version__}-{system}"
    out = ROOT / "release" / name
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    for folder in folders:
        shutil.copytree(folder, out / folder.name)
    shutil.copy(ROOT / "README.md", out / "README_DE.md")
    shutil.copy(ROOT / "docs" / "USER_GUIDE_DE.md", out / "BENUTZERHANDBUCH_DE.md")
    shutil.copytree(ROOT / "examples", out / "examples")
    try:
        import PyInstaller  # type: ignore[import-not-found]
        pyi = PyInstaller.__version__
    except ImportError:
        pyi = "unbekannt"
    native = system == "Windows"
    exe_suffix = ".exe" if native else ""
    report = [
        "MakePrank – Buildbericht",
        f"Version: {__version__}",
        f"Zeit (UTC): {datetime.now(timezone.utc).isoformat(timespec='seconds')}",
        f"Build-Plattform: {system} {platform.release()} / Python {platform.python_version()} / PyInstaller {pyi}",
        "Windows-Build: " + ("JA – auf Windows gebaut" if native else f"NEIN – gebaut auf {system}; das ist KEINE Windows-EXE"),
        f"Tests: {test_summary}",
        f"Start-Smoke-Test der Programme: {smoke_summary}",
        "Codesignatur: KEINE (Programme sind nicht signiert; Windows SmartScreen kann warnen).",
        f"Programme: MakePrank-Creator/MakePrank-Creator{exe_suffix}, MakePrank-Client/MakePrank-Client{exe_suffix}",
        "",
        "Doppelklick auf .mpk: keine Registry-Änderung. Datei auf MakePrank-Client.exe ziehen oder",
        "Rechtsklick → Öffnen mit → MakePrank-Client.exe. Das Paket startet nie ohne ausdrückliche Zustimmung.",
    ]
    (out / "BUILD_REPORT.txt").write_text("\n".join(report) + "\n", encoding="utf-8")
    lines = []
    for path in sorted(p for p in out.rglob("*") if p.is_file() and p.name != "SHA256SUMS.txt"):
        lines.append(f"{sha256(path)}  {path.relative_to(out).as_posix()}")
    (out / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    archive = ROOT / "release" / f"{name}.zip"
    archive.unlink(missing_ok=True)
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(out.rglob("*")):
            if path.is_file():
                zf.write(path, Path(name, path.relative_to(out)).as_posix())
    print(f"Release-Ordner: {out}")
    print(f"ZIP: {archive}")
    print(f"ZIP SHA-256: {sha256(archive)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
