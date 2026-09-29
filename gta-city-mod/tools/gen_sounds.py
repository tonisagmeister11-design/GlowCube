"""Synthesizes all of the mod's sounds (mono Ogg Vorbis, so Minecraft plays them positionally).

    pip install numpy scipy soundfile
    python3 gen_sounds.py

Writes src/main/resources/assets/gtacity/sounds/*.ogg and assets/gtacity/sounds.json. Loops (engines, siren,
rotor, skid) are built from whole periods so they repeat without a click.
"""
import json
import os

import numpy as np
import soundfile as sf
from scipy.signal import lfilter

RATE = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "gtacity")
OUT = os.path.join(ROOT, "sounds")
rng = np.random.default_rng(1234)


def t(seconds):
    return np.arange(int(RATE * seconds)) / RATE


def noise(seconds):
    return rng.uniform(-1.0, 1.0, int(RATE * seconds))


def lowpass(x, cutoff):
    """One-pole low pass, run twice for a softer slope."""
    a = np.exp(-2.0 * np.pi * cutoff / RATE)
    for _ in range(2):
        x = lfilter([1 - a], [1, -a], x)
    return x


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def bandpass(x, low, high):
    return lowpass(highpass(x, low), high)


def env(n, attack, decay):
    """Exponential decay envelope with a short linear attack (times in seconds)."""
    tt = np.arange(n) / RATE
    e = np.exp(-tt / decay)
    a = int(RATE * attack)
    if a > 0:
        e[:a] *= np.linspace(0, 1, a)
    return e


def pad(x, seconds):
    n = int(RATE * seconds)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def mix(*parts):
    n = max(len(p) for p in parts)
    return sum(pad(p, n / RATE) for p in parts)


def delay(x, seconds, gain):
    d = int(RATE * seconds)
    return np.concatenate([np.zeros(d), x * gain])


def normalize(x, peak=0.72):
    return x / (np.max(np.abs(x)) + 1e-9) * peak


