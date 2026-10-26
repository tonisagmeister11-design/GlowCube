"""Creator side: connects to a receiver that showed a pairing link, over pinned TLS."""
from __future__ import annotations

import hmac
import logging
import socket
import threading
import time
from dataclasses import dataclass
from typing import Any, Callable

from . import protocol as pr
from .certs import client_context, peer_fingerprint

logger = logging.getLogger("makeprank")


class ConnectionFailed(Exception):
    """Pairing or transport failure with a message that can be shown to the user."""


@dataclass
class Ack:
    ok: bool
    detail: str


class CreatorConnection:
    def __init__(self, creator_name: str, on_event: Callable[[str, str], None] | None = None,
                 heartbeat_interval: float = 5.0, timeout: float = 10.0, allow_public: bool = False):
        if not pr.NAME_RE.fullmatch(creator_name):
            raise ValueError("Ungültiger Creator-Name.")
        self.creator_name = creator_name
        self.on_event = on_event or (lambda kind, detail: None)
        self.heartbeat_interval = heartbeat_interval
        self.timeout = timeout
        self.allow_public = allow_public
        self.connected = False
        self.client_id = ""
        self.receiver_name = ""
        self.last_activity = 0.0
        self.peer = ""
        self._sock: Any = None
        self._token = ""
        self._seq = 0
        self._lock = threading.Lock()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    # ------------------------------------------------------------------ connect
    def connect(self, uri_or_target: str | pr.PairingTarget) -> None:
        try:
            target = (pr.parse_pairing_uri(uri_or_target, self.allow_public)
                      if isinstance(uri_or_target, str) else uri_or_target)
        except pr.ProtocolError as exc:
            raise ConnectionFailed(str(exc)) from exc
        if not pr.is_allowed_host(target.host, self.allow_public):
            raise ConnectionFailed("Adresse nicht erlaubt.")
        try:
            raw = socket.create_connection((target.host, target.port), timeout=self.timeout)
            sock = client_context().wrap_socket(raw, server_hostname=None)
        except OSError as exc:
            raise ConnectionFailed(f"Verbindung fehlgeschlagen: {exc}") from exc
        try:
            if not hmac.compare_digest(peer_fingerprint(sock), target.fingerprint):
                raise ConnectionFailed("Zertifikat-Fingerabdruck stimmt nicht überein – Verbindung abgebrochen.")
            sock.settimeout(self.timeout)
            self._seq = 1
            sock.sendall(pr.encode(pr.build("PAIR_REQUEST", 1, creator_name=self.creator_name,
                                            code=pr.format_code(target.code))))
            reply = pr.decode(pr.read_frame(sock), from_creator=False)
        except ConnectionFailed:
            sock.close()
            raise
        except (OSError, pr.ProtocolError) as exc:
            sock.close()
            raise ConnectionFailed(f"Pairing fehlgeschlagen: {exc}") from exc
        if reply["type"] == "PAIR_DENIED":
            sock.close()
            raise ConnectionFailed(f"Pairing abgelehnt: {reply['reason']}")
        if reply["type"] != "PAIR_OK":
            sock.close()
            raise ConnectionFailed("Unerwartete Antwort beim Pairing.")
        self._sock = sock
        self._token = reply["token"]
        self.client_id = reply["client_id"]
        self.receiver_name = reply["receiver_name"]
        self.peer = f"{target.host}:{target.port}"
        self.connected = True
        self.last_activity = time.time()
        self._stop.clear()
        self._thread = threading.Thread(target=self._heartbeat, name="makeprank-heartbeat", daemon=True)
        self._thread.start()
        self.on_event("connected", self.receiver_name)

    # ------------------------------------------------------------------ requests
    def _request(self, kind: str, **fields: Any) -> dict[str, Any]:
        with self._lock:
            if not self.connected or self._sock is None:
                raise ConnectionFailed("Nicht verbunden.")
            self._seq += 1
            try:
                self._sock.sendall(pr.encode(pr.build(kind, self._seq, token=self._token, **fields)))
                reply = pr.decode(pr.read_frame(self._sock), from_creator=False)
            except (OSError, pr.ProtocolError) as exc:
                self._drop(f"Verbindung verloren: {exc}")
                raise ConnectionFailed("Verbindung verloren.") from exc
            self.last_activity = time.time()
        if reply["type"] == "ERROR":
            self._drop(f"Empfänger meldet Fehler: {reply['detail']}")
            raise ConnectionFailed(reply["detail"])
        return reply

    def ping(self) -> None:
        self._request("PING")

    def status(self) -> dict[str, Any]:
        return self._request("STATUS")

    def start_effect(self, package_sha256: str, action_index: int, effect_id: str) -> Ack:
        reply = self._request("START_EFFECT", package_sha256=package_sha256, action_index=action_index, effect_id=effect_id)
        return Ack(reply.get("ok", False), reply.get("detail", ""))

    def stop_session(self) -> Ack:
        reply = self._request("STOP_SESSION")
        return Ack(reply.get("ok", False), reply.get("detail", ""))

    def disconnect(self) -> None:
        if self.connected:
            try:
                self._request("DISCONNECT")
            except ConnectionFailed:
                pass
        self._drop("Getrennt")

    def _heartbeat(self) -> None:
        while not self._stop.wait(self.heartbeat_interval):
            try:
                self.ping()
            except ConnectionFailed:
                return

    def _drop(self, reason: str) -> None:
        was = self.connected
        self.connected = False
        self._token = ""
        self._stop.set()
        sock, self._sock = self._sock, None
        if sock is not None:
            try:
                sock.close()
            except OSError:
                pass
        if was:
            self.on_event("disconnected", reason)
