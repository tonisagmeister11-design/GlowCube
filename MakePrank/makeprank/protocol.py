"""Strictly typed, size-limited wire protocol (JSON frames inside TLS).

There is deliberately no message that carries code, a command line, a path or a
URL. ``START_EFFECT`` only names an action of a package the recipient has
already opened and accepted.
"""
from __future__ import annotations

import ipaddress
import json
import re
import secrets
import socket
import struct
from dataclasses import dataclass
from typing import Any
from urllib.parse import parse_qs, urlparse

PROTOCOL_VERSION = 1
MAX_FRAME_BYTES = 16 * 1024
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 8
HEX64 = re.compile(r"^[0-9a-f]{64}$")
CODE_RE = re.compile(r"^[A-HJ-NP-Z2-9]{4}-?[A-HJ-NP-Z2-9]{4}$")
NAME_RE = re.compile(r"^[\w .()\-]{1,40}$", re.UNICODE)


class ProtocolError(ValueError):
    """Malformed, oversized, unknown or unauthorised message."""


# type -> (required fields with validators, needs token)
def _is_str(max_len: int):
    return lambda v: isinstance(v, str) and 0 < len(v) <= max_len


def _is_index(v: Any) -> bool:
    return isinstance(v, int) and not isinstance(v, bool) and 0 <= v < 30


CLIENT_MESSAGES: dict[str, tuple[dict[str, Any], bool]] = {
    "PAIR_REQUEST": ({"creator_name": lambda v: isinstance(v, str) and bool(NAME_RE.fullmatch(v)),
                      "code": lambda v: isinstance(v, str) and bool(CODE_RE.fullmatch(v))}, False),
    "PING": ({}, True),
    "STATUS": ({}, True),
    "START_EFFECT": ({"package_sha256": lambda v: isinstance(v, str) and bool(HEX64.fullmatch(v)),
                      "action_index": _is_index,
                      "effect_id": _is_str(40)}, True),
    "STOP_SESSION": ({}, True),
    "DISCONNECT": ({}, True),
}
SERVER_MESSAGES: dict[str, tuple[dict[str, Any], bool]] = {
    "PAIR_OK": ({"client_id": _is_str(32), "receiver_name": _is_str(40), "token": _is_str(80)}, False),
    "PAIR_DENIED": ({"reason": _is_str(120)}, False),
    "PONG": ({}, False),
    "STATUS_REPLY": ({"state": _is_str(20), "package_sha256": lambda v: v == "" or bool(HEX64.fullmatch(v)),
                      "remaining_seconds": lambda v: isinstance(v, (int, float)) and not isinstance(v, bool),
                      "effects": lambda v: isinstance(v, list) and len(v) <= 5 and all(isinstance(x, str) and len(x) < 60 for x in v)},
                     False),
    "ACK": ({"for": _is_str(20), "ok": lambda v: isinstance(v, bool), "detail": lambda v: isinstance(v, str) and len(v) <= 200}, False),
    "ERROR": ({"detail": _is_str(200)}, False),
}
_ENVELOPE = {"v", "type", "seq", "token"}


