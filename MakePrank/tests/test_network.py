"""Pairing + protocol security tests. Everything runs on loopback with real TLS."""
import json
import socket
import ssl
import struct
import threading
import time

import pytest

from makeprank import protocol as pr
from makeprank.certs import client_context, peer_fingerprint
from makeprank.creator_client import ConnectionFailed, CreatorConnection
from makeprank.receiver_server import PairingInfo, RateLimiter, ReceiverServer

SHA = "a" * 64


class Handler:
    def __init__(self, approve=True):
        self.approve = approve
        self.started = []
        self.stopped = 0
        self.disconnected = []
        self.paired = []
        self.confirmed = []
        self.event = threading.Event()

    def confirm_pairing(self, creator_name, peer):
        self.confirmed.append((creator_name, peer))
        return self.approve

    def on_paired(self, creator_name, client_id):
        self.paired.append((creator_name, client_id))

    def on_start_effect(self, sha, index, effect_id):
        self.started.append((sha, index, effect_id))
        return (sha == SHA, "ok" if sha == SHA else "fremdes Paket")

    def on_stop_session(self):
        self.stopped += 1

    def status(self):
        return {"state": "running", "package_sha256": SHA, "remaining_seconds": 3.0, "effects": ["Toast"]}

    def on_disconnected(self, reason):
        self.disconnected.append(reason)
        self.event.set()


@pytest.fixture
def servers():
    created = []

    def make(handler=None, **kw):
        handler = handler or Handler()
        server = ReceiverServer(handler, **kw)
        created.append(server)
        return server, handler, server.start_pairing()

    yield make
    for server in created:
        server.stop_pairing()
        server.wait(3)


def creator(**kw):
    return CreatorConnection("Toni", heartbeat_interval=kw.pop("heartbeat_interval", 60), timeout=3, **kw)


def target(info: PairingInfo, **override):
    data = dict(host=info.host, port=info.port, fingerprint=info.fingerprint, code=info.code)
    data.update(override)
    return pr.PairingTarget(**data)


def raw_session(info, code=None, name="Roh"):
    """Open a TLS connection and pair by hand; returns (sock, token, helper)."""
    sock = client_context().wrap_socket(socket.create_connection((info.host, info.port), timeout=3))
    sock.settimeout(3)
    sock.sendall(pr.encode(pr.build("PAIR_REQUEST", 1, creator_name=name, code=code or info.display_code)))
    reply = pr.decode(pr.read_frame(sock), from_creator=False)
    return sock, reply


def send_raw(sock, payload: bytes):
    sock.sendall(struct.pack(">I", len(payload)) + payload)


def read_until_closed(sock):
    sock.settimeout(3)
    try:
        while True:
            data = sock.recv(4096)
            if not data:
                return True
    except (ConnectionError, ssl.SSLError):
        return True
    except socket.timeout:
        return False


# ----------------------------------------------------------------- defaults & happy path

def test_nothing_listens_until_pairing_is_started():
    server = ReceiverServer(Handler())
    assert not server.listening and not server.paired


def test_full_pairing_and_whitelisted_commands(servers):
    server, handler, info = servers()
    conn = creator()
    conn.connect(info.uri)
    assert conn.connected and conn.receiver_name and conn.client_id
    assert handler.confirmed[0][0] == "Toni" and handler.paired
    conn.ping()
    status = conn.status()
    assert status["state"] == "running" and status["effects"] == ["Toast"]
    assert conn.start_effect(SHA, 0, "toast").ok
    assert handler.started == [(SHA, 0, "toast")]
    assert not conn.start_effect("b" * 64, 0, "toast").ok  # package the recipient never accepted
    assert conn.stop_session().ok and handler.stopped == 1
    conn.disconnect()
    assert handler.event.wait(3)
    assert handler.disconnected[0]
    assert not conn.connected


def test_listener_closes_after_session(servers):
    server, handler, info = servers()
    conn = creator()
    conn.connect(info.uri)
    conn.disconnect()
    assert server.wait(3)
    assert not server.listening
    with pytest.raises(OSError):
        socket.create_connection((info.host, info.port), timeout=1)


