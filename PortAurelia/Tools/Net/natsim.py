#!/usr/bin/env python3
"""Userspace "internet with home routers" simulator for the multiplayer tests.

Every game process binds its sockets to a private loopback address (HH_BIND_IP), e.g. the host
127.0.1.2 behind router A and the friend 127.0.2.2 behind router B. Packets from a game to a
"public" address (a router's public IP or a STUN server) arrive at this simulator, which applies
the sender's router (address/port mapping) and the receiver's router (filtering) like real NATs
do, then delivers the packet from the translated public address.

Router types (RFC 4787 terms):
  cone            endpoint-independent mapping + endpoint-independent filtering ("full cone")
  restricted      endpoint-independent mapping + address-dependent filtering
  portrestricted  endpoint-independent mapping + address+port-dependent filtering (most homes)
  symmetric       address+port-dependent mapping + filtering (strict / some mobile carriers)
  blocked         no UDP at all
  append "-rand" for routers that pick random public ports (carrier-grade NAT), e.g. portrestricted-rand

  natsim.py --router 127.0.1.:127.0.101.1:portrestricted --router 127.0.2.:127.0.102.1:symmetric \
            --stun 127.0.50.1:3478 --stun 127.0.50.2:3478 [--delay 20] [--jitter 5] [--loss 1]
"""
import argparse
import heapq
import random
import selectors
import socket
import struct
import sys
import time

COOKIE = 0x2112A442


class Mapping:
    def __init__(self, pub_port, priv):
        self.pub_port = pub_port
        self.priv = priv
        self.remotes = set()
        self.last = time.time()


class Router:
    def __init__(self, spec):
        prefix, pub, kind = spec.split(":")
        # "-rand": the router does not keep the port (typical for carrier-grade NAT)
        self.rand = kind.endswith("-rand")
        kind = kind.replace("-rand", "")
        self.prefix, self.pub, self.kind = prefix, pub, kind
        self.maps = {}      # key -> Mapping
        self.by_port = {}   # public port -> Mapping

    def owns_private(self, ip):
        return ip.startswith(self.prefix)

    def outbound(self, sim, src, dst):
        if self.kind == "symmetric":
            key = (src, dst)
        else:
            key = (src,)
        m = self.maps.get(key)
        if m is None:
            port = src[1] if self.kind != "symmetric" and not self.rand else 0
            if port == 0 or port in self.by_port or not sim.can_bind((self.pub, port)):
                while True:
                    port = random.randint(20000, 60000)
                    if port not in self.by_port and sim.can_bind((self.pub, port)):
                        break
            m = Mapping(port, src)
            self.maps[key] = m
            self.by_port[port] = m
            sim.bind((self.pub, port))
            sim.log(f"map {self.pub} {src} -> :{port} ({self.kind})")
        m.remotes.add(dst)
        m.last = time.time()
        return (self.pub, m.pub_port)

    def inbound(self, pub_port, remote):
        m = self.by_port.get(pub_port)
        if m is None:
            return None
        if self.kind == "cone":
            ok = True
        elif self.kind == "restricted":
            ok = any(r[0] == remote[0] for r in m.remotes)
        else:
            ok = remote in m.remotes
        return m.priv if ok else None


class Sim:
    def __init__(self, a):
        self.routers = [Router(r) for r in a.router]
        self.stun = [(s.split(":")[0], int(s.split(":")[1])) for s in a.stun]
        self.delay, self.jitter, self.loss = a.delay / 1000.0, a.jitter / 1000.0, a.loss / 100.0
        self.sel = selectors.DefaultSelector()
        self.socks = {}
        self.queue = []
        self.seq = 0
        self.verbose = a.verbose
        self.stats = {"fwd": 0, "filtered": 0, "stun": 0, "nomap": 0, "blocked": 0, "lost": 0}
        for s in self.stun:
            self.bind(s)

    def log(self, t):
        if self.verbose:
            print(t, flush=True)

    def can_bind(self, addr):
        if addr in self.socks:
            return False
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.bind(addr)
            s.close()
            return True
        except OSError:
            return False

    def bind(self, addr):
        if addr in self.socks:
            return
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1 << 20)
        s.bind(addr)
        s.setblocking(False)
        self.socks[addr] = s
        self.sel.register(s, selectors.EVENT_READ, addr)

    def router_private(self, ip):
        for r in self.routers:
            if r.owns_private(ip):
                return r
        return None

    def router_public(self, ip):
        for r in self.routers:
            if r.pub == ip:
                return r
        return None

    def send_later(self, from_addr, data, to):
        if random.random() < self.loss:
            self.stats["lost"] += 1
            return
        t = time.time() + max(0.0, self.delay + random.uniform(-self.jitter, self.jitter))
        self.seq += 1
        heapq.heappush(self.queue, (t, self.seq, from_addr, data, to))

    def handle(self, local, data, src):
        rs = self.router_private(src[0])
        if rs is None:
            return
        if rs.kind == "blocked":
            self.stats["blocked"] += 1
            return
        spub = rs.outbound(self, src, local)
        if local in self.stun:
            if len(data) >= 20 and data[0:2] == b"\x00\x01" and struct.unpack("!I", data[4:8])[0] == COOKIE:
                self.stats["stun"] += 1
                txid = data[8:20]
                ip = struct.unpack("!I", socket.inet_aton(spub[0]))[0] ^ COOKIE
                attr = struct.pack("!HHBBHI", 0x0020, 8, 0, 1, spub[1] ^ 0x2112, ip)
                resp = struct.pack("!HHI", 0x0101, len(attr), COOKIE) + txid + attr
                # back through the sender's router: it sent to the STUN server, so it passes
                priv = rs.inbound(spub[1], local)
                if priv:
                    self.send_later(local, resp, priv)
            return
        rd = self.router_public(local[0])
        if rd is None:
            return
        if rd.kind == "blocked":
            self.stats["blocked"] += 1
            return
        priv = rd.inbound(local[1], spub)
        if priv is None:
            self.stats["filtered" if local[1] in rd.by_port else "nomap"] += 1
            self.log(f"filtered {spub} -> {local}")
            return
        self.stats["fwd"] += 1
        self.bind(spub)
        self.send_later(spub, data, priv)

    def run(self):
        last_stats = time.time()
        while True:
            timeout = 0.05
            if self.queue:
                timeout = max(0.0, min(timeout, self.queue[0][0] - time.time()))
            for key, _ in self.sel.select(timeout):
                s = key.fileobj
                while True:
                    try:
                        data, src = s.recvfrom(65536)
                    except (BlockingIOError, ConnectionRefusedError):
                        break
                    except OSError:
                        break
                    self.handle(key.data, data, src)
            now = time.time()
            while self.queue and self.queue[0][0] <= now:
                _, _, frm, data, to = heapq.heappop(self.queue)
                try:
                    self.socks[frm].sendto(data, to)
                except OSError:
                    pass
            if now - last_stats > 5:
                last_stats = now
                print("natsim stats:", self.stats, flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--router", action="append", required=True)
    ap.add_argument("--stun", action="append", default=[])
    ap.add_argument("--delay", type=float, default=15)
    ap.add_argument("--jitter", type=float, default=5)
    ap.add_argument("--loss", type=float, default=0)
    ap.add_argument("--verbose", action="store_true")
    a = ap.parse_args()
    try:
        Sim(a).run()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    sys.exit(main())