def saturate(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


# ------------------------------------------------------------------ weapons

def gunshot(crack_tau, thump_hz, thump_tau, tail_tau, length, crack_cut=6000, drive=2.5, body_gain=0.9):
    n = int(RATE * length)
    crack = highpass(noise(length), 900) * env(n, 0.0005, crack_tau)
    crack = lowpass(crack, crack_cut)
    tt = t(length)
    thump = np.sin(2 * np.pi * thump_hz * tt * (1 - tt * 0.8)) * env(n, 0.001, thump_tau) * body_gain
    tail = lowpass(noise(length), 1800) * env(n, 0.01, tail_tau) * 0.35
    return normalize(saturate(crack + thump + tail, drive))


def echo(x, times, gains, cut=2500):
    out = x.copy()
    for d, g in zip(times, gains):
        out = mix(out, lowpass(delay(x, d, g), cut))
    return normalize(out)


def click(freq, length=0.03, gain=1.0):
    n = int(RATE * length)
    tt = t(length)
    ring = np.sin(2 * np.pi * freq * tt) * env(n, 0.0002, 0.006)
    tick = highpass(noise(length), 3000) * env(n, 0.0001, 0.003)
    return (ring * 0.6 + tick) * gain


def at(x, seconds, total):
    out = np.zeros(int(RATE * total))
    s = int(RATE * seconds)
    out[s:s + len(x)] += x[:len(out) - s]
    return out


def shotgun():
    boom = gunshot(0.02, 70, 0.12, 0.35, 0.5, crack_cut=4500, drive=3.0, body_gain=1.3)
    pump = at(click(1800, 0.05), 0.55, 1.0) + at(click(1400, 0.06), 0.72, 1.0)
    return normalize(mix(boom, pump * 0.5))


def rpg_launch():
    length = 1.1
    n = int(RATE * length)
    tt = t(length)
    whoosh = noise(length)
    # sweep a band upwards: mix low and high passed versions with a moving crossfade
    lo = bandpass(whoosh, 300, 1200)
    hi = bandpass(whoosh, 1500, 5000)
    fade = np.clip(tt / length, 0, 1)
    sweep = (lo * (1 - fade) + hi * fade) * env(n, 0.02, 0.5)
    thud = np.sin(2 * np.pi * 55 * tt) * env(n, 0.002, 0.15)
    return normalize(saturate(sweep * 1.4 + thud, 2.0))


def explosion():
    length = 2.2
    n = int(RATE * length)
    tt = t(length)
    crack = highpass(noise(length), 1200) * env(n, 0.0005, 0.03)
    rumble = lowpass(noise(length), 220) * env(n, 0.005, 0.7) * 3.0
    boom = np.sin(2 * np.pi * 42 * tt * (1 - tt * 0.3)) * env(n, 0.002, 0.35)
    debris = bandpass(noise(length), 800, 3000) * env(n, 0.05, 0.4) * 0.3
    return normalize(saturate(crack + rumble + boom + debris, 2.5), 0.55)


def reload_sound():
    total = 0.75
    return normalize(at(click(1200, 0.05), 0.0, total) + at(click(900, 0.06), 0.28, total)
                     + at(click(2200, 0.04), 0.52, total) * 1.2)


# ------------------------------------------------------------------ vehicles

def engine(base, seconds, harmonics, raspy):
    """Seamless engine loop. base must make whole cycles in `seconds`."""
    tt = t(seconds)
    sig = np.zeros_like(tt)
    for k, g in harmonics:
        sig += g * np.sin(2 * np.pi * base * k * tt + k * 0.7)
    firing = 0.55 + 0.45 * np.maximum(0, np.sin(2 * np.pi * base * 2 * tt)) ** 3
    sig *= firing
    grit = bandpass(noise(seconds), 200, 1800) * raspy
    # make the noise periodic: crossfade its end into its start
    fadelen = int(RATE * 0.05)
    grit[:fadelen] = grit[:fadelen] * np.linspace(0, 1, fadelen) + grit[-fadelen:] * np.linspace(1, 0, fadelen)
    return normalize(saturate(sig + grit, 1.8), 0.8)


def seamless(x, fade=0.08):
    """Crossfades the end of a noisy loop into its start so it repeats without a click."""
    n = int(RATE * fade)
    head, body, tail = x[:n], x[n:-n], x[-n:]
    w = np.linspace(0, 1, n)
    return np.concatenate([tail * (1 - w) + head * w, body])


def siren(seconds=2.0):
    tt = t(seconds)
    f = 750 + 450 * np.sin(2 * np.pi * tt / seconds - np.pi / 2)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    phase *= (2 * np.pi * round(phase[-1] / (2 * np.pi))) / phase[-1]  # whole cycles -> seamless
    sig = np.sin(phase) + 0.3 * np.sin(2 * phase) + 0.15 * np.sin(3 * phase)
    return normalize(saturate(sig, 1.5), 0.7)


def rotor(seconds=1.0):
    seconds += 0.08
    tt = t(seconds)
    blade = 12.0  # chops per second (whole number -> seamless)
    chop = np.maximum(0, np.sin(2 * np.pi * blade * tt)) ** 6
    body = lowpass(noise(seconds), 400) * 3.0 * chop
    whine = 0.12 * np.sin(2 * np.pi * 420 * tt) + 0.08 * np.sin(2 * np.pi * 840 * tt)
    thump = np.sin(2 * np.pi * 24 * tt) * chop
    return normalize(seamless(body + thump + whine), 0.7)


def skid(seconds=1.0):
    seconds += 0.08
    tt = t(seconds)
    squeal = bandpass(noise(seconds), 1800, 3800) * (0.7 + 0.3 * np.sin(2 * np.pi * 7 * tt))
    tone = 0.25 * np.sin(2 * np.pi * 2300 * tt)
    return normalize(seamless(squeal + tone), 0.55)


def horn():
    length = 0.7
    n = int(RATE * length)
    tt = t(length)
    sig = np.sign(np.sin(2 * np.pi * 415 * tt)) + np.sign(np.sin(2 * np.pi * 523 * tt))
    sig = lowpass(sig, 2500)
    e = np.ones(n)
    e[:400] = np.linspace(0, 1, 400)
    e[-3000:] = np.linspace(1, 0, 3000)
    return normalize(sig * e, 0.8)


def cash():
    length = 0.9
    n = int(RATE * length)
    tt = t(length)
    bell = (np.sin(2 * np.pi * 1318 * tt) + 0.5 * np.sin(2 * np.pi * 2637 * tt)) * env(n, 0.001, 0.25)
    bell2 = at((np.sin(2 * np.pi * 1760 * tt) + 0.4 * np.sin(2 * np.pi * 3520 * tt)) * env(n, 0.001, 0.35), 0.09,
               length)
    drawer = at(click(700, 0.06, 1.5), 0.0, length)
    return normalize(bell + bell2 + drawer)


SOUNDS = {
    # name: (generator, subtitle de, subtitle en)
    "gun_pistol": (lambda: gunshot(0.018, 150, 0.06, 0.25, 0.6), "Pistolenschuss", "Pistol shot"),
    "gun_smg": (lambda: gunshot(0.010, 210, 0.035, 0.12, 0.3, crack_cut=7500), "SMG-Schuss", "SMG shot"),
    "gun_rifle": (lambda: gunshot(0.022, 95, 0.08, 0.35, 0.7, drive=3.0), "Gewehrschuss", "Rifle shot"),
    "gun_shotgun": (shotgun, "Schrotflinte", "Shotgun blast"),
    "gun_sniper": (lambda: echo(gunshot(0.03, 80, 0.1, 0.5, 0.9, drive=3.5), [0.35, 0.8], [0.45, 0.25]),
                   "Scharfschützenschuss", "Sniper shot"),
    "gun_minigun": (lambda: gunshot(0.006, 240, 0.02, 0.05, 0.12, crack_cut=8000), "Minigun", "Minigun"),
    "rpg_launch": (rpg_launch, "Rakete abgefeuert", "Rocket launched"),
    "explosion": (explosion, "Explosion", "Explosion"),
    "reload": (reload_sound, "Nachladen", "Reloading"),
    "dry_fire": (lambda: normalize(click(2500, 0.05)), "Klick - leer", "Click - empty"),
    "horn": (horn, "Hupe", "Car horn"),
    "engine": (lambda: engine(60, 1.0, [(1, 1.0), (2, 0.6), (3, 0.35), (4, 0.2), (6, 0.1)], 0.15),
               "Motor", "Engine"),
    "engine_super": (lambda: engine(80, 1.0, [(1, 0.8), (2, 0.7), (3, 0.5), (4, 0.4), (5, 0.3), (8, 0.15)], 0.35),
                     "Sportmotor", "Sports engine"),
    "siren": (siren, "Polizeisirene", "Police siren"),
    "heli_rotor": (rotor, "Hubschrauber", "Helicopter"),
    "skid": (skid, "Quietschende Reifen", "Screeching tires"),
    "cash": (cash, "Geld", "Cash"),
}


def main():
    os.makedirs(OUT, exist_ok=True)
    sounds_json = {}
    lang = {"de_de": {}, "en_us": {}}
    for name, (gen, de, en) in SOUNDS.items():
        data = gen().astype(np.float32)
        sf.write(os.path.join(OUT, name + ".ogg"), data, RATE, format="OGG", subtype="VORBIS")
        sounds_json[name] = {"sounds": ["gtacity:" + name], "subtitle": "subtitles.gtacity." + name}
        lang["de_de"]["subtitles.gtacity." + name] = de
        lang["en_us"]["subtitles.gtacity." + name] = en
        print("wrote", name, "%.2fs" % (len(data) / RATE))
    with open(os.path.join(ROOT, "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds_json, f, indent=2)
    # merge the subtitles into the language files written by gen_resources.py
    for code, entries in lang.items():
        path = os.path.join(ROOT, "lang", code + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        data.update(entries)
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")


if __name__ == "__main__":
    main()