# ----------------------------------------------------------------- pairing security

def test_wrong_code_is_rejected_and_locks_after_three_attempts(servers):
    server, handler, info = servers()
    for attempt in range(3):
        with pytest.raises(ConnectionFailed, match="Falscher Code"):
            creator().connect(target(info, code="AAAAAAAA" if info.code != "AAAAAAAA" else "BBBBBBBB"))
    assert server.wait(3)  # locked: pairing closed
    with pytest.raises(ConnectionFailed):
        creator().connect(info.uri)
    assert handler.confirmed == [] and handler.paired == []


def test_expired_code_rejected():
    now = {"t": 1000.0}
    handler = Handler()
    server = ReceiverServer(handler, pairing_ttl=10, clock=lambda: now["t"])
    info = server.start_pairing()
    try:
        now["t"] += 11
        with pytest.raises(ConnectionFailed):
            creator().connect(info.uri)
        assert server.wait(3) and handler.confirmed == []
    finally:
        server.stop_pairing()


def test_code_is_single_use_even_if_declined(servers):
    server, handler, info = servers(Handler(approve=False))
    with pytest.raises(ConnectionFailed, match="abgelehnt"):
        creator().connect(info.uri)
    assert server.wait(3)
    assert handler.paired == []
    with pytest.raises(ConnectionFailed):
        creator().connect(info.uri)


def test_wrong_fingerprint_aborts_before_code_is_sent(servers):
    server, handler, info = servers()
    with pytest.raises(ConnectionFailed, match="Fingerabdruck"):
        creator().connect(target(info, fingerprint="0" * 64))
    assert handler.confirmed == []


def test_plain_tcp_without_tls_is_rejected(servers):
    server, handler, info = servers()
    sock = socket.create_connection((info.host, info.port), timeout=2)
    sock.sendall(pr.encode(pr.build("PAIR_REQUEST", 1, creator_name="X", code=info.display_code)))
    sock.settimeout(2)
    try:
        data = sock.recv(100)
    except (ConnectionError, socket.timeout):
        data = b""
    sock.close()
    assert b"PAIR_OK" not in data and handler.confirmed == []


def test_tls_configuration_pins_a_per_session_certificate(servers):
    server, handler, info = servers()
    sock = client_context().wrap_socket(socket.create_connection((info.host, info.port), timeout=3))
    assert sock.version() in ("TLSv1.2", "TLSv1.3")
    assert peer_fingerprint(sock) == info.fingerprint
    sock.close()
    server2, _, info2 = servers()
    assert info2.fingerprint != info.fingerprint
    assert info2.code != info.code


def test_pairing_codes_are_random_and_well_formed():
    codes = {pr.new_pairing_code() for _ in range(200)}
    assert len(codes) == 200
    assert all(len(c) == 8 and set(c) <= set(pr.CODE_ALPHABET) for c in codes)


def test_concurrent_connection_flood_is_rate_limited(servers):
    server, handler, info = servers()
    for _ in range(25):
        try:
            s = socket.create_connection((info.host, info.port), timeout=1)
            s.close()
        except OSError:
            break
    assert handler.confirmed == []


# ----------------------------------------------------------------- authenticated session

def test_invalid_token_disconnects(servers):
    server, handler, info = servers()
    sock, reply = raw_session(info)
    assert reply["type"] == "PAIR_OK"
    send_raw(sock, json.dumps(pr.build("PING", 2, token="falsch")).encode())
    assert pr.decode(pr.read_frame(sock), from_creator=False)["type"] == "ERROR"
    assert handler.event.wait(3) and "Authentifizierung" in handler.disconnected[0]


