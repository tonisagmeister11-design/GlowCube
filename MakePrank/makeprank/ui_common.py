"""Shared Tk pieces: theme, the Tk-backed session host, the consent dialog."""
from __future__ import annotations

import queue
import threading
import time
import tkinter as tk
from tkinter import ttk
from typing import Any, Callable

from .package_format import summarize_config

BG = "#0b1220"
CARD = "#111b2e"
TEXT = "#e5e7eb"
MUTED = "#9ca3af"
OK = "#86efac"
BAD = "#fca5a5"
FONT = "Segoe UI"


class TkHost:
    """SessionHost backed by Tk's event loop."""

    def __init__(self, root: tk.Misc):
        self.root = root

    def schedule(self, delay_ms: int, callback: Callable[[], None]) -> Any:
        return self.root.after(delay_ms, callback)

    def cancel(self, handle: Any) -> None:
        try:
            self.root.after_cancel(handle)
        except tk.TclError:
            pass

    def now(self) -> float:
        return time.monotonic()


class MainThreadCalls:
    """Lets network threads run a function on the Tk thread and wait for its result."""

    def __init__(self, root: tk.Misc, interval_ms: int = 40):
        self.root = root
        self.interval_ms = interval_ms
        self._queue: queue.Queue[tuple[Callable[[], Any], dict[str, Any], threading.Event]] = queue.Queue()
        self._closed = False
        self._handle: Any = None
        self._poll()

    def _poll(self) -> None:
        if self._closed:
            return
        try:
            while True:
                fn, holder, done = self._queue.get_nowait()
                try:
                    holder["value"] = fn()
                except Exception as exc:  # reported back to the calling thread, never swallowed
                    holder["error"] = exc
                done.set()
        except queue.Empty:
            pass
        try:
            self._handle = self.root.after(self.interval_ms, self._poll)
        except tk.TclError:
            self._closed = True

    def call(self, fn: Callable[[], Any], timeout: float = 5.0, default: Any = None) -> Any:
        if self._closed:
            return default
        holder: dict[str, Any] = {}
        done = threading.Event()
        self._queue.put((fn, holder, done))
        deadline = time.monotonic() + timeout
        while not done.wait(0.05):
            if self._closed or time.monotonic() >= deadline:
                return default
        if "error" in holder:
            raise holder["error"]
        return holder.get("value", default)

    def post(self, fn: Callable[[], Any]) -> None:
        """Fire-and-forget variant: never blocks the calling (network) thread."""
        if not self._closed:
            self._queue.put((fn, {}, threading.Event()))

    def close(self) -> None:
        """Stop polling and release every waiting network thread (they get their default value)."""
        self._closed = True
        while True:
            try:
                _fn, _holder, done = self._queue.get_nowait()
            except queue.Empty:
                break
            done.set()
        if self._handle is not None:
            try:
                self.root.after_cancel(self._handle)
            except tk.TclError:
                pass
            self._handle = None


def apply_theme(root: tk.Misc) -> None:
    style = ttk.Style(root)
    try:
        style.theme_use("clam")
    except tk.TclError:
        pass
    style.configure("TFrame", background=BG)
    style.configure("Card.TFrame", background=CARD)
    style.configure("TLabel", background=BG, foreground=TEXT, font=(FONT, 10))
    style.configure("Card.TLabel", background=CARD, foreground=TEXT, font=(FONT, 10))
    style.configure("Muted.TLabel", background=BG, foreground=MUTED, font=(FONT, 9))
    style.configure("Heading.TLabel", background=BG, foreground="#f9fafb", font=(FONT, 18, "bold"))
    style.configure("Sub.TLabel", background=CARD, foreground="#93c5fd", font=(FONT, 11, "bold"))
    style.configure("TButton", font=(FONT, 10), padding=(10, 6))
    style.configure("Accent.TButton", background="#2563eb", foreground="#ffffff")
    style.map("Accent.TButton", background=[("active", "#1d4ed8"), ("disabled", "#334155")])
    style.configure("Stop.TButton", background="#b91c1c", foreground="#ffffff", font=(FONT, 14, "bold"), padding=(18, 12))
    style.map("Stop.TButton", background=[("active", "#991b1b")])
    style.configure("TCheckbutton", background=BG, foreground=TEXT, font=(FONT, 10))
    style.configure("Card.TCheckbutton", background=CARD, foreground=TEXT, font=(FONT, 10))
    style.configure("TNotebook", background=BG, borderwidth=0)
    style.configure("TNotebook.Tab", background="#1e293b", foreground=TEXT, padding=(14, 8), font=(FONT, 10))
    style.map("TNotebook.Tab", background=[("selected", "#2563eb")], foreground=[("selected", "#ffffff")])
    style.configure("Treeview", background=CARD, fieldbackground=CARD, foreground=TEXT, rowheight=26, borderwidth=0)
    style.configure("Treeview.Heading", background="#1e293b", foreground="#f8fafc", font=(FONT, 10, "bold"))
    style.configure("TEntry", fieldbackground="#f9fafb", foreground="#111827", padding=4)
    style.configure("TCombobox", fieldbackground="#f9fafb", foreground="#111827", padding=3)
    root.configure(bg=BG)  # type: ignore[call-arg]


