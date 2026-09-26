"""Builds the distributable game folders.

    python3 Tools/Export/build_release.py [--release] [--development] [--smoke] [--zip]
    (no flags = everything)

Output (Export/Release/MyOpenWorldGame and Export/Development/MyOpenWorldGame):
    StartGame.exe            Windows x86_64 executable with the embedded core pack
                             (code, UI, shaders, materials)
    Game/PortAurelia.pck     characters, vehicles, weapons, props, interiors, textures, sounds
    Assets/City.pck          streamed city chunks, far city, map, city data
    Audio/Music/             put your own .ogg/.mp3/.wav here (no music ships with the game)
    Saves/  Config/          created/used at runtime (portable)
    LIES_MICH.txt            player readme

The Development build uses the debug template with a console window and enables the
F1-F11 debug tools. `--smoke` exports a Linux build with the same packs and boots it
headless (`-- --smoke-test`) to verify that the pack layout works.
"""
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GAME = os.path.join(ROOT, "Game")
GODOT = os.path.join(ROOT, "Tools", "Godot", "Godot_v4.4.1-stable_linux.x86_64")
if sys.platform.startswith("win"):
    GODOT = os.path.join(ROOT, "Tools", "Godot", "Godot_v4.4.1-stable_win64_console.exe")
EXPORT = os.path.join(ROOT, "Export")
VERSION = "0.9.0"

CITY_DIRS = ["assets/generated/city"]
CITY_FILES = ["assets/ui/generated/map.png"]
CITY_INCLUDE = "data/city/*.json,assets/generated/city/*.json"
GAME_DIRS = ["assets/generated/characters", "assets/generated/vehicles", "assets/generated/weapons",
             "assets/generated/props", "assets/generated/interiors", "assets/textures", "Audio/SFX"]
GAME_INCLUDE = ("Audio/SFX/manifest.json,assets/generated/characters/*.json,assets/generated/vehicles/*.json,"
                "assets/generated/weapons/*.json,assets/generated/props/*.json,assets/generated/interiors/*.json")
RESOURCE_EXT = (".glb", ".png", ".wav", ".ogg", ".tres", ".res", ".gdshader")

PLAYER_README = """PORT AURELIA
============

Starten:   StartGame.exe doppelklicken. Es muss nichts installiert werden.

Ordner
  Game/          Spieldaten (Figuren, Fahrzeuge, Waffen, Innenräume, Sounds)
  Assets/        Stadt (Straßen, Gebäude, Landschaft)
  Audio/Music/   Eigene Musik (.ogg, .mp3, .wav) hier ablegen und in den
                 Einstellungen unter Audio "Musik aktivieren" einschalten.
                 Das Spiel selbst enthält keine Musik.
  Saves/         Spielstände (Platz 1-3 und automatische Speicherung)
  Config/        Einstellungen

Steuerung (Tastatur & Maus, Xbox-Controller wird automatisch erkannt)
  WASD Bewegen · Shift Sprinten · Leertaste Springen/Klettern · Strg Ducken · Q Deckung
  Linke Maustaste Schießen · Rechte Maustaste Zielen · R Nachladen · 1-9 Waffen · TAB Waffenrad
  E Interagieren · F Fahrzeug · M Karte · Pfeil hoch Telefon · ESC Pause · F5 Schnellspeichern
  Im Auto: W/S Gas/Bremse · A/D Lenken · Leertaste Handbremse · H Hupe · L Licht · G Sirene
  V Kamera wechseln (zu Fuß: Third/First Person, im Auto: nah, weit, Motorhaube, Cockpit)

Kamera
  Einstellungen -> Kamera: Standard-Perspektive zu Fuß und im Auto, Abstand, Höhe, Sichtfeld,
  Kamerawackeln, automatische Zentrierung, Fadenkreuz, Minikarte, HUD.

Schwacher Laptop? -> LEISTUNGSMODUS
  Einstellungen -> Grafik -> Leistungsmodus "Maximal (schwache Laptops)" wählen und das Spiel
  neu starten. Dann läuft es mit dem sparsamen OpenGL-Renderer, 60 % Renderauflösung, ohne
  Schatten und teure Effekte, mit kürzerer Sichtweite, weniger Passanten/Verkehr und 30-FPS-Limit
  (spart Strom und Akku). "Ausgewogen" ist der Mittelweg. Die Wahl wird in override.cfg neben
  StartGame.exe gespeichert (Datei löschen = zurück zum Standard-Renderer).

Systemvoraussetzungen: Windows 10/11 64-bit. Volle Grafik: Vulkan-fähige Grafikkarte
(DirectX-12-Klasse), 8 GB RAM. Leistungsmodus "Maximal": OpenGL-3.3-fähige Grafik
(auch ältere Intel-Onboard-Grafik), 4-8 GB RAM. Ca. 1 GB Speicherplatz.
"""
MUSIC_README = """Eigene Musik
============
Lege hier deine eigenen Musikdateien ab (.ogg, .mp3 oder .wav).
Aktivieren: Hauptmenü oder Pause -> Einstellungen -> Audio -> "Musik aktivieren".
Das Spiel spielt ohne deine Dateien keine Musik ab.
"""


