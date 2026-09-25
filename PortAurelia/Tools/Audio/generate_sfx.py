"""Procedural sound effects for Port Aurelia (numpy + stdlib wave).

Everything is synthesised - no recorded or third-party audio. NO MUSIC is generated;
Audio/Music/ is reserved for the player's own files.

Usage: python Tools/Audio/generate_sfx.py [output_root]
Writes Game/Audio/SFX/<Category>/<name>.wav (22.05 kHz, 16-bit mono).
"""
import os
import sys
import wave

import numpy as np

SR = 22050
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", "Game", "Audio", "SFX"))
RNG = np.random.default_rng(777)


def save(cat, name, x, norm=0.9):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1.0
    x = x / peak * norm
    d = os.path.join(ROOT, cat)
    os.makedirs(d, exist_ok=True)
    data = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    with wave.open(os.path.join(d, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data.tobytes())


def t(dur):
    return np.arange(int(SR * dur)) / SR


def noise(dur):
    return RNG.standard_normal(int(SR * dur))


def lp(x, cutoff):
    """One-pole low-pass (cutoff in Hz), vectorised via cumulative filter loop in chunks."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def lp_fast(x, cutoff, passes=1):
    # moving-average approximation for long buffers
    n = max(1, int(SR / (cutoff * 2.2)))
    k = np.ones(n) / n
    for _ in range(passes):
        x = np.convolve(x, k, mode="same")
    return x


def hp(x, cutoff):
    return x - lp_fast(x, cutoff)


def env(n, attack, decay, sustain=0.0, curve=4.0):
    tt = np.arange(n) / SR
    e = np.where(tt < attack, tt / max(attack, 1e-4), np.exp(-(tt - attack) * curve / max(decay, 1e-4)))
    return np.maximum(e, sustain * (tt < attack + decay))


def reverb(x, amount=0.3, length=0.6):
    n = int(SR * length)
    ir = RNG.standard_normal(n) * np.exp(-np.arange(n) / SR * 6.0 / length)
    ir = lp_fast(ir, 3000)
    wet = np.convolve(x, ir)
    out = np.zeros(len(wet))
    out[:len(x)] += x
    out += wet / (np.max(np.abs(wet)) or 1) * amount * np.max(np.abs(x))
    return out


def loop_crossfade(x, fade=0.25):
    n = int(SR * fade)
    head = x[:n]
    tail = x[-n:]
    w = np.linspace(0, 1, n)
    x = x[:-n].copy()
    x[:n] = head * w + tail * (1 - w)
    return x


# ------------------------------------------------------------------ footsteps
def footsteps():
    for surf, cut, dec, thump in (("concrete", 2800, 0.06, 0.6), ("grass", 1400, 0.09, 0.3), ("wood", 1800, 0.07, 1.0),
                                  ("metal", 4000, 0.12, 0.5), ("sand", 900, 0.12, 0.2), ("road", 2400, 0.06, 0.6)):
        for v in range(4):
            n = noise(0.22)
            x = lp_fast(n, cut + RNG.uniform(-300, 300))
            x = hp(x, 180) * env(len(x), 0.004, dec)
            tt = t(0.22)
            x += np.sin(2 * np.pi * RNG.uniform(70, 110) * tt) * env(len(x), 0.002, 0.05) * thump
            if surf == "metal":
                x += np.sin(2 * np.pi * RNG.uniform(900, 1400) * tt) * env(len(x), 0.001, 0.15) * 0.2
            if surf == "grass" or surf == "sand":
                x += lp_fast(noise(0.22), 5000) * env(len(x), 0.02, 0.12) * 0.3
            save("Environment", f"step_{surf}_{v}", x, 0.7)


# ------------------------------------------------------------------ weapons
def gunshot(name, body_hz, crack, tail, dur, sub=1.0):
    n = int(SR * dur)
    tt = t(dur)
    c = noise(dur) * env(n, 0.0005, 0.02 + crack * 0.02, curve=5)
    b = lp_fast(noise(dur), 1500) * env(n, 0.001, 0.12 + tail * 0.2)
    s = np.sin(2 * np.pi * body_hz * tt * (1 - tt * 0.8)) * env(n, 0.001, 0.08 + tail * 0.1) * sub
    x = c * 0.7 + b * 1.2 + s * 0.8
    x = reverb(x, 0.35 + tail * 0.3, 0.5 + tail)
    save("Weapons", name, x, 0.95)


def weapons():
    gunshot("pistol", 110, 0.4, 0.3, 0.5)
    gunshot("revolver", 85, 0.8, 0.6, 0.8, 1.3)
    gunshot("smg", 130, 0.3, 0.15, 0.3)
    gunshot("rifle", 95, 0.6, 0.4, 0.6, 1.1)
    gunshot("shotgun", 70, 1.0, 0.7, 0.9, 1.6)
    gunshot("sniper", 60, 1.2, 1.0, 1.4, 1.8)
    # reload: two metallic clicks
    x = np.zeros(int(SR * 0.6))
    for (pos, f) in ((0.05, 2400), (0.35, 1800)):
        i = int(SR * pos)
        n = int(SR * 0.08)
        tt = np.arange(n) / SR
        x[i:i + n] += (np.sin(2 * np.pi * f * tt) + lp_fast(RNG.standard_normal(n), 5000)) * np.exp(-tt * 60)
    save("Weapons", "reload", x, 0.6)
    n = int(SR * 0.1)
    tt = np.arange(n) / SR
    save("Weapons", "dry_fire", np.sin(2 * np.pi * 2000 * tt) * np.exp(-tt * 80), 0.5)
    save("Weapons", "swing", hp(lp_fast(noise(0.3), 2500), 400) * env(int(SR * 0.3), 0.08, 0.12), 0.4)
    ph = np.sin(2 * np.pi * 90 * t(0.25)) * env(int(SR * 0.25), 0.002, 0.08) + lp_fast(noise(0.25), 1200) * env(int(SR * 0.25), 0.001, 0.05)
    save("Weapons", "punch_hit", ph, 0.8)
    for v in range(3):
        n = int(SR * 0.3)
        tt = np.arange(n) / SR
        save("Weapons", f"impact_{v}", (hp(noise(0.3), 800) * np.exp(-tt * 40) + np.sin(2 * np.pi * RNG.uniform(1500, 3000) * tt) * np.exp(-tt * 50) * 0.3), 0.5)


# ------------------------------------------------------------------ vehicles
def vehicles():
    # engine loop: harmonic stack at 50 Hz fundamental (pitched in game)
    dur = 2.0
    tt = t(dur)
    f0 = 50.0
    x = np.zeros_like(tt)
    for h, a in ((1, 1.0), (2, 0.7), (3, 0.45), (4, 0.3), (6, 0.15), (8, 0.08)):
        x += np.sin(2 * np.pi * f0 * h * tt + RNG.uniform(0, 6)) * a
    # combustion pulses
    x *= 0.7 + 0.3 * np.sign(np.sin(2 * np.pi * f0 * 0.5 * tt))
    x += lp_fast(noise(dur), 900) * 0.25
    save("Vehicles", "engine_loop", loop_crossfade(x), 0.8)
    # higher, raspier sports engine
    x = np.zeros_like(tt)
    for h, a in ((1, 1.0), (2, 0.8), (3, 0.6), (5, 0.4), (7, 0.25)):
        x += np.sign(np.sin(2 * np.pi * 70 * h * tt)) * a * 0.3 + np.sin(2 * np.pi * 70 * h * tt) * a
    x += lp_fast(noise(dur), 2000) * 0.3
    save("Vehicles", "engine_sport_loop", loop_crossfade(lp_fast(x, 3500)), 0.8)
    # truck/diesel
    x = np.zeros_like(tt)
    for h, a in ((1, 1.0), (2, 0.9), (3, 0.7), (4, 0.5)):
        x += np.sin(2 * np.pi * 32 * h * tt) * a
    x *= 0.6 + 0.4 * (np.sin(2 * np.pi * 16 * tt) > 0)
    x += lp_fast(noise(dur), 600) * 0.4
    save("Vehicles", "engine_diesel_loop", loop_crossfade(x), 0.8)
    # motorcycle
    x = np.zeros_like(tt)
    for h, a in ((1, 1.0), (2, 0.6), (3, 0.5), (4, 0.3)):
        x += np.sin(2 * np.pi * 90 * h * tt) * a
    x *= 0.5 + 0.5 * (np.sin(2 * np.pi * 45 * tt) > 0)
    save("Vehicles", "engine_bike_loop", loop_crossfade(lp_fast(x, 3000)), 0.8)
    # tyre screech loop
    x = hp(noise(1.5), 1200)
    x = lp_fast(x, 3500) * (0.8 + 0.2 * np.sin(2 * np.pi * 7 * t(1.5)))
    x += np.sin(2 * np.pi * (1800 + 200 * np.sin(2 * np.pi * 3 * t(1.5))) * t(1.5)) * 0.25
    save("Vehicles", "tire_screech", loop_crossfade(x), 0.6)
    # rolling road noise loop
    save("Vehicles", "road_noise", loop_crossfade(lp_fast(noise(2.0), 400)), 0.6)
    # horn (two tone)
    tt = t(0.8)
    x = (np.sign(np.sin(2 * np.pi * 400 * tt)) + np.sign(np.sin(2 * np.pi * 500 * tt))) * 0.5
    x = lp_fast(x, 2500) * env(len(tt), 0.01, 0.8, 0.9, 0.5)
    save("Vehicles", "horn", x, 0.6)
    # crash
    for v in range(3):
        dur = 1.4
        n = int(SR * dur)
        tt = t(dur)
        x = lp_fast(noise(dur), 2500) * env(n, 0.002, 0.3)
        x += np.sin(2 * np.pi * 60 * tt) * env(n, 0.002, 0.2) * 1.5
        for k in range(6):
            f = RNG.uniform(300, 2200)
            x += np.sin(2 * np.pi * f * tt) * np.exp(-tt * RNG.uniform(4, 10)) * 0.25 * (tt > RNG.uniform(0, 0.1))
        save("Vehicles", f"crash_{v}", reverb(x, 0.2, 0.4), 0.95)
    # glass break
    dur = 1.2
    n = int(SR * dur)
    tt = t(dur)
    x = hp(noise(dur), 2500) * env(n, 0.001, 0.25)
    for k in range(25):
        start = RNG.uniform(0, 0.6)
        f = RNG.uniform(2500, 7000)
        x += np.sin(2 * np.pi * f * tt) * np.exp(-np.maximum(tt - start, 0) * 30) * (tt > start) * 0.15
    save("Vehicles", "glass_break", x, 0.8)
    # doors
    tt = t(0.4)
    save("Vehicles", "door_close", (lp_fast(noise(0.4), 800) + np.sin(2 * np.pi * 80 * tt) * 1.5) * env(len(tt), 0.002, 0.1), 0.8)
    save("Vehicles", "door_open", hp(lp_fast(noise(0.4), 3000), 500) * env(len(tt), 0.01, 0.15), 0.5)
    # siren wail loop (period 4 s)
    dur = 4.0
    tt = t(dur)
    f = 700 + 500 * (0.5 - 0.5 * np.cos(2 * np.pi * tt / dur))
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) + 0.3 * np.sin(2 * ph)
    save("Vehicles", "siren_wail", x, 0.7)
    # yelp loop
    dur = 0.5
    tt = t(dur)
    f = 700 + 900 * (tt / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    save("Vehicles", "siren_yelp", np.sin(ph) + 0.3 * np.sin(2 * ph), 0.7)
    # indicator tick
    tt = t(0.05)
    save("Vehicles", "indicator", np.sin(2 * np.pi * 2500 * tt) * np.exp(-tt * 120), 0.4)
    # explosion
    dur = 3.0
    n = int(SR * dur)
    tt = t(dur)
    x = lp_fast(noise(dur), 700) * env(n, 0.005, 1.2) * 1.5 + np.sin(2 * np.pi * 40 * tt * (1 - tt * 0.2)) * env(n, 0.002, 0.8) * 2
    x += hp(noise(dur), 2000) * env(n, 0.001, 0.15) * 0.6
    save("Weapons", "explosion", reverb(x, 0.4, 1.2), 1.0)


# ------------------------------------------------------------------ environment
def environment():
    dur = 6.0
    tt = t(dur)
    rain = hp(lp_fast(noise(dur), 6000), 800) * 0.6
    drops = np.zeros(len(tt))
    for k in range(900):
        i = RNG.integers(0, len(tt) - 200)
        m = np.arange(200)
        drops[i:i + 200] += np.sin(2 * np.pi * RNG.uniform(2000, 6000) * m / SR) * np.exp(-m / 30) * RNG.uniform(0.1, 0.4)
    save("Environment", "rain_loop", loop_crossfade(rain + drops), 0.6)
    save("Environment", "rain_heavy_loop", loop_crossfade(lp_fast(noise(dur), 3000) * 0.8 + drops * 0.5), 0.8)
    wind = lp_fast(noise(dur), 300) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.2 * tt + 1))
    save("Environment", "wind_loop", loop_crossfade(wind), 0.5)
    # distant city: traffic rumble + occasional horns + faint hum
    city = lp_fast(noise(dur), 250) * 1.0 + np.sin(2 * np.pi * 60 * tt) * 0.05
    for k in range(4):
        s = RNG.uniform(0.2, dur - 1.0)
        m = (tt > s) & (tt < s + RNG.uniform(0.2, 0.5))
        city += np.sin(2 * np.pi * RNG.uniform(350, 520) * tt) * m * 0.06
    save("Environment", "city_loop", loop_crossfade(city), 0.5)
    # ocean waves
    waves = lp_fast(noise(dur), 800) * (0.4 + 0.6 * np.maximum(0, np.sin(2 * np.pi * tt / 6.0)) ** 2)
    waves += hp(lp_fast(noise(dur), 4000), 1500) * 0.2 * np.maximum(0, np.sin(2 * np.pi * tt / 6.0 - 0.5)) ** 4
    save("Environment", "ocean_loop", loop_crossfade(waves), 0.5)
    # birds (day ambience)
    birds = np.zeros(len(tt))
    for k in range(18):
        s = RNG.uniform(0, dur - 0.5)
        n = int(SR * RNG.uniform(0.08, 0.25))
        i = int(s * SR)
        m = np.arange(n) / SR
        f = RNG.uniform(2500, 4500) + 800 * np.sin(2 * np.pi * RNG.uniform(10, 25) * m)
        birds[i:i + n] += np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * m / m[-1]) * 0.3
    save("Environment", "birds_loop", loop_crossfade(birds + lp_fast(noise(dur), 200) * 0.05), 0.4)
    # crickets (night ambience)
    cr = np.zeros(len(tt))
    for k in range(40):
        s = RNG.uniform(0, dur - 0.3)
        i = int(s * SR)
        n = int(SR * 0.2)
        m = np.arange(n) / SR
        cr[i:i + n] += np.sin(2 * np.pi * 4200 * m) * (np.sin(2 * np.pi * 40 * m) > 0.3) * 0.2
    save("Environment", "night_loop", loop_crossfade(cr + lp_fast(noise(dur), 150) * 0.1), 0.35)
    # thunder
    dur = 4.0
    n = int(SR * dur)
    x = lp_fast(noise(dur), 200, 2) * env(n, 0.05, 2.5) * (1 + 0.5 * np.sin(2 * np.pi * 3 * t(dur)))
    x += hp(noise(dur), 1000) * env(n, 0.002, 0.08) * 0.3
    save("Environment", "thunder", reverb(x, 0.5, 1.5), 1.0)
    # water splash
    x = lp_fast(noise(0.8), 2500) * env(int(SR * 0.8), 0.005, 0.3)
    save("Environment", "splash", x, 0.6)


# ------------------------------------------------------------------ UI
def tone(freqs, dur, decay=6.0, shape="sine"):
    tt = t(dur)
    x = np.zeros_like(tt)
    for f in freqs:
        s = np.sin(2 * np.pi * f * tt)
        if shape == "tri":
            s = 2 / np.pi * np.arcsin(s)
        x += s
    return x * np.exp(-tt * decay) * np.minimum(1, tt * 400)


def ui():
    save("UI", "click", tone([1200], 0.06, 50), 0.5)
    save("UI", "hover", tone([900], 0.04, 60), 0.3)
    save("UI", "back", tone([700, 500], 0.1, 30), 0.4)
    save("UI", "select", tone([880, 1320], 0.15, 20), 0.5)
    save("UI", "notify", np.concatenate([tone([988], 0.09, 25), tone([1319], 0.2, 12)]), 0.5)
    cash = np.concatenate([tone([2000, 3000], 0.05, 40, "tri"), tone([2600], 0.25, 10)])
    rattle = np.pad(lp_fast(noise(0.1), 5000) * 0.3, (0, len(cash)))[:len(cash)]
    cash = cash + rattle
    save("UI", "money", cash, 0.5)
    # mission passed stinger (short, not music)
    seq = [523, 659, 784, 1047]
    x = np.concatenate([tone([f, f * 1.5], 0.14, 8, "tri") for f in seq[:-1]] + [tone([1047, 1319, 1568], 0.8, 3, "tri")])
    save("UI", "mission_passed", reverb(x, 0.3, 0.6), 0.6)
    seq = [392, 370, 349, 262]
    x = np.concatenate([tone([f], 0.2, 6, "tri") for f in seq[:-1]] + [tone([262, 311], 0.8, 3, "tri")])
    save("UI", "mission_failed", reverb(x, 0.3, 0.6), 0.6)
    save("UI", "wasted", reverb(tone([110, 116], 1.5, 1.5), 0.4, 1.0), 0.7)
    ring = np.concatenate([tone([1300, 1700], 0.4, 1, "tri"), np.zeros(int(SR * 0.2))] * 2)
    save("UI", "phone_ring", ring, 0.5)
    save("UI", "message", np.concatenate([tone([1568], 0.08, 30), tone([2093], 0.15, 20)]), 0.5)
    save("UI", "checkpoint", np.concatenate([tone([1047], 0.07, 30), tone([1568], 0.18, 15)]), 0.5)
    save("UI", "wanted_up", tone([440, 660], 0.3, 8, "tri"), 0.4)


# ------------------------------------------------------------------ voice (synthetic vocal sounds)
def vowel(f0, formants, dur, jitter=0.02):
    tt = t(dur)
    f = f0 * (1 + jitter * np.sin(2 * np.pi * 5 * tt)) * (1 - 0.25 * tt / dur)
    ph = 2 * np.pi * np.cumsum(f) / SR
    src = np.zeros_like(tt)
    for h in range(1, 30):
        src += np.sin(h * ph) / h
    out = np.zeros_like(tt)
    for (fc, bw, g) in formants:
        # resonator approximated by band-pass (difference of low-passes)
        band = lp_fast(src, fc + bw) - lp_fast(src, max(50, fc - bw))
        out += band * g
    return out * env(len(tt), 0.02, dur * 0.8, curve=3)


def voice():
    for i, (f0, form) in enumerate(((120, [(700, 150, 1.0), (1200, 200, 0.6)]), (140, [(500, 120, 1.0), (1000, 200, 0.5)]),
                                    (210, [(800, 150, 1.0), (1400, 250, 0.6)]))):
        save("Voice", f"pain_{i}", vowel(f0, form, 0.35), 0.7)
    save("Voice", "scream_0", vowel(420, [(900, 200, 1.0), (1600, 300, 0.6)], 0.9, 0.08) + hp(noise(0.9), 3000) * 0.05, 0.7)
    save("Voice", "scream_1", vowel(300, [(800, 200, 1.0), (1300, 300, 0.6)], 0.8, 0.1), 0.7)
    save("Voice", "shout_0", vowel(160, [(650, 150, 1.0), (1100, 200, 0.6)], 0.5, 0.05), 0.7)
    save("Voice", "death_0", vowel(110, [(600, 150, 1.0), (1000, 200, 0.4)], 0.6), 0.6)


def main():
    global ROOT
    if len(sys.argv) > 1:
        ROOT = sys.argv[1]
    print("sfx ->", ROOT)
    footsteps()
    weapons()
    vehicles()
    environment()
    ui()
    voice()
    manifest = {}
    for cat in sorted(os.listdir(ROOT)):
        d = os.path.join(ROOT, cat)
        if os.path.isdir(d):
            for f in sorted(os.listdir(d)):
                if f.endswith(".wav"):
                    manifest[f[:-4]] = f"res://Audio/SFX/{cat}/{f}"
    import json
    with open(os.path.join(ROOT, "manifest.json"), "w") as fh:
        json.dump(manifest, fh, indent=0)
    print("sounds:", len(manifest))


if __name__ == "__main__":
    main()
