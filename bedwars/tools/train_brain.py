#!/usr/bin/env python3
"""
Trainiert das Strategie-Netz der Bots (StrategyNet, 36 -> 24 -> 9).

Voraussetzung: ein laufender Fabric-Server mit der Mod, dessen Konsole ueber eine FIFO
erreichbar ist (z.B. `tail -f console.in | java -jar fabric-server-launch.jar nogui`).

  1. teacher  - spielt Runden mit dem alten Regelwerk und zeichnet jede Entscheidung auf
  2. clone    - lernt diese Entscheidungen nach (Behavior Cloning, Softmax-Klassifikator)
  3. evolve   - verbessert das Netz durch Selbstspiel: Evolutionsstrategie mit gespiegelten
                Stoerungen, jede Runde spielen fuenf Varianten gegeneinander
  4. eval     - laesst das neue Netz gegen das Regelwerk antreten

Beispiel:
  python3 train_brain.py --console server/console.in --work /tmp/bw all
"""
import argparse
import json
import os
import random
import sys
import time

import numpy as np

IN, HID, OUT = 36, 24, 9
PLANS = ["GATHER", "SHOP", "FORTIFY", "DEFEND", "RAID", "MID", "HUNT", "ESCORT", "REGROUP"]


def send(console, cmd):
    with open(console, "w") as f:
        f.write(cmd + "\n")


def play(args, mode, teams=None, timeout=240):
    """Spielt eine Runde im Zeitraffer und liefert das Ergebnis (oder None)."""
    d = os.path.abspath(os.path.join(args.work, "game"))
    os.makedirs(d, exist_ok=True)
    res = os.path.join(d, "result.json")
    tf = os.path.join(d, "teams.json")
    if os.path.exists(res):
        os.remove(res)
    if teams is None:
        if os.path.exists(tf):
            os.remove(tf)
    else:
        with open(tf, "w") as f:
            json.dump(teams, f)
    if mode == "record":
        # Lehrer-Daten landen in game/teacher.jsonl
        pass
    send(args.console, f"bedwars {mode} {d}")
    time.sleep(1.5)
    send(args.console, "tick sprint 60000")
    start = time.time()
    while time.time() - start < timeout:
        if os.path.exists(res):
            time.sleep(0.3)
            send(args.console, "tick sprint stop")
            with open(res) as f:
                return json.load(f)
        time.sleep(0.5)
    send(args.console, "tick sprint stop")
    send(args.console, "bedwars stop")
    return None


# ------------------------------------------------------------------ Netz

def net_to_json(p, mean, std, name):
    w1, b1, w2, b2 = p
    return {"in": IN, "hidden": HID, "out": OUT, "name": name,
            "w1": w1.reshape(-1).round(5).tolist(), "b1": b1.round(5).tolist(),
            "w2": w2.reshape(-1).round(5).tolist(), "b2": b2.round(5).tolist(),
            "mean": mean.round(5).tolist(), "std": std.round(5).tolist()}


def json_to_net(o):
    w1 = np.array(o["w1"]).reshape(IN, HID)
    w2 = np.array(o["w2"]).reshape(HID, OUT)
    return [w1, np.array(o["b1"]), w2, np.array(o["b2"])], np.array(o["mean"]), np.array(o["std"])


def flat(p):
    return np.concatenate([x.reshape(-1) for x in p])


def unflat(v):
    i = 0
    out = []
    for shape in [(IN, HID), (HID,), (HID, OUT), (OUT,)]:
        n = int(np.prod(shape))
        out.append(v[i:i + n].reshape(shape))
        i += n
    return out


# ------------------------------------------------------------------ 1+2: Lehrer und Nachahmen

def teacher(args):
    path = os.path.join(args.work, "game", "teacher.jsonl")
    if os.path.exists(path) and not args.append:
        os.remove(path)
    for g in range(args.games):
        r = play(args, "record")
        n = sum(1 for _ in open(path)) if os.path.exists(path) else 0
        print(f"Lehrer-Runde {g + 1}/{args.games}: {'ok' if r else 'Zeitueberschreitung'}, {n} Entscheidungen", flush=True)