class ConsentDialog:
    """Shows exactly what will happen; nothing starts unless the (unchecked) box is ticked.

    ``result`` is ``None`` when declined/closed, otherwise ``{"network": bool}``.
    """

    def __init__(self, parent: tk.Misc, config: dict[str, Any], *, mode: str = "local", wait: bool = True):
        self.config = config
        self.mode = mode
        self.result: dict[str, Any] | None = None
        self.window = tk.Toplevel(parent)
        win = self.window
        win.title("MakePrank — Zustimmung erforderlich")
        win.configure(bg=BG)
        win.geometry("760x640")
        try:
            win.transient(parent)  # type: ignore[arg-type]
        except tk.TclError:
            pass
        ttk.Label(win, text="Vor dem Start prüfen", style="Heading.TLabel").pack(anchor="w", padx=22, pady=(20, 4))
        ttk.Label(win, text="Lies die Liste unten. Es startet nichts, solange du nicht ausdrücklich zustimmst.",
                  style="Muted.TLabel").pack(anchor="w", padx=22)
        frame = ttk.Frame(win)
        frame.pack(fill="both", expand=True, padx=22, pady=10)
        self.details = tk.Text(frame, wrap="word", bg=CARD, fg="#f9fafb", relief="flat", font=(FONT, 10), height=16)
        scroll = ttk.Scrollbar(frame, orient="vertical", command=self.details.yview)
        self.details.configure(yscrollcommand=scroll.set)
        self.details.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        self.details.insert("1.0", summarize_config(config))
        self.details.configure(state="disabled")

        self.consent_var = tk.BooleanVar(master=win, value=False)  # never pre-selected
        self.network_var = tk.BooleanVar(master=win, value=False)
        text = ("Ich habe die Liste gelesen und stimme genau diesen Effekten zu."
                + (" Audio wird abgespielt." if config["audio"] else ""))
        ttk.Checkbutton(win, text=text, variable=self.consent_var, command=self._update).pack(anchor="w", padx=22, pady=(0, 4))
        self.network_check = None
        if config["network"] == "pairing" and mode == "local":
            self.network_check = ttk.Checkbutton(
                win, variable=self.network_var, command=self._update,
                text="Zusätzlich erlaube ich, dass ein von mir bestätigter Creator diese Effekte nach Pairing auslösen darf.")
            self.network_check.pack(anchor="w", padx=22, pady=(0, 4))
        buttons = ttk.Frame(win)
        buttons.pack(fill="x", padx=22, pady=(8, 18))
        self.decline_button = ttk.Button(buttons, text="Ablehnen", command=self.decline)
        self.decline_button.pack(side="right")
        label = "Zustimmen und starten" if mode == "local" else "Zustimmen (Fernsteuerung erlauben)"
        self.start_button = ttk.Button(buttons, text=label, style="Accent.TButton", command=self.accept, state="disabled")
        self.start_button.pack(side="right", padx=8)
        win.protocol("WM_DELETE_WINDOW", self.decline)
        win.bind("<Escape>", lambda _e: self.decline())
        if wait:
            win.grab_set()
            win.wait_window()

    def _update(self) -> None:
        self.start_button.configure(state="normal" if self.consent_var.get() else "disabled")

    def accept(self) -> None:
        if not self.consent_var.get():
            return
        self.result = {"network": bool(self.network_var.get()) or self.mode == "remote"}
        self.window.destroy()

    def decline(self) -> None:
        self.result = None
        self.window.destroy()
