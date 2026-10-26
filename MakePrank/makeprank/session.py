"""Session manager: runs a list of actions on *virtual* time with guaranteed cleanup.

The manager knows nothing about Tk. A *host* supplies a repeating callback and a
clock; an *effect runner* supplies the actual effects. All effect timers live in
the manager (``Scope.after``), so pausing, stopping and timeouts apply to every
effect without exception, and after ``stop()`` nothing can still be scheduled.
"""
from __future__ import annotations

import enum
import heapq
import itertools
import logging
import time
from pathlib import Path
from typing import Any, Callable, Protocol

from .audit import SessionLog
from .catalog import EFFECTS

logger = logging.getLogger("makeprank")

TICK_MS = 40
INTEGRITY_INTERVAL_SECONDS = 0.75
MAX_TICK_STEP = 0.25
MAX_OPEN_WINDOWS = 3


class StopReason(enum.Enum):
    BUTTON = "STOPP-Knopf"
    ESCAPE = "Escape-Taste"
    WINDOW_CLOSE = "Fenster geschlossen"
    TIMEOUT = "Maximale Sitzungsdauer erreicht"
    FILE_DELETED = "Paketdatei wurde gelöscht oder verschoben"
    FILE_CHANGED = "Paketdatei wurde verändert (Hash-Abweichung)"
    DISCONNECT = "Verbindung getrennt"
    NETWORK_TIMEOUT = "Netzwerk-Timeout"
    REMOTE_STOP = "Stopp vom Creator"
    ERROR = "Fehler in der Anwendung"
    FINISHED = "Alle Effekte abgeschlossen"
    REPLACED = "Durch neue Sitzung ersetzt"
    GLOBAL_STOP = "Alles stoppen"
    SHUTDOWN = "Anwendung wird beendet"


class SessionState(enum.Enum):
    IDLE = "idle"
    RUNNING = "running"
    PAUSED = "paused"
    STOPPED = "stopped"


class SessionHost(Protocol):
    def schedule(self, delay_ms: int, callback: Callable[[], None]) -> Any: ...
    def cancel(self, handle: Any) -> None: ...
    def now(self) -> float: ...


class EffectRunner(Protocol):
    def start(self, scope: "Scope", action: dict[str, Any]) -> None: ...


class Scope:
    """Owns every timer and cleanup callback created by one action (or the session)."""

    def __init__(self, session: "SessionManager", name: str):
        self.session = session
        self.name = name
        self.closed = False
        self._timer_ids: set[int] = set()
        self._cleanups: list[Callable[[], None]] = []

    def after(self, delay_seconds: float, callback: Callable[[], None]) -> int:
        if self.closed:
            raise RuntimeError("Scope ist bereits geschlossen.")
        timer_id = self.session._add_timer(delay_seconds, callback, self)
        self._timer_ids.add(timer_id)
        return timer_id

    def every(self, interval_seconds: float, callback: Callable[[], None]) -> None:
        """Call ``callback`` repeatedly until the scope closes."""
        def run() -> None:
            if self.closed:
                return
            callback()
            if not self.closed:
                self.after(interval_seconds, run)
        self.after(interval_seconds, run)

    def add_cleanup(self, callback: Callable[[], None]) -> None:
        if self.closed:
            callback()
            return
        self._cleanups.append(callback)

    @property
    def open_cleanups(self) -> int:
        return len(self._cleanups)

    def close(self) -> None:
        if self.closed:
            return
        self.closed = True
        for timer_id in self._timer_ids:
            self.session._timers.pop(timer_id, None)
        self._timer_ids.clear()
        cleanups, self._cleanups = self._cleanups[::-1], []
        for cleanup in cleanups:
            try:
                cleanup()
            except Exception:  # every cleanup must run even if one fails
                logger.exception("Aufräumen in %s fehlgeschlagen", self.name)