def encode(message: dict[str, Any]) -> bytes:
    data = json.dumps(message, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    if len(data) > MAX_FRAME_BYTES:
        raise ProtocolError("Nachricht zu groß.")
    return struct.pack(">I", len(data)) + data


def decode(data: bytes, *, from_creator: bool) -> dict[str, Any]:
    """Parse and strictly validate one frame payload."""
    if len(data) > MAX_FRAME_BYTES:
        raise ProtocolError("Nachricht zu groß.")
    try:
        message = json.loads(data.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ProtocolError("Ungültiges JSON.") from exc
    if not isinstance(message, dict):
        raise ProtocolError("Nachricht muss ein Objekt sein.")
    if message.get("v") != PROTOCOL_VERSION:
        raise ProtocolError("Protokollversion wird nicht unterstützt.")
    kind = message.get("type")
    table = CLIENT_MESSAGES if from_creator else SERVER_MESSAGES
    if not isinstance(kind, str) or kind not in table:
        raise ProtocolError("Unbekannter Nachrichtentyp.")
    fields, needs_token = table[kind]
    seq = message.get("seq")
    if not isinstance(seq, int) or isinstance(seq, bool) or not 0 <= seq < 2**31:
        raise ProtocolError("Ungültige Sequenznummer.")
    allowed = _ENVELOPE | set(fields)
    extra = set(message) - allowed
    if extra:
        raise ProtocolError("Unerwartete Felder in der Nachricht.")
    for name, check in fields.items():
        if name not in message or not check(message[name]):
            raise ProtocolError(f"Ungültiges Feld {name!r}.")
    if from_creator:
        token = message.get("token")
        if needs_token and not (isinstance(token, str) and 0 < len(token) <= 80):
            raise ProtocolError("Token fehlt.")
        if not needs_token and "token" in message:
            raise ProtocolError("Unerwartetes Token.")
    return message


def build(kind: str, seq: int, token: str | None = None, **fields: Any) -> dict[str, Any]:
    message: dict[str, Any] = {"v": PROTOCOL_VERSION, "type": kind, "seq": seq}
    if token is not None:
        message["token"] = token
    message.update(fields)
    return message


def read_exact(sock: socket.socket, count: int) -> bytes:
    chunks = []
    remaining = count
    while remaining:
        chunk = sock.recv(min(remaining, 4096))
        if not chunk:
            raise ConnectionError("Verbindung geschlossen.")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def read_frame(sock: socket.socket) -> bytes:
    (length,) = struct.unpack(">I", read_exact(sock, 4))
    if length > MAX_FRAME_BYTES:
        raise ProtocolError("Nachricht zu groß.")  # rejected before any large allocation
    return read_exact(sock, length)


def new_pairing_code() -> str:
    return "".join(secrets.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


def format_code(code: str) -> str:
    return f"{code[:4]}-{code[4:]}"


def normalize_code(code: str) -> str:
    return code.strip().upper().replace("-", "")


# ----------------------------------------------------------------- pairing URI

@dataclass(frozen=True)
class PairingTarget:
    host: str
    port: int
    fingerprint: str
    code: str

    def uri(self) -> str:
        return f"makeprank://pair?host={self.host}&port={self.port}&fp={self.fingerprint}&code={format_code(self.code)}"


def is_allowed_host(host: str, allow_public: bool = False) -> bool:
    """Only IP literals in loopback / private / link-local ranges unless explicitly allowed."""
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return False
    if address.is_unspecified or address.is_multicast or address.is_reserved:
        return False
    if address.is_loopback or address.is_private or address.is_link_local:
        return True
    return allow_public and address.is_global


def parse_pairing_uri(text: str, allow_public: bool = False) -> PairingTarget:
    text = text.strip()
    parsed = urlparse(text)
    if parsed.scheme != "makeprank" or parsed.netloc != "pair" or len(text) > 400:
        raise ProtocolError("Kein gültiger MakePrank-Pairing-Link.")
    query = parse_qs(parsed.query, keep_blank_values=True)
    try:
        host = query["host"][0]
        port = int(query["port"][0])
        fingerprint = query["fp"][0].lower()
        code = normalize_code(query["code"][0])
    except (KeyError, ValueError, IndexError) as exc:
        raise ProtocolError("Pairing-Link unvollständig.") from exc
    if not 1 <= port <= 65535:
        raise ProtocolError("Ungültiger Port.")
    if not HEX64.fullmatch(fingerprint):
        raise ProtocolError("Ungültiger Zertifikat-Fingerabdruck.")
    if not CODE_RE.fullmatch(format_code(code)) or len(code) != CODE_LENGTH:
        raise ProtocolError("Ungültiger Pairing-Code.")
    if not is_allowed_host(host, allow_public):
        raise ProtocolError("Verbindungen sind nur zu Adressen im lokalen Netz erlaubt (127.x, 10.x, 172.16–31.x, 192.168.x).")
    return PairingTarget(host, port, fingerprint, code)
