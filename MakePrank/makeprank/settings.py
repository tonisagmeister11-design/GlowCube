"""Small JSON settings file in the user's profile. Never stores tokens or pairing codes."""
from __future__ import annotations

import json
import logging
import os
import platform
from dataclasses import asdict, dataclass, fields
from pathlib import Path

logger = logging.getLogger("makeprank")


def data_dir() -> Path:
    return Path(os.environ.get("MAKEPRANK_HOME") or Path.home() / ".makeprank")


@dataclass
class Settings:
    muted: bool = False
    creator_name: str = "Creator"
    receiver_name: str = ""
    allow_public_hosts: bool = False

    @property
    def effective_receiver_name(self) -> str:
        return self.receiver_name or (platform.node()[:30] or "Empfänger")

    @classmethod
    def load(cls, path: Path | None = None) -> "Settings":
        path = path or data_dir() / "settings.json"
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return cls()
        known = {f.name: f.type for f in fields(cls)}
        clean = {k: v for k, v in raw.items() if k in known and isinstance(v, (bool, str))}
        if not isinstance(clean.get("muted", False), bool) or not isinstance(clean.get("allow_public_hosts", False), bool):
            return cls()
        return cls(**clean)

    def save(self, path: Path | None = None) -> None:
        path = path or data_dir() / "settings.json"
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(asdict(self), indent=2, ensure_ascii=False), encoding="utf-8")
        except OSError as exc:
            logger.warning("Einstellungen konnten nicht gespeichert werden: %s", exc)


def configure_logging() -> None:
    """Log to stderr, or to ~/.makeprank/makeprank.log in windowed builds that have no console."""
    import sys
    kwargs: dict = {"level": logging.INFO, "format": "%(asctime)s %(levelname)s %(message)s"}
    if sys.stderr is None or getattr(sys, "frozen", False):
        try:
            data_dir().mkdir(parents=True, exist_ok=True)
            kwargs["filename"] = str(data_dir() / "makeprank.log")
            kwargs["encoding"] = "utf-8"
        except OSError:
            pass
    logging.basicConfig(**kwargs)
