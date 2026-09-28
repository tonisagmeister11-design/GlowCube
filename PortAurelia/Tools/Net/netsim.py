"""UDP network simulator for multiplayer tests: sits between clients and the host and adds
delay, jitter and packet loss like a real internet connection (e.g. two flats in Vienna on
different providers: ~15-35 ms one way, a few ms jitter, occasional loss).

    python netsim.py --listen 7790 --target 127.0.0.1:7777 --delay 25 --jitter 10 --loss 2

Every client that talks to the listen port gets its own socket towards the target, so the host
sees separate peers. Stats are printed on exit (Ctrl+C / SIGTERM)."""
import argparse
import heapq
import random
import select
import signal
import socket
import sys
import time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--listen", type=int, default=7790)
    ap.add_argument("--target", default="127.0.0.1:7777")
    ap.add_argument("--delay", type=float, default=25.0, help="one-way delay in ms")
    ap.add_argument("--jitter", type=float, default=10.0, help="+- ms")
    ap.add_argument("--loss", type=float, default=2.0, help="percent of packets dropped (each way)")
    ap.add_argument("--seed", type=int, default=1)
    a = ap.parse_args()
    rnd = random.Random(a.seed)
    th, tp = a.target.split(":")
    target = (th, int(tp))
    front = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    front.bind(("127.0.0.1", a.listen))
    back = {}      # client addr -> socket to target
    rev = {}       # socket fileno -> client addr
    queue = []     # (due, seq, sock, data, addr)
    seq = 0
    stats = {"up": 0, "down": 0, "dropped": 0}
    running = [True]

    def stop(*_):
        running[0] = False
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    print(f"netsim: 127.0.0.1:{a.listen} -> {a.target}  delay {a.delay}+-{a.jitter} ms  loss {a.loss}%", flush=True)

    def schedule(sock, data, addr):
        nonlocal seq
        if rnd.random() * 100.0 < a.loss:
            stats["dropped"] += 1
            return
        d = max(0.0, a.delay + rnd.uniform(-a.jitter, a.jitter)) / 1000.0
        seq += 1
        heapq.heappush(queue, (time.monotonic() + d, seq, sock, data, addr))

    while running[0]:
        timeout = 0.005
        if queue:
            timeout = max(0.0, min(timeout, queue[0][0] - time.monotonic()))
        socks = [front] + list(back.values())
        try:
            r, _, _ = select.select(socks, [], [], timeout)
        except (InterruptedError, ValueError):
            continue
        for s in r:
            try:
                data, addr = s.recvfrom(65535)
            except OSError:
                continue
            if s is front:
                if addr not in back:
                    b = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    b.bind(("127.0.0.1", 0))
                    back[addr] = b
                    rev[b.fileno()] = addr
                stats["up"] += 1
                schedule(back[addr], data, target)
            else:
                stats["down"] += 1
                schedule(front, data, rev[s.fileno()])
        now = time.monotonic()
        while queue and queue[0][0] <= now:
            _, _, sock, data, addr = heapq.heappop(queue)
            try:
                sock.sendto(data, addr)
            except OSError:
                pass
    print(f"netsim stats: {stats}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
