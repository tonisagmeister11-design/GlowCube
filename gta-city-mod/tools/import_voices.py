"""Voice lines (spoken by the city's people): converts audio files to mono Ogg and registers them.

    python3 tools/import_voices.py voice_gang_threat=gang.mp3 voice_police_surrender=police.mp3 ...
    python3 tools/import_voices.py            # only (re)register the voice_*.ogg files that are there

Run it after gen_sounds.py (which rewrites sounds.json). A new name also needs a SoundEvent in ModSounds and a
place in the code where it is played.
"""
import json
import os
import sys

import numpy as np
import soundfile as sf

ASSETS = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "gtacity")
SOUNDS = os.path.join(ASSETS, "sounds")

# subtitle (English, German) per voice line
SUBTITLES = {
    "voice_gang_threat": ("Gang member threatens you", "Gangster droht dir"),
    "voice_police_surrender": ("Officer: give yourself up", "Polizist: Ergib dich"),
    "voice_pedestrian_angry": ("Pedestrian swears at you", "Passant beschimpft dich"),
}


def convert(name, path):
    data, rate = sf.read(path)
    if data.ndim > 1:
        data = data.mean(axis=1)  # positional sounds must be mono
    data = data / (float(np.max(np.abs(data))) or 1.0) * 0.9
    sf.write(os.path.join(SOUNDS, name + ".ogg"), data.astype(np.float32), rate, format="OGG", subtype="VORBIS")


def main(args):
    for arg in args:
        name, path = arg.split("=", 1)
        convert(name, path)
    sounds_path = os.path.join(ASSETS, "sounds.json")
    sounds = json.load(open(sounds_path, encoding="utf-8"))
    names = sorted(f[:-4] for f in os.listdir(SOUNDS) if f.startswith("voice_") and f.endswith(".ogg"))
    for name in names:
        sounds[name] = {"sounds": ["gtacity:" + name], "subtitle": "subtitles.gtacity." + name}
    with open(sounds_path, "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2, ensure_ascii=False)
        f.write("\n")
    for lang, i in (("en_us", 0), ("de_de", 1)):
        path = os.path.join(ASSETS, "lang", lang + ".json")
        table = json.load(open(path, encoding="utf-8"))
        for name in names:
            speaker = name.split("_")[1].capitalize() if name.count("_") >= 2 else ""
            fallback = (speaker + " speaks", speaker + " spricht") if speaker else ("Someone speaks", "Jemand spricht")
            texts = SUBTITLES.get(name, fallback)
            table["subtitles.gtacity." + name] = texts[i]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(table, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("voice lines:", ", ".join(names))


if __name__ == "__main__":
    main(sys.argv[1:])
