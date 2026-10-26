"""Receiver side of the pairing prototype (TLS server, one peer, one pairing at a time).

Security properties (all covered by tests/test_network.py):
* disabled until the recipient clicks "Pairing starten"; listener closes after the
  first session, a timeout, or too many wrong codes,
* pairing code: random, short-lived, single use, three attempts, then the pairing is closed,
* the recipient must confirm the creator identity locally before any token is issued,
* TLS with an ephemeral certificate; the creator pins its fingerprint,
* every message is strictly validated; token, strictly increasing sequence number,
  size limit, rate limit and heartbeat timeout are enforced,
* leaving the session for ANY reason calls ``on_disconnected`` so remote effects stop.
"""
from __future__ import annotations

import hmac
import logging
import secrets
import socket
import ssl
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Any, Callable, Protocol

from . import protocol as pr
from .audit import SessionLog
from .certs import generate_self_signed, server_context

logger = logging.getLogger("makeprank")

MAX_PAIRING_ATTEMPTS = 3
CONNECTIONS_PER_MINUTE = 10


class ReceiverHandler(Protocol):
    def confirm_pairing(self, creator_name: str, peer: str) -> bool: ...
    def on_paired(self, creator_name: str, client_id: str) -> None: ...
    def on_start_effect(self, package_sha256: str, action_index: int, effect_id: str) -> tuple[bool, str]: ...
    def on_stop_session(self) -> None: ...
    def status(self) -> dict[str, Any]: ...
    def on_disconnected(self, reason: str) -> None: ...


@dataclass(frozen=True)
class PairingInfo:
    host: str
    port: int
    code: str
    fingerprint: str
    expires_in: float

    @property
    def uri(self) -> str:
        return pr.PairingTarget(self.host, self.port, self.fingerprint, self.code).uri()

    @property
    def display_code(self) -> str:
        return pr.format_code(self.code)


class RateLimiter:
    """Token bucket: ``rate`` messages per second with a burst allowance."""

    def __init__(self, rate: float, burst: int, clock: Callable[[], float]):
        self.rate, self.burst, self.clock = rate, burst, clock
        self.tokens = float(burst)
        self.last = clock()

    def allow(self) -> bool:
        now = self.clock()
        self.tokens = min(self.burst, self.tokens + (now - self.last) * self.rate)
        self.last = now
        if self.tokens >= 1.0:
            self.tokens -= 1.0
            return True
        return False


def detect_lan_address() -> str:
    """Best-effort LAN address; sends no packets (UDP connect only selects a route)."""
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("10.255.255.255", 1))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