def test_replayed_sequence_number_is_rejected(servers):
    server, handler, info = servers()
    sock, reply = raw_session(info)
    token = reply["token"]
    send_raw(sock, json.dumps(pr.build("PING", 2, token=token)).encode())
    assert pr.decode(pr.read_frame(sock), from_creator=False)["type"] == "PONG"
    send_raw(sock, json.dumps(pr.build("PING", 2, token=token)).encode())  # replay
    assert pr.decode(pr.read_frame(sock), from_creator=False)["type"] == "ERROR"
    assert handler.event.wait(3) and "Replay" in handler.disconnected[0]


def test_manipulated_messages_end_the_session(servers):
    cases = [
        b"{kein json",
        json.dumps({"v": 1, "type": "RUN", "seq": 2, "token": "x"}).encode(),
        json.dumps({"v": 2, "type": "PING", "seq": 2, "token": "x"}).encode(),
        json.dumps({"v": 1, "type": "PING", "seq": 2, "token": "x", "cmd": "calc.exe"}).encode(),
        json.dumps({"v": 1, "type": "START_EFFECT", "seq": 2, "token": "x", "package_sha256": "zz", "action_index": 0,
                    "effect_id": "toast"}).encode(),
        json.dumps({"v": 1, "type": "START_EFFECT", "seq": 2, "token": "x", "package_sha256": SHA, "action_index": -1,
                    "effect_id": "toast"}).encode(),
        json.dumps([1, 2, 3]).encode(),
        b"\xff\xfe",
    ]
    for payload in cases:
        server, handler, info = servers()
        sock, reply = raw_session(info)
        assert reply["type"] == "PAIR_OK"
        send_raw(sock, payload)
        assert handler.event.wait(3), payload
        assert handler.started == []


def test_oversized_message_is_rejected_without_reading_it(servers):
    server, handler, info = servers()
    sock, reply = raw_session(info)
    sock.sendall(struct.pack(">I", 50 * 1024 * 1024))  # announce 50 MB
    assert handler.event.wait(3)
    assert "groß" in handler.disconnected[0]


def test_unknown_effect_ids_are_passed_to_handler_only_as_data(servers):
    class Strict(Handler):
        def on_start_effect(self, sha, index, effect_id):
            from makeprank.catalog import EFFECTS
            return (effect_id in EFFECTS, "unbekannter Effekt")

    server, handler, info = servers(Strict(), effect_min_interval=0.0)
    conn = creator()
    conn.connect(info.uri)
    assert conn.start_effect(SHA, 0, "toast").ok
    time.sleep(1.1)  # respect the per-session minimum interval between effects
    assert not conn.start_effect(SHA, 0, "powershell").ok
    conn.disconnect()


def test_start_effect_flood_is_throttled(servers):
    server, handler, info = servers(effect_min_interval=5)
    conn = creator()
    conn.connect(info.uri)
    assert conn.start_effect(SHA, 0, "toast").ok
    second = conn.start_effect(SHA, 0, "toast")
    assert not second.ok and "schnell" in second.detail
    assert len(handler.started) == 1
    conn.disconnect()


def test_message_rate_limit_disconnects(servers):
    server, handler, info = servers(messages_per_second=2)
    sock, reply = raw_session(info)
    token = reply["token"]
    for seq in range(2, 40):
        try:
            send_raw(sock, json.dumps(pr.build("PING", seq, token=token)).encode())
        except OSError:
            break
    assert handler.event.wait(3) and "Rate-Limit" in handler.disconnected[0]


def test_heartbeat_timeout_stops_remote_effects(servers):
    server, handler, info = servers(heartbeat_timeout=0.4)
    conn = creator()
    conn.connect(info.uri)
    assert handler.event.wait(3)
    assert "Timeout" in handler.disconnected[0]


def test_heartbeat_keeps_session_alive(servers):
    server, handler, info = servers(heartbeat_timeout=0.6)
    conn = creator(heartbeat_interval=0.2)
    conn.connect(info.uri)
    time.sleep(1.5)
    assert conn.connected and handler.disconnected == []
    conn.disconnect()


def test_token_expiry(servers):
    now = {"t": 0.0}
    server, handler, info = servers(token_ttl=5, clock=lambda: now["t"])
    conn = creator()
    conn.connect(info.uri)
    conn.ping()
    now["t"] += 10
    with pytest.raises(ConnectionFailed):
        conn.ping()
    assert handler.event.wait(3) and "Token" in handler.disconnected[0]