def clone(args):
    path = os.path.join(args.work, "game", "teacher.jsonl")
    X, Y = [], []
    for line in open(path):
        o = json.loads(line)
        X.append(o["x"])
        Y.append(o["a"])
    X = np.array(X, dtype=np.float64)
    Y = np.array(Y)
    mean = X.mean(0)
    std = X.std(0) + 1e-3
    Xn = (X - mean) / std
    rng = np.random.default_rng(1)
    idx = rng.permutation(len(X))
    cut = int(len(X) * 0.85)
    tr, va = idx[:cut], idx[cut:]
    w1 = rng.normal(0, 1 / np.sqrt(IN), (IN, HID))
    b1 = np.zeros(HID)
    w2 = rng.normal(0, 1 / np.sqrt(HID), (HID, OUT))
    b2 = np.zeros(OUT)
    params = [w1, b1, w2, b2]
    m = [np.zeros_like(p) for p in params]
    v = [np.zeros_like(p) for p in params]
    lr, step = 0.01, 0
    counts = np.bincount(Y[tr], minlength=OUT) + 1
    cw = (counts.sum() / counts) ** 0.5
    cw = cw / cw.mean()

    def fwd(x):
        h = np.tanh(x @ params[0] + params[1])
        z = h @ params[2] + params[3]
        return h, z

    for epoch in range(args.epochs):
        rng.shuffle(tr)
        for s in range(0, len(tr), 256):
            bi = tr[s:s + 256]
            x, y = Xn[bi], Y[bi]
            h, z = fwd(x)
            z = z - z.max(1, keepdims=True)
            pz = np.exp(z)
            pz /= pz.sum(1, keepdims=True)
            g = pz.copy()
            g[np.arange(len(y)), y] -= 1
            g *= cw[y][:, None] / len(y)
            gw2 = h.T @ g + 1e-4 * params[2]
            gb2 = g.sum(0)
            gh = g @ params[2].T * (1 - h ** 2)
            gw1 = x.T @ gh + 1e-4 * params[0]
            gb1 = gh.sum(0)
            step += 1
            for i, gr in enumerate([gw1, gb1, gw2, gb2]):
                m[i] = 0.9 * m[i] + 0.1 * gr
                v[i] = 0.999 * v[i] + 0.001 * gr ** 2
                params[i] -= lr * (m[i] / (1 - 0.9 ** step)) / (np.sqrt(v[i] / (1 - 0.999 ** step)) + 1e-8)
        _, zt = fwd(Xn[va])
        acc = (zt.argmax(1) == Y[va]).mean()
        if epoch % 5 == 0 or epoch == args.epochs - 1:
            print(f"Epoche {epoch}: Treffergenauigkeit auf ungesehenen Daten {acc:.3f}", flush=True)
    out = net_to_json(params, mean, std, "nachgeahmt")
    with open(os.path.join(args.work, "brain_clone.json"), "w") as f:
        json.dump(out, f)
    print(f"{len(X)} Beispiele, Verteilung: " + ", ".join(f"{PLANS[i]} {c}" for i, c in enumerate(np.bincount(Y, minlength=OUT))))


# ------------------------------------------------------------------ 3: Evolution im Selbstspiel

PLACE_POINTS = {1: 4.0, 2: 2.5, 3: 1.5, 4: 0.7, 5: 0.0}


def fitness(team):
    return PLACE_POINTS[team["place"]] + 0.6 * team["beds"] + 0.12 * team["finals"] + 0.01 * team["kills"]


