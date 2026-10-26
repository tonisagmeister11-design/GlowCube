"""Session log: what MakePrank did and when. Never records foreign content or input."""
from __future__ import annotations

import json
import logging
import threading
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable

logger = logging.getLogger("makeprank")

# Keys that must never be written to the log, whatever a caller passes in.
_SECRET_KEYS = {"token", "code", "pairing_code", "secret", "password", "key"}


class SessionLog:
    """Thread-safe in-memory event list with an optional JSON-lines file."""

    def __init__(self, path: str | Path | None = None, max_events: int = 2000):
        self.path = Path(path) if path else None
        self.max_events = max_events
        self.events: list[dict[str, Any]] = []
        self._lock = threading.Lock()
        self._listeners: list[Callable[[dict[str, Any]], None]] = []

    def add_listener(self, callback: Callable[[dict[str, Any]], None]) -> None:
        self._listeners.append(callback)

    def record(self, kind: str, message: str = "", package_hash: str = "", effect: str = "", **extra: Any) -> dict[str, Any]:
        event = {
            "time": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "kind": kind,
            "message": message,
            "package_hash": package_hash,
            "effect": effect,
        }
        for key, value in extra.items():
            if key.lower() not in _SECRET_KEYS:
                event[key] = value
        with self._lock:
            self.events.append(event)
            if len(self.events) > self.max_events:
                del self.events[: len(self.events) - self.max_events]
            if self.path is not None:
                try:
                    self.path.parent.mkdir(parents=True, exist_ok=True)
                    with open(self.path, "a", encoding="utf-8") as handle:
                        handle.write(json.dumps(event, ensure_ascii=False) + "\n")
                except OSError as exc:
                    logger.warning("Sitzungsprotokoll konnte nicht geschrieben werden: %s", exc)
        logger.info("%s %s %s", kind, effect, message)
        for callback in list(self._listeners):
            try:
                callback(event)
            except Exception:  # a broken UI listener must never break stopping a session
                logger.exception("Log-Listener fehlgeschlagen")
        return event

    def format_line(self, event: dict[str, Any]) -> str:
        parts = [event["time"][11:19], event["kind"]]
        if event.get("effect"):
            parts.append(event["effect"])
        if event.get("message"):
            parts.append(event["message"])
        if event.get("package_hash"):
            parts.append(f"[{event['package_hash'][:12]}]")
        return "  ".join(parts)