def test_creator_disconnect_triggers_cleanup(servers):
    server, handler, info = servers()
    conn = creator()
    conn.connect(info.uri)
    conn.disconnect()
    assert handler.event.wait(3)


def test_abrupt_socket_loss_triggers_cleanup(servers):
    server, handler, info = servers()
    sock, reply = raw_session(info)
    sock.close()
    assert handler.event.wait(3)


def test_receiver_can_disconnect_locally_at_any_time(servers):
    server, handler, info = servers()
    conn = creator()
    events = []
    conn.on_event = lambda kind, detail: events.append(kind)
    conn.connect(info.uri)
    server.disconnect()
    assert handler.event.wait(3)
    assert "getrennt" in handler.disconnected[0]
    with pytest.raises(ConnectionFailed):
        conn.ping()
    assert "disconnected" in events


def test_second_session_not_possible_after_first_ends(servers):
    server, handler, info = servers()
    conn = creator()
    conn.connect(info.uri)
    conn.disconnect()
    server.wait(3)
    with pytest.raises(ConnectionFailed):
        creator().connect(info.uri)


def test_token_and_code_never_appear_in_log(servers):
    server, handler, info = servers()
    conn = creator()
    conn.connect(info.uri)
    token = conn._token
    conn.disconnect()
    server.wait(3)
    blob = json.dumps(server.log.events)
    assert token not in blob and info.code not in blob and info.display_code not in blob


# ----------------------------------------------------------------- pure helpers

@pytest.mark.parametrize("host,ok", [("127.0.0.1", True), ("192.168.1.20", True), ("10.0.0.5", True), ("172.16.4.4", True),
                                      ("169.254.1.1", True), ("8.8.8.8", False), ("example.com", False),
                                      ("0.0.0.0", False), ("", False)])
def test_host_allowlist(host, ok):
    assert pr.is_allowed_host(host) is ok


def test_public_hosts_need_explicit_opt_in():
    assert pr.is_allowed_host("8.8.8.8", allow_public=True)


@pytest.mark.parametrize("uri", [
    "http://pair?host=127.0.0.1&port=1&fp=" + "a" * 64 + "&code=ABCD-EFGH",
    "makeprank://pair?host=127.0.0.1&port=99999&fp=" + "a" * 64 + "&code=ABCD-EFGH",
    "makeprank://pair?host=127.0.0.1&port=1&fp=xyz&code=ABCD-EFGH",
    "makeprank://pair?host=127.0.0.1&port=1&fp=" + "a" * 64 + "&code=0000-0000",
    "makeprank://pair?host=evil.example&port=1&fp=" + "a" * 64 + "&code=ABCD-EFGH",
    "makeprank://pair?host=127.0.0.1",
    "makeprank://other?host=127.0.0.1&port=1",
    "x" * 1000,
])
def test_bad_pairing_links_rejected(uri):
    with pytest.raises(pr.ProtocolError):
        pr.parse_pairing_uri(uri)


def test_pairing_uri_round_trip():
    t = pr.PairingTarget("192.168.0.2", 4000, "b" * 64, "ABCDEFGH")
    assert pr.parse_pairing_uri(t.uri()) == t


def test_rate_limiter_refills():
    now = {"t": 0.0}
    limiter = RateLimiter(2, 2, lambda: now["t"])
    assert limiter.allow() and limiter.allow() and not limiter.allow()
    now["t"] += 1
    assert limiter.allow()


def test_protocol_has_no_generic_command_message():
    assert set(pr.CLIENT_MESSAGES) == {"PAIR_REQUEST", "PING", "STATUS", "START_EFFECT", "STOP_SESSION", "DISCONNECT"}
    fields = set().union(*(set(f) for f, _ in pr.CLIENT_MESSAGES.values()))
    assert not fields & {"command", "cmd", "shell", "path", "url", "script", "code_exec"}