class ReceiverServer:
    def __init__(self, handler: ReceiverHandler, *, receiver_name: str = "MakePrank-Empfänger",
                 bind_host: str = "127.0.0.1", advertise_host: str | None = None, port: int = 0,
                 pairing_ttl: float = 120.0, heartbeat_timeout: float = 15.0, handshake_timeout: float = 10.0,
                 confirm_timeout: float = 60.0, token_ttl: float = 1800.0, messages_per_second: float = 10.0,
                 effect_min_interval: float = 1.0, clock: Callable[[], float] = time.monotonic,
                 log: SessionLog | None = None):
        self.handler = handler
        self.receiver_name = receiver_name
        self.bind_host = bind_host
        self.advertise_host = advertise_host
        self.port = port
        self.pairing_ttl = pairing_ttl
        self.heartbeat_timeout = heartbeat_timeout
        self.handshake_timeout = handshake_timeout
        self.confirm_timeout = confirm_timeout
        self.token_ttl = token_ttl
        self.messages_per_second = messages_per_second
        self.effect_min_interval = effect_min_interval
        self.clock = clock
        self.log = log or SessionLog()
        self._lock = threading.Lock()
        self._listener: socket.socket | None = None
        self._thread: threading.Thread | None = None
        self._conn: socket.socket | None = None
        self._stop = threading.Event()
        self._code = ""
        self._code_used = False
        self._failed = 0
        self._deadline = 0.0
        self._attempt_times: deque[float] = deque()
        self._token = ""
        self._token_expires = 0.0
        self._local_reason = ""
        self.paired = False
        self.creator_name = ""
        self.client_id = ""
        self.fingerprint = ""
        self.done = threading.Event()

    # ------------------------------------------------------------------ lifecycle
    @property
    def listening(self) -> bool:
        return self._listener is not None

    def start_pairing(self) -> PairingInfo:
        if self.listening or self.paired:
            raise RuntimeError("Pairing läuft bereits.")
        cert, key, fingerprint = generate_self_signed()
        context = server_context(cert, key)
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind((self.bind_host, self.port))
        listener.listen(2)
        listener.settimeout(0.25)
        self._listener = listener
        self._context = context
        self.fingerprint = fingerprint
        self._code = pr.new_pairing_code()
        self._code_used = False
        self._failed = 0
        self._deadline = self.clock() + self.pairing_ttl
        self._stop.clear()
        self.done.clear()
        self._token = ""
        self._local_reason = ""
        self._end_reason = ""
        self.paired = False
        host = self.advertise_host or (detect_lan_address() if self.bind_host in ("0.0.0.0", "") else self.bind_host)
        self._thread = threading.Thread(target=self._serve, name="makeprank-receiver", daemon=True)
        self._thread.start()
        self.log.record("pairing_start", f"Pairing gestartet auf {host}:{listener.getsockname()[1]}")
        return PairingInfo(host, listener.getsockname()[1], self._code, fingerprint, self.pairing_ttl)

    def stop_pairing(self) -> None:
        """Local abort: closes listener and any connection. Works without network."""
        self._local_reason = "Vom Empfänger getrennt"
        self._stop.set()
        self._close_conn()
        self._close_listener()

    disconnect = stop_pairing

    def wait(self, timeout: float | None = None) -> bool:
        return self.done.wait(timeout)

    def _close_listener(self) -> None:
        listener, self._listener = self._listener, None
        if listener is not None:
            try:
                listener.close()
            except OSError:
                pass

    def _close_conn(self) -> None:
        with self._lock:
            conn, self._conn = self._conn, None
        if conn is not None:
            try:
                conn.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            try:
                conn.close()
            except OSError:
                pass

    # ------------------------------------------------------------------ serving
    def _serve(self) -> None:
        reason = "Pairing beendet"
        try:
            while not self._stop.is_set():
                listener = self._listener
                if listener is None:
                    break
                if self.clock() >= self._deadline:
                    reason = "Pairing-Code abgelaufen"
                    break
                try:
                    raw, addr = listener.accept()
                except socket.timeout:
                    continue
                except OSError:
                    break
                outcome = self._handle(raw, addr)
                if outcome == "closed":
                    reason = "Sitzung beendet"
                    break
                if outcome == "locked":
                    reason = "Zu viele falsche Codes"
                    break
        finally:
            self._close_conn()
            self._close_listener()
            self._token = ""
            self.paired = False
            self.log.record("pairing_end", reason)
            self.done.set()

    def _rate_limited(self) -> bool:
        now = self.clock()
        while self._attempt_times and now - self._attempt_times[0] > 60:
            self._attempt_times.popleft()
        if len(self._attempt_times) >= CONNECTIONS_PER_MINUTE:
            return True
        self._attempt_times.append(now)
        return False

    def _handle(self, raw: socket.socket, addr: tuple[str, int]) -> str:
        peer = addr[0]
        if self._rate_limited():
            raw.close()
            return "continue"
        raw.settimeout(self.handshake_timeout)
        try:
            conn = self._context.wrap_socket(raw, server_side=True)
        except (ssl.SSLError, OSError):
            raw.close()
            return "continue"
        with self._lock:
            self._conn = conn
        seq_out = 0

        def send(kind: str, **fields: Any) -> None:
            nonlocal seq_out
            seq_out += 1
            conn.sendall(pr.encode(pr.build(kind, seq_out, **fields)))

        try:
            try:
                request = pr.decode(pr.read_frame(conn), from_creator=True)
            except (pr.ProtocolError, OSError):
                return "continue"
            if request["type"] != "PAIR_REQUEST" or request["seq"] != 1:
                send("PAIR_DENIED", reason="Pairing erwartet.")
                return "continue"
            if self.clock() >= self._deadline:
                send("PAIR_DENIED", reason="Pairing-Code abgelaufen.")
                return "continue"
            if self._code_used:
                send("PAIR_DENIED", reason="Pairing-Code wurde bereits verwendet.")
                return "closed"
            supplied = pr.normalize_code(request["code"])
            if not hmac.compare_digest(supplied.encode(), self._code.encode()):
                self._failed += 1
                self.log.record("pairing_failed", f"Falscher Code (Versuch {self._failed}/{MAX_PAIRING_ATTEMPTS}) von {peer}")
                send("PAIR_DENIED", reason="Falscher Code.")
                return "locked" if self._failed >= MAX_PAIRING_ATTEMPTS else "continue"
            self._code_used = True  # single use, even if the recipient declines below
            creator = request["creator_name"]
            conn.settimeout(self.confirm_timeout)
            try:
                approved = bool(self.handler.confirm_pairing(creator, peer))
            except Exception:
                logger.exception("Pairing-Bestätigung fehlgeschlagen")
                approved = False
            if not approved:
                self.log.record("pairing_declined", f"{creator} ({peer}) abgelehnt")
                send("PAIR_DENIED", reason="Vom Empfänger abgelehnt.")
                return "closed"
            self._token = secrets.token_urlsafe(32)
            self.client_id = secrets.token_hex(8)
            self.creator_name = creator
            self.paired = True
            self._token_expires = self.clock() + self.token_ttl
            send("PAIR_OK", client_id=self.client_id, receiver_name=self.receiver_name, token=self._token)
            self.log.record("paired", f"Gekoppelt mit {creator} ({peer})", client=self.client_id)
            self.handler.on_paired(creator, self.client_id)
            self._session(conn, send)
            return "closed"
        except OSError as exc:
            self.log.record("network_error", f"{type(exc).__name__}")
            return "closed" if self.paired else "continue"
        finally:
            was_paired = self.paired
            self._close_conn()
            if was_paired:
                reason = self._local_reason or self._end_reason or "Verbindung getrennt"
                try:
                    self.handler.on_disconnected(reason)
                except Exception:
                    logger.exception("on_disconnected fehlgeschlagen")
                self.log.record("disconnected", reason, client=self.client_id)
            self.paired = False
            self._token = ""

    _end_reason = ""

    def _session(self, conn: ssl.SSLSocket, send: Callable[..., None]) -> None:
        self._end_reason = ""
        conn.settimeout(self.heartbeat_timeout)
        limiter = RateLimiter(self.messages_per_second, int(self.messages_per_second * 2), self.clock)
        last_seq = 1
        last_effect = -1e9
        while not self._stop.is_set():
            try:
                message = pr.decode(pr.read_frame(conn), from_creator=True)
            except socket.timeout:
                self._end_reason = "Netzwerk-Timeout (kein Heartbeat)"
                return
            except (ConnectionError, OSError):
                self._end_reason = "Verbindung getrennt"
                return
            except pr.ProtocolError as exc:
                self._end_reason = f"Ungültige Nachricht: {exc}"
                self._safe_send(send, "ERROR", detail="Ungültige Nachricht.")
                return
            if not limiter.allow():
                self._end_reason = "Rate-Limit überschritten"
                self._safe_send(send, "ERROR", detail="Zu viele Nachrichten.")
                return
            if self.clock() >= self._token_expires:
                self._end_reason = "Sitzungs-Token abgelaufen"
                self._safe_send(send, "ERROR", detail="Token abgelaufen.")
                return
            token = message.get("token", "")
            if message["type"] == "PAIR_REQUEST" or not hmac.compare_digest(str(token).encode(), self._token.encode()):
                self._end_reason = "Ungültige Authentifizierung"
                self._safe_send(send, "ERROR", detail="Authentifizierung fehlgeschlagen.")
                return
            if message["seq"] <= last_seq:
                self._end_reason = "Replay oder falsche Reihenfolge erkannt"
                self._safe_send(send, "ERROR", detail="Sequenzfehler.")
                return
            last_seq = message["seq"]
            kind = message["type"]
            if kind == "PING":
                send("PONG")
            elif kind == "STATUS":
                state = self.handler.status()
                send("STATUS_REPLY", state=state.get("state", "idle"), package_sha256=state.get("package_sha256", ""),
                     remaining_seconds=round(float(state.get("remaining_seconds", 0.0)), 1),
                     effects=list(state.get("effects", []))[:5])
            elif kind == "START_EFFECT":
                now = self.clock()
                if now - last_effect < self.effect_min_interval:
                    send("ACK", **{"for": "START_EFFECT"}, ok=False, detail="Zu schnell hintereinander.")
                    continue
                last_effect = now
                ok, detail = self.handler.on_start_effect(message["package_sha256"], message["action_index"], message["effect_id"])
                send("ACK", **{"for": "START_EFFECT"}, ok=bool(ok), detail=str(detail)[:200])
            elif kind == "STOP_SESSION":
                self.handler.on_stop_session()
                send("ACK", **{"for": "STOP_SESSION"}, ok=True, detail="Gestoppt.")
            elif kind == "DISCONNECT":
                self._end_reason = "Creator hat getrennt"
                self._safe_send(send, "ACK", **{"for": "DISCONNECT"}, ok=True, detail="Getrennt.")
                return
        self._end_reason = self._local_reason or "Vom Empfänger getrennt"

    @staticmethod
    def _safe_send(send: Callable[..., None], kind: str, **fields: Any) -> None:
        try:
            send(kind, **fields)
        except OSError:
            pass