def list_files(dirs, exts=RESOURCE_EXT):
    out = []
    for d in dirs:
        base = os.path.join(GAME, d)
        for dp, _dn, fn in os.walk(base):
            for f in sorted(fn):
                if f.endswith(exts):
                    out.append("res://" + os.path.relpath(os.path.join(dp, f), GAME).replace(os.sep, "/"))
    return out


def psa(items):
    return "PackedStringArray(" + ", ".join('"%s"' % i for i in items) + ")"


def preset(idx, name, platform, filt, files, include, exclude, path, feats, options):
    lines = [f"[preset.{idx}]", "", f'name="{name}"', f'platform="{platform}"', "runnable=false",
             "advanced_options=false", "dedicated_server=false", f'custom_features="{feats}"',
             f'export_filter="{filt}"', f"export_files={psa(files)}", f'include_filter="{include}"',
             f'exclude_filter="{exclude}"', f'export_path="{path}"', "patches=PackedStringArray()",
             'encryption_include_filters=""', 'encryption_exclude_filters=""', "seed=0", "encrypt_pck=false",
             "encrypt_directory=false", "script_export_mode=2", "", f"[preset.{idx}.options]", ""]
    for k, v in options.items():
        if isinstance(v, bool):
            v = "true" if v else "false"
        elif isinstance(v, str):
            v = f'"{v}"'
        lines.append(f"{k}={v}")
    return "\n".join(lines) + "\n\n"


def win_options(debug, embed=True):
    return {
        "custom_template/debug": "", "custom_template/release": "",
        "debug/export_console_wrapper": 1 if debug else 0,
        "binary_format/embed_pck": embed, "texture_format/s3tc_bptc": True, "texture_format/etc2_astc": False,
        "binary_format/architecture": "x86_64", "codesign/enable": False, "application/modify_resources": False,
        "application/icon": "res://assets/ui/icon.png", "application/icon_interpolation": 4,
        "application/file_version": VERSION + ".0", "application/product_version": VERSION + ".0",
        "application/company_name": "Port Aurelia", "application/product_name": "Port Aurelia",
        "application/file_description": "Port Aurelia", "application/copyright": "",
        "application/trademarks": "", "application/export_angle": 0, "application/export_d3d12": 0,
        "application/d3d12_agility_sdk_multiarch": True, "ssh_remote_deploy/enabled": False,
    }


def linux_options(embed=True):
    return {"custom_template/debug": "", "custom_template/release": "", "debug/export_console_wrapper": 0,
            "binary_format/embed_pck": embed, "texture_format/s3tc_bptc": True, "texture_format/etc2_astc": False,
            "binary_format/architecture": "x86_64", "ssh_remote_deploy/enabled": False}


def write_presets():
    city = list_files(CITY_DIRS) + [f for f in ("res://" + c for c in CITY_FILES) if os.path.exists(os.path.join(GAME, f[6:]))]
    game = list_files(GAME_DIRS)
    core_excl = city + game
    txt = ""
    txt += preset(0, "Windows Release", "Windows Desktop", "exclude", core_excl, "", "tests/*",
                  "../Export/Release/MyOpenWorldGame/StartGame.exe", "release_build", win_options(False))
    txt += preset(1, "Windows Development", "Windows Desktop", "exclude", core_excl, "", "",
                  "../Export/Development/MyOpenWorldGame/StartGame.exe", "debug_tools", win_options(True))
    txt += preset(2, "Game Data", "Windows Desktop", "resources", game, GAME_INCLUDE, "", "", "", win_options(False, False))
    txt += preset(3, "City Data", "Windows Desktop", "resources", city, CITY_INCLUDE, "", "", "", win_options(False, False))
    txt += preset(4, "Linux Smoke", "Linux", "exclude", core_excl, "", "",
                  "../Export/Test/MyOpenWorldGame/StartGame.x86_64", "debug_tools", linux_options())
    with open(os.path.join(GAME, "export_presets.cfg"), "w", encoding="utf-8") as f:
        f.write(txt)
    print(f"[export] presets written: core excludes {len(core_excl)}, game pack {len(game)}, city pack {len(city)} files")