def evolve(args):
    base_path = os.path.join(args.work, args.start)
    with open(base_path) as f:
        params, mean, std = json_to_net(json.load(f))
    full = flat(params)
    # Nur die Ausgangs-Neigungen (b2) oder alle Gewichte?
    idx = np.arange(full.size - OUT, full.size) if args.bias_only else np.arange(full.size)
    theta = full[idx].copy()
    rng = np.random.default_rng(args.seed)
    log = open(os.path.join(args.work, "evolve.log"), "a")
    log.write(f"--- start {args.start} bias_only={args.bias_only} vs_rules={args.vs_rules}\n")
    pairs = args.pairs
    best_theta, best_score = theta.copy(), -1e9

    def make(t):
        v = full.copy()
        v[idx] = t
        return v

    for gen in range(args.generations):
        eps = rng.normal(0, 1, (pairs, theta.size))
        cands = []
        for k in range(pairs):
            cands.append(theta + args.sigma * eps[k])
            cands.append(theta - args.sigma * eps[k])
        scores = [[] for _ in cands]
        per_game = 2 if args.vs_rules else 5
        order = list(range(len(cands))) * args.repeats
        random.shuffle(order)
        while len(order) % per_game:
            order.append(random.randrange(len(cands)))
        for g in range(0, len(order), per_game):
            group = order[g:g + per_game]
            slots = random.sample(range(5), per_game)
            teams = [None] * 5
            for c, sl in zip(group, slots):
                teams[sl] = net_to_json(unflat(make(cands[c])), mean, std, f"g{gen}c{c}")
            r = play(args, "train", teams)
            if r is None:
                print("Runde hing - uebersprungen", flush=True)
                continue
            for t in r["teams"]:
                if t["team"] in slots:
                    scores[group[slots.index(t["team"])]].append(fitness(t))
        f = np.array([np.mean(s) if s else np.nan for s in scores])
        f = np.where(np.isnan(f), np.nanmean(f), f)
        ranks = f.argsort().argsort() / (len(f) - 1) - 0.5
        grad = np.zeros_like(theta)
        for k in range(pairs):
            grad += (ranks[2 * k] - ranks[2 * k + 1]) * eps[k]
        theta = theta + args.lr / pairs * grad
        if f.mean() > best_score:
            best_score, best_theta = f.mean(), theta.copy()
        line = f"Generation {gen + 1}: Fitness mittel {f.mean():.2f}, beste {f.max():.2f}" + (
            "  Neigungen " + " ".join(f"{PLANS[i][:4]}{theta[i]:+.2f}" for i in range(OUT)) if args.bias_only else "")
        print(line, flush=True)
        log.write(line + "\n")
        log.flush()
        with open(os.path.join(args.work, args.out), "w") as fo:
            json.dump(net_to_json(unflat(make(theta)), mean, std, f"{args.out}-g{gen + 1}"), fo)


# ------------------------------------------------------------------ 4: Vergleich

def evaluate(args):
    with open(os.path.join(args.work, args.candidate)) as f:
        cand = json.load(f)
    base = None
    if args.baseline:
        with open(os.path.join(args.work, args.baseline)) as f:
            base = json.load(f)
    places, beds = {"netz": [], "regeln": []}, {"netz": [], "regeln": []}
    for g in range(args.games):
        slots = random.sample(range(5), 5)
        net_teams = set(slots[:2] if g % 2 == 0 else slots[:3])
        teams = [cand if i in net_teams else base for i in range(5)]
        r = play(args, "train", teams)
        if r is None:
            continue
        for t in r["teams"]:
            k = "netz" if t["team"] in net_teams else "regeln"
            places[k].append(t["place"])
            beds[k].append(t["beds"])
        print(f"Vergleich {g + 1}: Sieger {'Netz' if any(t['place'] == 1 and t['team'] in net_teams for t in r['teams']) else 'Regeln'}", flush=True)
    for k in places:
        if places[k]:
            print(f"{k}: mittlere Platzierung {np.mean(places[k]):.2f}, Siege {sum(1 for p in places[k] if p == 1)}, Betten/Runde {np.mean(beds[k]):.2f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("step", choices=["teacher", "clone", "evolve", "eval"])
    ap.add_argument("--console", required=True)
    ap.add_argument("--work", required=True)
    ap.add_argument("--games", type=int, default=10)
    ap.add_argument("--append", action="store_true")
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--generations", type=int, default=20)
    ap.add_argument("--pairs", type=int, default=5)
    ap.add_argument("--repeats", type=int, default=2)
    ap.add_argument("--sigma", type=float, default=0.05)
    ap.add_argument("--lr", type=float, default=0.15)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--start", default="brain_clone.json")
    ap.add_argument("--out", default="brain_evolved.json")
    ap.add_argument("--bias-only", action="store_true")
    ap.add_argument("--vs-rules", action="store_true")
    ap.add_argument("--candidate", default="brain_evolved.json")
    ap.add_argument("--baseline", default=None, help="Netz der Gegner (sonst Regelwerk)")
    args = ap.parse_args()
    os.makedirs(args.work, exist_ok=True)
    {"teacher": teacher, "clone": clone, "evolve": evolve, "eval": evaluate}[args.step](args)


if __name__ == "__main__":
    main()