class SessionManager:
    def __init__(self, host: SessionHost, runner: EffectRunner, log: SessionLog | None = None):
        self.host = host
        self.runner = runner
        self.log = log or SessionLog()
        self.state = SessionState.IDLE
        self.config: dict[str, Any] | None = None
        self.source = "local"
        self.package_path: Path | None = None
        self.package_sha: str = ""
        self.last_stop_reason: StopReason | None = None
        self.last_stop_detail = ""
        self.on_change: list[Callable[[], None]] = []
        self.integrity_check: Callable[[], StopReason | None] | None = None
        self.index = -1
        self.virtual_time = 0.0
        self._timers: dict[int, tuple[float, int, Callable[[], None], Scope]] = {}
        self._heap: list[tuple[float, int, int]] = []
        self._ids = itertools.count(1)
        self._tick_handle: Any = None
        self._last_real = 0.0
        self._since_integrity = 0.0
        self._session_scope: Scope | None = None
        self._action_scope: Scope | None = None
        self._action_started_at = 0.0
        self._action_duration = 0.0
        self._nominal = 0.0

    # ------------------------------------------------------------------ queries
    @property
    def active(self) -> bool:
        return self.state in (SessionState.RUNNING, SessionState.PAUSED)

    @property
    def remaining_seconds(self) -> float:
        if not self.active or not self.config:
            return 0.0
        return max(0.0, self.config["max_duration_seconds"] - self.virtual_time)

    @property
    def remaining_in_action(self) -> float:
        if not self.active:
            return 0.0
        return max(0.0, self._action_started_at + self._action_duration - self.virtual_time)

    @property
    def current_action(self) -> dict[str, Any] | None:
        if self.active and self.config and 0 <= self.index < len(self.config["actions"]):
            return self.config["actions"][self.index]
        return None

    @property
    def active_effect_labels(self) -> list[str]:
        action = self.current_action
        return [EFFECTS[action["type"]].label] if action else []

    @property
    def pending_timers(self) -> int:
        return len(self._timers)

    # ------------------------------------------------------------------ control
    def start(self, config: dict[str, Any], *, package_path: str | Path | None = None,
              package_sha: str = "", source: str = "local") -> None:
        if self.active:
            self.stop(StopReason.REPLACED)
        self.config = config
        self.source = source
        self.package_path = Path(package_path) if package_path else None
        self.package_sha = package_sha
        self.last_stop_reason = None
        self.last_stop_detail = ""
        self.virtual_time = 0.0
        self.index = -1
        self._nominal = 0.0
        self._timers.clear()
        self._heap.clear()
        self._since_integrity = 0.0
        self._session_scope = Scope(self, "session")
        self._action_scope = None
        self.state = SessionState.RUNNING
        self.log.record("session_start", f"{config['name']} ({source})", package_sha)
        self._last_real = self.host.now()
        self._tick_handle = self.host.schedule(TICK_MS, self._on_host_tick)
        self._notify()
        self._next_action()

    def pause(self) -> None:
        if self.state is SessionState.RUNNING:
            self.state = SessionState.PAUSED
            self.log.record("session_pause", "", self.package_sha)
            self._notify()

    def resume(self) -> None:
        if self.state is SessionState.PAUSED:
            self.state = SessionState.RUNNING
            self._last_real = self.host.now()
            self.log.record("session_resume", "", self.package_sha)
            self._notify()

    def stop(self, reason: StopReason = StopReason.BUTTON, detail: str = "") -> None:
        """Stop everything. Idempotent and independent of network or creator."""
        if not self.active:
            return
        self.state = SessionState.STOPPED
        self.last_stop_reason = reason
        self.last_stop_detail = detail
        if self._tick_handle is not None:
            try:
                self.host.cancel(self._tick_handle)
            except Exception:
                logger.exception("Tick konnte nicht abgebrochen werden")
            self._tick_handle = None
        action_scope, self._action_scope = self._action_scope, None
        session_scope, self._session_scope = self._session_scope, None
        if action_scope:
            action_scope.close()
        if session_scope:
            session_scope.close()
        self._timers.clear()
        self._heap.clear()
        message = reason.value + (f" – {detail}" if detail else "")
        self.log.record("session_stop", message, self.package_sha)
        self._notify()

    # ------------------------------------------------------------------ timing
    def _add_timer(self, delay: float, callback: Callable[[], None], scope: Scope) -> int:
        timer_id = next(self._ids)
        due = self.virtual_time + max(0.0, delay)
        self._timers[timer_id] = (due, timer_id, callback, scope)
        heapq.heappush(self._heap, (due, timer_id, timer_id))
        return timer_id

    def _on_host_tick(self) -> None:
        self._tick_handle = None
        if not self.active:
            return
        now = self.host.now()
        dt = min(max(0.0, now - self._last_real), MAX_TICK_STEP)
        real_dt = max(0.0, now - self._last_real)
        self._last_real = now
        self.tick(dt, real_dt)
        if self.active:
            self._tick_handle = self.host.schedule(TICK_MS, self._on_host_tick)

    def tick(self, dt: float, real_dt: float | None = None) -> None:
        """Advance the session by ``dt`` seconds (tests call this directly)."""
        if not self.active:
            return
        self._since_integrity += dt if real_dt is None else real_dt
        if self._since_integrity >= INTEGRITY_INTERVAL_SECONDS:
            self._since_integrity = 0.0
            if not self._check_integrity():
                return
        if self.state is not SessionState.RUNNING:
            return
        self.virtual_time += dt
        try:
            while self._heap and self.active:
                due, timer_id, _ = self._heap[0]
                if due > self.virtual_time:
                    break
                heapq.heappop(self._heap)
                entry = self._timers.pop(timer_id, None)
                if entry is None:
                    continue
                entry[3]._timer_ids.discard(timer_id)
                entry[2]()
        except Exception as exc:
            logger.exception("Fehler beim Ausführen eines Effekts")
            self.stop(StopReason.ERROR, str(exc))

    def _check_integrity(self) -> bool:
        if self.integrity_check is None:
            return True
        try:
            reason = self.integrity_check()
        except Exception as exc:
            logger.exception("Integritätsprüfung fehlgeschlagen")
            self.stop(StopReason.ERROR, str(exc))
            return False
        if reason is not None:
            self.stop(reason)
            return False
        return True

    # ------------------------------------------------------------------ actions
    def _next_action(self) -> None:
        if not self.active or self._session_scope is None or self.config is None:
            return
        if self._action_scope is not None:
            self._action_scope.close()
            self._action_scope = None
        self.index += 1
        if self.index == 0:
            # Slightly after the nominal maximum so a package that uses its whole budget finishes normally.
            self._session_scope.after(self.config["max_duration_seconds"] + 0.05, lambda: self.stop(StopReason.TIMEOUT))
        if self.index >= len(self.config["actions"]):
            self.stop(StopReason.FINISHED)
            return
        action = self.config["actions"][self.index]
        scope = Scope(self, f"action-{self.index + 1}")
        self._action_scope = scope
        # Nominal (drift-free) schedule: tick granularity must not accumulate across actions.
        self._action_started_at = self._nominal
        self._action_duration = float(action["duration_seconds"])
        self._nominal += self._action_duration
        label = EFFECTS[action["type"]].label
        self.log.record("effect_start", f"{self.index + 1}/{len(self.config['actions'])}", self.package_sha, label)
        self._notify()
        try:
            self.runner.start(scope, action)
        except Exception as exc:
            logger.exception("Effekt %s konnte nicht gestartet werden", action["type"])
            self.log.record("error", f"{label}: {exc}", self.package_sha, label)
            self.stop(StopReason.ERROR, f"{label}: {exc}")
            return
        if self.active and self._action_scope is scope:
            scope.after(self._nominal - self.virtual_time, self._finish_action)

    def _finish_action(self) -> None:
        if not self.active or self.config is None:
            return
        action = self.config["actions"][self.index]
        self.log.record("effect_stop", "", self.package_sha, EFFECTS[action["type"]].label)
        self._next_action()

    def _notify(self) -> None:
        for callback in list(self.on_change):
            try:
                callback()
            except Exception:
                logger.exception("Statuslistener fehlgeschlagen")


class MonotonicHost:
    """Minimal host for headless use: schedules via an injected scheduler."""

    def __init__(self, schedule: Callable[[int, Callable[[], None]], Any], cancel: Callable[[Any], None]):
        self._schedule = schedule
        self._cancel = cancel

    def schedule(self, delay_ms: int, callback: Callable[[], None]) -> Any:
        return self._schedule(delay_ms, callback)

    def cancel(self, handle: Any) -> None:
        self._cancel(handle)

    def now(self) -> float:
        return time.monotonic()