def godot(*args):
    cmd = [GODOT, "--headless", "--path", GAME] + list(args)
    print("[export] " + " ".join(os.path.basename(a) if a == GODOT else a for a in cmd[1:]))
    r = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    errors = [ln for ln in r.stdout.splitlines() if "ERROR" in ln and "rcedit" not in ln]
    for ln in errors[:20]:
        print("   " + ln)
    if r.returncode != 0:
        print(r.stdout[-3000:])
        raise SystemExit(f"godot failed ({r.returncode})")


def folder_layout(dst):
    for d in ("Game", "Assets", "Audio/Music", "Saves", "Config"):
        os.makedirs(os.path.join(dst, d), exist_ok=True)
    with open(os.path.join(dst, "LIES_MICH.txt"), "w", encoding="utf-8") as f:
        f.write(PLAYER_README)
    with open(os.path.join(dst, "Audio", "Music", "LIES_MICH.txt"), "w", encoding="utf-8") as f:
        f.write(MUSIC_README)


def build(kind, packs_dir):
    dst = os.path.join(EXPORT, kind, "MyOpenWorldGame")
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    folder_layout(dst)
    preset_name = "Windows Release" if kind == "Release" else "Windows Development"
    godot("--export-release" if kind == "Release" else "--export-debug", preset_name, os.path.join(dst, "StartGame.exe"))
    shutil.copy(os.path.join(packs_dir, "PortAurelia.pck"), os.path.join(dst, "Game", "PortAurelia.pck"))
    shutil.copy(os.path.join(packs_dir, "City.pck"), os.path.join(dst, "Assets", "City.pck"))
    size = sum(os.path.getsize(os.path.join(dp, f)) for dp, _d, fn in os.walk(dst) for f in fn)
    print(f"[export] {kind}: {dst} ({size / 1e6:.0f} MB)")
    return dst


def make_zip(dst, name):
    zp = os.path.join(os.path.dirname(dst), name)
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for dp, dn, fn in os.walk(dst):
            rel_dir = os.path.relpath(dp, os.path.dirname(dst))
            if not fn and not dn:
                z.writestr(rel_dir.replace(os.sep, "/") + "/", "")
            for f in fn:
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, os.path.dirname(dst)))
    print(f"[export] zip: {zp} ({os.path.getsize(zp) / 1e6:.0f} MB)")


def smoke(packs_dir):
    dst = os.path.join(EXPORT, "Test", "MyOpenWorldGame")
    if os.path.isdir(dst):
        shutil.rmtree(dst)
    folder_layout(dst)
    godot("--export-debug", "Linux Smoke", os.path.join(dst, "StartGame.x86_64"))
    shutil.copy(os.path.join(packs_dir, "PortAurelia.pck"), os.path.join(dst, "Game", "PortAurelia.pck"))
    shutil.copy(os.path.join(packs_dir, "City.pck"), os.path.join(dst, "Assets", "City.pck"))
    exe = os.path.join(dst, "StartGame.x86_64")
    os.chmod(exe, 0o755)
    r = subprocess.run([exe, "--headless", "--", "--smoke-test"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       text=True, timeout=900, cwd=dst)
    lines = [ln for ln in r.stdout.splitlines() if "SMOKE" in ln or "SCRIPT ERROR" in ln]
    for ln in lines:
        print("   " + ln)
    ok = r.returncode == 0 and any("SMOKE OK" in ln for ln in lines)
    print(f"[export] smoke test: {'PASS' if ok else 'FAIL'}")
    if not ok:
        print(r.stdout[-4000:])
    return ok


def main():
    args = sys.argv[1:]
    all_ = not any(a in args for a in ("--release", "--development", "--smoke"))
    write_presets()
    packs = os.path.join(EXPORT, "packs")
    os.makedirs(packs, exist_ok=True)
    godot("--export-pack", "Game Data", os.path.join(packs, "PortAurelia.pck"))
    godot("--export-pack", "City Data", os.path.join(packs, "City.pck"))
    ok = True
    if all_ or "--smoke" in args:
        ok = smoke(packs)
    if all_ or "--development" in args:
        build("Development", packs)
    if all_ or "--release" in args:
        rel = build("Release", packs)
        if all_ or "--zip" in args:
            make_zip(rel, "MyOpenWorldGame.zip")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
