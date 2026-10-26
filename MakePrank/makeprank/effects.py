"""Tk implementations of every MakePrank effect.

Rules every effect follows (see ``CLAUDE_CODE_MASTER_SPEC.md``, sections 1 and 2.1):

* it only creates ordinary, closable windows owned by MakePrank,
* all its timers go through ``scope.after`` / ``scope.every`` (so pause/stop work),
* everything it creates is registered for cleanup, so no effect survives ``stop``,
* it never runs commands, opens URLs, writes files or touches other windows.
"""
from __future__ import annotations

import base64
import logging
import math
import random
import tkinter as tk
from typing import Any, Callable

from .audio import AudioPlayer
from .catalog import MAX_CHAT_LINES
from .session import MAX_OPEN_WINDOWS, Scope

logger = logging.getLogger("makeprank")

FONT = "Segoe UI"
MONO = "Consolas"
DARK = "#111827"
PANEL = "#1f2937"
TEXT = "#f9fafb"
MUTED = "#9ca3af"
ACCENT = "#93c5fd"

MSGBOX_BUTTONS = {
    "ok": ("OK",),
    "ok_cancel": ("OK", "Abbrechen"),
    "yes_no": ("Ja", "Nein"),
    "yes_no_cancel": ("Ja", "Nein", "Abbrechen"),
    "retry_cancel": ("Wiederholen", "Abbrechen"),
    "abort_retry_ignore": ("Abbrechen", "Wiederholen", "Ignorieren"),
}
ICON_STYLE = {
    "error": ("#dc2626", "✕"),
    "warning": ("#eab308", "!"),
    "info": ("#2563eb", "i"),
    "question": ("#2563eb", "?"),
}


def invert_color(color: str) -> str:
    r, g, b = (int(color[i:i + 2], 16) for i in (1, 3, 5))
    return f"#{255 - r:02x}{255 - g:02x}{255 - b:02x}"


def blend(c1: str, c2: str, t: float) -> str:
    a = [int(c1[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(c2[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02x}" for x, y in zip(a, b))


class TkEffectRunner:
    """Creates the visible effects. One instance per application window."""

    def __init__(self, root: tk.Misc, resources: dict[str, bytes] | None = None,
                 audio: AudioPlayer | None = None, on_stop_all: Callable[[], None] | None = None):
        self.root = root
        self.resources = resources if resources is not None else {}
        self.audio = audio or AudioPlayer()
        self.on_stop_all = on_stop_all or (lambda: None)
        self.windows: set[tk.Toplevel] = set()
        self._window_counts: dict[int, int] = {}
        self._builders: dict[str, Callable[[Scope, dict[str, Any]], None]] = {
            "error_dialog": self._error_dialog,
            "msgbox": self._msgbox,
            "toast": self._toast,
            "fake_browser": self._fake_browser,
            "fake_notepad": self._fake_notepad,
            "fake_shutdown": self._fake_shutdown,
            "fake_delete": self._fake_delete,
            "color_bars": self._color_bars,
            "screamer": self._screamer,
            "sound_effect": self._sound_effect,
            "sound_clip": self._sound_clip,
            "window_shake": self._window_shake,
            "text_overlay": self._text_overlay,
            "console_overlay": self._console_overlay,
            "visual_effect": self._visual_effect,
            "freeze_simulation": self._freeze_simulation,
            "wallpaper_preview": self._wallpaper_preview,
            "chat_message": self._chat_message,
        }

    # ------------------------------------------------------------------ plumbing
    def start(self, scope: Scope, action: dict[str, Any]) -> None:
        builder = self._builders.get(action["type"])
        if builder is None:
            raise ValueError(f"Kein Effekt für {action['type']!r}")
        builder(scope, action)

    def _window(self, scope: Scope, title: str, width: int, height: int, *, bg: str = DARK,
                x: int | None = None, y: int | None = None, footer: bool = True) -> tk.Toplevel:
        if self._window_counts.get(id(scope), 0) >= MAX_OPEN_WINDOWS:
            raise RuntimeError("Zu viele gleichzeitige Fenster.")
        self._window_counts[id(scope)] = self._window_counts.get(id(scope), 0) + 1
        win = tk.Toplevel(self.root)
        win.title(f"MakePrank — {title}")
        win.configure(bg=bg)
        win.resizable(True, True)
        geometry = f"{width}x{height}"
        if x is not None and y is not None:
            geometry += f"+{x}+{y}"
        win.geometry(geometry)
        self.windows.add(win)

        def forget(event: tk.Event) -> None:
            if event.widget is win:
                self.windows.discard(win)

        win.bind("<Destroy>", forget, add="+")
        win.protocol("WM_DELETE_WINDOW", win.destroy)
        win.bind("<Escape>", lambda _e: self.on_stop_all())

        def destroy() -> None:
            self._window_counts.pop(id(scope), None)
            try:
                if win.winfo_exists():
                    win.destroy()
            except tk.TclError:
                pass
            self.windows.discard(win)

        scope.add_cleanup(destroy)
        if footer:
            bar = tk.Frame(win, bg=bg)
            bar.pack(side="bottom", fill="x", pady=(0, 8), padx=8)
            tk.Button(bar, text="STOPP (alles beenden)", command=self.on_stop_all, bg="#b91c1c", fg="white",
                      activebackground="#991b1b", relief="flat", padx=8).pack(side="right")
            tk.Button(bar, text="Fenster schließen", command=win.destroy, relief="flat", padx=8).pack(side="right", padx=6)
        win.lift()
        win.focus_set()  # so that Escape reaches the window; no forced focus stealing
        return win

    @staticmethod
    def _alive(win: tk.Toplevel, callback: Callable[[], None]) -> Callable[[], None]:
        """Wrap a timer callback so it does nothing once the user closed the window."""
        def guarded() -> None:
            try:
                if win.winfo_exists():
                    callback()
            except tk.TclError:
                pass
        return guarded

    def _screen_pos(self, win_w: int, win_h: int, where: str) -> tuple[int, int]:
        sw, sh = self.root.winfo_screenwidth(), self.root.winfo_screenheight()
        margin = 24
        xs = {"left": margin, "right": max(0, sw - win_w - margin), "center": max(0, (sw - win_w) // 2)}
        ys = {"top": margin, "bottom": max(0, sh - win_h - 80), "center": max(0, (sh - win_h) // 2)}
        v, h = ("center", where) if where in ("left", "right") else (where, "center")
        return xs[h], ys[v]

    @staticmethod
    def _label(parent: tk.Misc, text: str, size: int = 11, bold: bool = False, fg: str = TEXT, bg: str = DARK,
               wrap: int = 0, **kw: Any) -> tk.Label:
        return tk.Label(parent, text=text, font=(FONT, size, "bold" if bold else "normal"), fg=fg, bg=bg,
                        wraplength=wrap, justify="left", **kw)

    @staticmethod
    def _icon(parent: tk.Misc, icon: str, bg: str, size: int = 44) -> tk.Canvas | None:
        if icon == "none":
            return None
        color, glyph = ICON_STYLE[icon]
        canvas = tk.Canvas(parent, width=size, height=size, bg=bg, highlightthickness=0)
        canvas.create_oval(2, 2, size - 2, size - 2, fill=color, outline=color)
        canvas.create_text(size / 2, size / 2, text=glyph, fill="white", font=(FONT, int(size * 0.5), "bold"))
        return canvas

    # ------------------------------------------------------------------ dialogs
    def _error_dialog(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 460, 230, bg="#f0f0f0", footer=False)
        body = tk.Frame(win, bg="#f0f0f0")
        body.pack(fill="both", expand=True, padx=16, pady=16)
        icon = self._icon(body, p["icon"], "#f0f0f0")
        if icon:
            icon.pack(side="left", anchor="n", padx=(0, 14))
        self._label(body, action["text"] or "(Kein Text)", 11, fg="#111827", bg="#f0f0f0", wrap=340).pack(side="left", anchor="n")
        bar = tk.Frame(win, bg="#e5e7eb")
        bar.pack(side="bottom", fill="x")
        tk.Button(bar, text="STOPP", command=self.on_stop_all, fg="#b91c1c", relief="flat").pack(side="left", padx=8, pady=8)
        tk.Button(bar, text=p["button_label"] or "OK", command=win.destroy, width=12).pack(side="right", padx=12, pady=8)

    def _msgbox(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 480, 230, bg="#f0f0f0", footer=False)
        body = tk.Frame(win, bg="#f0f0f0")
        body.pack(fill="both", expand=True, padx=16, pady=16)
        icon = self._icon(body, p["icon"], "#f0f0f0")
        if icon:
            icon.pack(side="left", anchor="n", padx=(0, 14))
        right = tk.Frame(body, bg="#f0f0f0")
        right.pack(side="left", fill="both", expand=True)
        self._label(right, action["text"] or "(Kein Text)", 11, fg="#111827", bg="#f0f0f0", wrap=360).pack(anchor="w")
        result = self._label(right, "", 9, fg="#6b7280", bg="#f0f0f0", wrap=360)
        result.pack(anchor="w", pady=(10, 0))
        bar = tk.Frame(win, bg="#e5e7eb")
        bar.pack(side="bottom", fill="x")
        tk.Button(bar, text="STOPP", command=self.on_stop_all, fg="#b91c1c", relief="flat").pack(side="left", padx=8, pady=8)
        buttons: list[tk.Button] = []

        def choose(label: str) -> None:
            result.configure(text=f"Gewählt: {label} – es wird keine Aktion außerhalb von MakePrank ausgelöst.")
            for button in buttons:
                button.configure(state="disabled")
            scope.after(1.2, self._alive(win, win.destroy))

        for label in reversed(MSGBOX_BUTTONS[p["variant"]]):
            button = tk.Button(bar, text=label, width=12, command=lambda l=label: choose(l))
            button.pack(side="right", padx=6, pady=8)
            buttons.append(button)

    def _toast(self, scope: Scope, action: dict[str, Any]) -> None:
        w, h = 340, 130
        where = action["params"]["position"]
        vertical, _, horizontal = where.partition("_")
        if where == "center":
            x, y = self._screen_pos(w, h, "center")
        else:
            x, y = self._screen_pos(w, h, horizontal)[0], self._screen_pos(w, h, vertical)[1]
        win = self._window(scope, "Benachrichtigung", w, h, bg=PANEL, x=x, y=y, footer=False)
        self._label(win, action["title"], 12, True, bg=PANEL).pack(anchor="w", padx=14, pady=(12, 2))
        self._label(win, action["text"], 10, fg="#d1d5db", bg=PANEL, wrap=310).pack(anchor="w", padx=14)
        bar = tk.Frame(win, bg=PANEL)
        bar.pack(side="bottom", fill="x", padx=8, pady=6)
        tk.Button(bar, text="Schließen", command=win.destroy, relief="flat").pack(side="right")
        tk.Button(bar, text="STOPP", command=self.on_stop_all, fg="#b91c1c", relief="flat").pack(side="right", padx=6)

    # ------------------------------------------------------------------ decoys
    def _fake_browser(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 780, 520, bg="#e5e7eb")
        top = tk.Frame(win, bg="#d1d5db")
        top.pack(fill="x")
        tab = tk.Frame(top, bg="white")
        tab.pack(side="left", padx=(8, 0), pady=(6, 0))
        self._label(tab, p["page_title"] or "Neuer Tab", 9, fg="#111827", bg="white").pack(padx=14, pady=4)
        bar = tk.Frame(win, bg="#f3f4f6")
        bar.pack(fill="x")
        for glyph in ("←", "→", "⟳"):
            tk.Label(bar, text=glyph, bg="#f3f4f6", fg="#6b7280", font=(FONT, 12)).pack(side="left", padx=6, pady=4)
        addr = tk.Label(bar, text=p["address"], anchor="w", bg="white", fg="#374151", font=(FONT, 10), relief="flat")
        addr.pack(side="left", fill="x", expand=True, padx=8, pady=5, ipady=3)
        page = tk.Frame(win, bg="white")
        page.pack(fill="both", expand=True)
        self._label(page, action["title"], 22, True, fg="#111827", bg="white", wrap=700).pack(anchor="w", padx=28, pady=(24, 8))
        self._label(page, action["text"], 12, fg="#374151", bg="white", wrap=700).pack(anchor="w", padx=28)
        self._label(page, "Attrappe – es wird keine echte Website geöffnet und kein Netzwerkzugriff ausgeführt.", 9,
                    fg="#9ca3af", bg="white").pack(side="bottom", anchor="w", padx=28, pady=10)

    def _fake_notepad(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, f"{p['filename']} – Editor", 640, 440, bg="white")
        menu = tk.Frame(win, bg="#f3f4f6")
        menu.pack(fill="x")
        for entry in ("Datei", "Bearbeiten", "Format", "Ansicht", "Hilfe"):
            tk.Label(menu, text=entry, bg="#f3f4f6", fg="#111827", font=(FONT, 10)).pack(side="left", padx=8, pady=3)
        text = tk.Text(win, wrap="word", font=(MONO, 11), relief="flat", bg="white", fg="#111827")
        text.pack(fill="both", expand=True, padx=2)
        text.insert("1.0", action["text"] or "")
        text.configure(state="disabled")
        self._label(win, f"{p['filename']} · Attrappe – es wird keine Datei geschrieben", 9, fg="#6b7280", bg="#f3f4f6"
                    ).pack(side="bottom", fill="x")

    def _fake_shutdown(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, "Simulation", 660, 420, bg="#000000")
        verb = "Windows wird heruntergefahren" if p["mode"] == "shutdown" else "Windows wird neu gestartet"
        self._label(win, action["title"] or verb, 20, True, bg="#000000").pack(pady=(50, 8))
        self._label(win, action["text"], 12, fg="#d1d5db", bg="#000000", wrap=560).pack()
        spinner = self._label(win, "", 26, fg=ACCENT, bg="#000000")
        spinner.pack(pady=18)
        countdown = self._label(win, "", 12, fg=MUTED, bg="#000000")
        countdown.pack()
        self._label(win, "SIMULATION – Windows wird NICHT heruntergefahren oder neu gestartet.", 10, True, fg="#facc15",
                    bg="#000000").pack(side="bottom", pady=6)
        state = {"left": p["countdown"], "frame": 0}
        frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"

        def spin() -> None:
            state["frame"] = (state["frame"] + 1) % len(frames)
            spinner.configure(text=frames[state["frame"]])

        def tick() -> None:
            if state["left"] > 0:
                countdown.configure(text=f"Noch {state['left']} s (Simulation)")
                state["left"] -= 1
            else:
                countdown.configure(text="Simulation beendet – du kannst dieses Fenster schließen.")

        spin()
        tick()
        scope.every(0.15, self._alive(win, spin))
        scope.every(1.0, self._alive(win, tick))

    def _fake_delete(self, scope: Scope, action: dict[str, Any]) -> None:
        # Nothing is ever deleted: this effect only draws. There is no filesystem
        # call anywhere in it (enforced by tests/test_effects.py).
        target = action["params"]["target"]
        items = (["Chrome", "Steam", "Discord", "Spotify", "Word", "Excel", "Photos", "Minecraft", "VLC", "Edge"]
                 if target == "apps" else
                 ["Dokumente", "Bilder", "Downloads", "Musik", "Videos", "Desktop", "Projekte", "Backup", "Urlaub.zip", "Zeugnis.pdf"])
        noun = "Anwendungen" if target == "apps" else "Dateien"
        win = self._window(scope, action["title"], 640, 440, bg="#0a0a0a")
        self._label(win, action["title"] or "Systembereinigung", 16, True, bg="#0a0a0a").pack(anchor="w", padx=18, pady=(14, 2))
        head = self._label(win, action["text"] or f"Entferne {noun} …", 11, fg="#d1d5db", bg="#0a0a0a")
        head.pack(anchor="w", padx=18)
        listing = tk.Text(win, height=10, bg="#050505", fg="#f3f4f6", insertbackground="#f3f4f6", font=(MONO, 10), relief="flat")
        listing.pack(fill="both", expand=True, padx=16, pady=10)
        listing.configure(state="disabled")
        bar_bg = tk.Frame(win, bg="#1f2937", height=16)
        bar_bg.pack(fill="x", padx=18, pady=(0, 4))
        bar = tk.Frame(bar_bg, bg="#dc2626", height=16, width=0)
        bar.place(x=0, y=0, relheight=1.0)
        percent = self._label(win, "0 %", 10, fg=MUTED, bg="#0a0a0a")
        percent.pack(anchor="w", padx=18, pady=(0, 6))
        state = {"i": 0}
        total = len(items)
        step_interval = max(0.25, min(float(action["duration_seconds"]) * 0.8 / total, 1.0))

        def remove_next() -> None:
            if state["i"] >= total:
                head.configure(text=f"{total} {noun} entfernt.")
                return
            name = items[state["i"]]
            listing.configure(state="normal")
            listing.insert("end", f"  Entferne  {name} …  OK\n")
            listing.see("end")
            listing.configure(state="disabled")
            state["i"] += 1
            frac = state["i"] / total
            try:
                bar.place_configure(relwidth=frac)
            except tk.TclError:
                pass
            percent.configure(text=f"{int(frac * 100)} %")

        remove_next()
        scope.every(step_interval, self._alive(win, remove_next))

    def _freeze_simulation(self, scope: Scope, action: dict[str, Any]) -> None:
        mode = action["params"]["mode"]
        win = self._window(scope, "Simulation", 660, 500, bg="#0f172a")
        canvas = tk.Canvas(win, width=640, height=360, bg="#334155", highlightthickness=0)
        canvas.pack(padx=10, pady=(10, 4))
        duration = action["duration_seconds"]
        state = {"phase": 0, "t": 0.0}

        def draw() -> None:
            canvas.delete("all")
            canvas.create_rectangle(0, 0, 640, 330, fill="#475569", outline="")
            canvas.create_rectangle(0, 330, 640, 360, fill="#0f172a", outline="")
            canvas.create_text(12, 345, anchor="w", text="Mini-Desktop (Simulation)", fill=MUTED, font=(FONT, 9))
            phase = state["phase"]
            if mode == "minimize":
                shrink = {0: 0.0, 1: 0.5, 2: 1.0, 3: 0.5}.get(phase, 0.0)
                if shrink < 1.0:
                    w, h = 360 * (1 - shrink), 200 * (1 - shrink)
                    x0, y0 = 150 + 180 * shrink, 70 + 230 * shrink
                    canvas.create_rectangle(x0, y0, x0 + w, y0 + h, fill="white", outline="#94a3b8")
                    canvas.create_rectangle(x0, y0, x0 + w, y0 + 24 * (1 - shrink), fill="#2563eb", outline="")
                canvas.create_rectangle(560, 334, 636, 358, fill="#1e293b" if phase != 2 else "#2563eb", outline="")
                canvas.create_text(598, 346, text="Programm", fill="white", font=(FONT, 8))
            else:
                canvas.create_rectangle(150, 70, 510, 270, fill="#e5e7eb", outline="#94a3b8")
                title = "Beispielprogramm" + (" (Keine Rückmeldung)" if phase == 1 else "")
                canvas.create_rectangle(150, 70, 510, 94, fill="#64748b" if phase == 1 else "#2563eb", outline="")
                canvas.create_text(160, 82, anchor="w", text=title, fill="white", font=(FONT, 10, "bold"))
                if phase == 1:
                    cx, cy = 330, 190
                    angle = state["t"] * 6.0
                    for i in range(8):
                        a = angle + i * math.pi / 4
                        canvas.create_oval(cx + 24 * math.cos(a) - 4, cy + 24 * math.sin(a) - 4,
                                           cx + 24 * math.cos(a) + 4, cy + 24 * math.sin(a) + 4,
                                           fill=blend("#94a3b8", "#1d4ed8", i / 8), outline="")

        def advance() -> None:
            state["t"] += 0.1
            draw()

        def set_phase(value: int) -> None:
            state["phase"] = value
            draw()

        self._label(win, action["text"], 11, bg="#0f172a", wrap=620).pack()
        self._label(win, "SIMULATION – es wird kein echtes Fenster minimiert oder blockiert.", 9, True, fg="#facc15",
                    bg="#0f172a").pack()
        draw()
        scope.every(0.1, self._alive(win, advance))
        if mode == "minimize":
            for fraction, phase in ((0.2, 1), (0.3, 2), (0.7, 3), (0.8, 0)):
                scope.after(duration * fraction, self._alive(win, lambda ph=phase: set_phase(ph)))
        else:
            for fraction, phase in ((0.2, 1), (0.75, 0)):
                scope.after(duration * fraction, self._alive(win, lambda ph=phase: set_phase(ph)))

    # ------------------------------------------------------------------ image & sound
    def _color_bars(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 700, 420)
        canvas = tk.Canvas(win, bg=DARK, highlightthickness=0)
        canvas.pack(fill="both", expand=True, padx=14, pady=10)
        self._label(win, action["text"], 10, fg=MUTED, wrap=660).pack()
        preset = p["preset"]
        rng = random.Random(1234)

        def stripes(colors: list[str], y0: float, y1: float, w: float, h: float) -> None:
            step = w / len(colors)
            for i, color in enumerate(colors):
                canvas.create_rectangle(i * step, y0 * h, (i + 1) * step + 1, y1 * h, fill=color, outline=color)

        def draw(_event: Any = None) -> None:
            canvas.delete("all")
            w, h = max(canvas.winfo_width(), 2), max(canvas.winfo_height(), 2)
            if preset in ("smpte", "hd"):
                top = ["#c0c0c0", "#c0c000", "#00c0c0", "#00c000", "#c000c0", "#c00000", "#0000c0"]
                if preset == "hd":
                    top = ["#999999"] + top[1:]
                stripes(top, 0, 0.66, w, h)
                stripes(["#0000c0", "#101010", "#c000c0", "#101010", "#00c0c0", "#101010", "#c0c0c0"], 0.66, 0.78, w, h)
                stripes(["#00214c", "#ffffff", "#32006a", "#101010", "#030303", "#101010", "#1d1d1d", "#101010"], 0.78, 1.0, w, h)
            elif preset == "ebu":
                stripes(["#bfbfbf", "#bfbf00", "#00bfbf", "#00bf00", "#bf00bf", "#bf0000", "#0000bf", "#000000"], 0, 1, w, h)
            elif preset == "pluge":
                stripes(["#000000", "#050505", "#0a0a0a", "#141414", "#1e1e1e", "#2a2a2a", "#3a3a3a"], 0, 1, w, h)
            elif preset == "mono":
                stripes([f"#{v:02x}{v:02x}{v:02x}" for v in range(16, 241, 32)], 0, 1, w, h)
            elif preset == "rgb":
                stripes(["#c00000", "#00c000", "#0000c0"], 0, 1, w, h)
            else:  # static: slow, low-contrast noise (at most ~2.5 changes per second)
                cols, rows = 32, 18
                cw, ch = w / cols, h / rows
                for cx in range(cols):
                    for cy in range(rows):
                        v = rng.randint(70, 180)
                        canvas.create_rectangle(cx * cw, cy * ch, (cx + 1) * cw + 1, (cy + 1) * ch + 1,
                                                fill=f"#{v:02x}{v:02x}{v:02x}", outline="")

        canvas.bind("<Configure>", draw)
        draw()
        if preset == "static":
            scope.every(0.4, self._alive(win, draw))
        if p["tone"]:
            if self.audio.play_tone("tv_tone", 900, 20):
                scope.add_cleanup(self.audio.stop)

    def _draw_builtin_image(self, canvas: tk.Canvas, name: str, w: int, h: int) -> None:
        cx, cy = w / 2, h / 2
        canvas.create_rectangle(0, 0, w, h, fill="#1a0000", outline="")
        if name == "builtin:ghost":
            canvas.create_oval(cx - 150, cy - 170, cx + 150, cy + 120, fill="#f3f4f6", outline="")
            canvas.create_rectangle(cx - 150, cy - 30, cx + 150, cy + 150, fill="#f3f4f6", outline="")
            for i in range(5):
                canvas.create_oval(cx - 150 + i * 60, cy + 110, cx - 90 + i * 60, cy + 190, fill="#1a0000", outline="")
            for ex in (-60, 60):
                canvas.create_oval(cx + ex - 28, cy - 80, cx + ex + 28, cy - 10, fill="#111827", outline="")
            canvas.create_oval(cx - 30, cy + 20, cx + 30, cy + 90, fill="#111827", outline="")
        elif name == "builtin:eyes":
            for ex in (-110, 110):
                canvas.create_oval(cx + ex - 80, cy - 60, cx + ex + 80, cy + 60, fill="#f9fafb", outline="")
                canvas.create_oval(cx + ex - 30, cy - 30, cx + ex + 30, cy + 30, fill="#b91c1c", outline="")
                canvas.create_oval(cx + ex - 12, cy - 12, cx + ex + 12, cy + 12, fill="black", outline="")
        else:  # builtin:clown
            canvas.create_oval(cx - 130, cy - 150, cx + 130, cy + 130, fill="#fde68a", outline="")
            canvas.create_oval(cx - 30, cy - 30, cx + 30, cy + 30, fill="#dc2626", outline="")
            for ex in (-55, 55):
                canvas.create_oval(cx + ex - 22, cy - 80, cx + ex + 22, cy - 40, fill="white", outline="black")
                canvas.create_oval(cx + ex - 8, cy - 70, cx + ex + 8, cy - 50, fill="black", outline="")
            canvas.create_arc(cx - 80, cy + 10, cx + 80, cy + 100, start=200, extent=140, style="arc", outline="#dc2626", width=8)

    def _screamer(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 780, 600)
        image_ref = p["image"]
        shown = False
        if image_ref.startswith("resource:"):
            data = self.resources.get(image_ref.split(":", 1)[1])
            if data:
                try:
                    photo = tk.PhotoImage(master=win, data=base64.b64encode(data))
                    factor = max(1, math.ceil(photo.width() / 740), math.ceil(photo.height() / 440))
                    if factor > 1:
                        photo = photo.subsample(factor, factor)
                    label = tk.Label(win, image=photo, bg="#1a0000")
                    label.image = photo  # keep a reference
                    label.pack(fill="both", expand=True, padx=12, pady=(12, 4))
                    shown = True
                except tk.TclError as exc:
                    logger.warning("Bild konnte nicht geladen werden: %s", exc)
        if not shown:
            canvas = tk.Canvas(win, width=740, height=440, bg="#1a0000", highlightthickness=0)
            canvas.pack(padx=12, pady=(12, 4))
            builtin = image_ref if image_ref in ("builtin:ghost", "builtin:eyes", "builtin:clown") else "builtin:ghost"
            self._draw_builtin_image(canvas, builtin, 740, 440)
        self._label(win, (action["text"] or action["title"])[:200], 28, True, fg="#ff4d4d", bg=DARK, wrap=720).pack(pady=4)
        if p["sound"] != "none" and self.audio.play_tone(p["sound"], 700, 25):
            scope.add_cleanup(self.audio.stop)

    def _sound_effect(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 460, 230)
        self._label(win, action["text"] or "Ein kurzer Ton wird abgespielt.", 12, wrap=420).pack(padx=18, pady=(20, 8))
        played = self.audio.play_tone(p["tone"], p["milliseconds"], p["volume"])
        info = (f"Audio: {p['tone']} · {p['milliseconds']} ms · {p['volume']} %" if played
                else "Audio ist stummgeschaltet – es wird nichts abgespielt.")
        self._label(win, info, 10, fg=ACCENT).pack()
        if played:
            scope.add_cleanup(self.audio.stop)
            tk.Button(win, text="Ton stoppen", command=self.audio.stop, relief="flat").pack(pady=6)

    def _sound_clip(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        ref = p["clip"]
        data = self.resources.get(ref.split(":", 1)[1]) if ref.startswith("resource:") else None
        played = self.audio.play_clip(data) if data else False
        if played:
            scope.add_cleanup(self.audio.stop)
        if not p.get("show_window", True):
            return
        win = self._window(scope, action["title"], 440, 220)
        self._label(win, action["text"] or "🔊 Ein Sound wird abgespielt …", 14, True, wrap=400).pack(padx=18, pady=(22, 8))
        if data is None:
            info = "Kein Sound-Clip im Paket hinterlegt."
        elif played:
            info = "Audio: eigener Sound-Clip wird abgespielt."
        else:
            info = "Audio ist stummgeschaltet – es wird nichts abgespielt."
        self._label(win, info, 10, fg=ACCENT).pack()
        if played:
            tk.Button(win, text="Ton stoppen", command=self.audio.stop, relief="flat").pack(pady=6)

    # ------------------------------------------------------------------ motion & text
    def _window_shake(self, scope: Scope, action: dict[str, Any]) -> None:
        amplitude = min(12, max(1, round(action["params"]["intensity"] * 1.2)))
        origin_x, origin_y = 120, 120
        win = self._window(scope, action["title"], 520, 260, x=origin_x, y=origin_y)
        self._label(win, action["text"] or "Das Fenster wackelt …", 16, True, wrap=460).pack(expand=True)
        win.update_idletasks()
        # Use the geometry we asked for as the origin. Reading winfo_x/y here is
        # unreliable before the window is mapped (0/1 on Windows), which would make
        # the shake offsets negative and the geometry string invalid.
        origin = (origin_x, origin_y)
        offsets = [(1, 0), (-1, 1), (0, -1), (1, 1), (-1, -1), (0, 0)]
        state = {"i": 0}
        shake_until = min(float(action["duration_seconds"]), 3.0)
        elapsed = {"t": 0.0}

        def place(dx: int, dy: int) -> None:
            try:
                if win.winfo_exists():
                    win.geometry(f"+{origin[0] + dx}+{origin[1] + dy}")
            except tk.TclError:
                pass

        def step() -> None:
            elapsed["t"] += 0.05
            if elapsed["t"] >= shake_until:
                place(0, 0)
                return
            dx, dy = offsets[state["i"] % len(offsets)]
            state["i"] += 1
            place(dx * amplitude, dy * amplitude)

        scope.every(0.05, self._alive(win, step))
        scope.add_cleanup(lambda: place(0, 0) if win.winfo_exists() else None)

    def _text_overlay(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        w, h = 900, 260
        x, y = self._screen_pos(w, h, p["position"])
        win = self._window(scope, action["title"], w, h, bg=p["background"], x=x, y=y)
        tk.Label(win, text=action["text"] or action["title"], font=(FONT, p["font_size"], "bold"), fg=p["color"],
                 bg=p["background"], wraplength=w - 60, justify="center").pack(expand=True, fill="both", padx=20)

    def _console_overlay(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 700, 380, bg="#050805")
        body = tk.Text(win, wrap="word", bg="#050805", fg="#78f078", insertbackground="#78f078", font=(MONO, 11), relief="flat")
        body.pack(fill="both", expand=True, padx=14, pady=(12, 4))
        lines = action["text"].splitlines() or ["MakePrank-Konsole (Attrappe)", "Es werden keine Befehle ausgeführt."]
        state = {"i": 0}
        body.insert("end", f"{p['prompt']} ")
        body.configure(state="disabled")

        def put() -> None:
            if state["i"] >= len(lines):
                return
            body.configure(state="normal")
            body.insert("end", lines[state["i"]] + "\n" + p["prompt"] + " ")
            body.see("end")
            body.configure(state="disabled")
            state["i"] += 1

        scope.every(p["line_delay_ms"] / 1000.0, self._alive(win, put))

    def _visual_effect(self, scope: Scope, action: dict[str, Any]) -> None:
        mode = action["params"]["mode"]
        win = self._window(scope, action["title"], 640, 440)
        canvas = tk.Canvas(win, width=600, height=340, bg="#102a43", highlightthickness=0)
        canvas.pack(padx=14, pady=10)
        self._label(win, "Nur die Zeichenfläche in diesem Fenster wird verändert (Wechsel alle 1,5 s).", 9, fg=MUTED).pack()
        state = {"on": False}
        W, H = 600, 340

        def draw() -> None:
            canvas.delete("all")
            on = state["on"]

            def col(c: str) -> str:
                return invert_color(c) if on and mode == "invert" else c

            def fx(x: float) -> float:
                return W - x if on and mode == "flip_h" else x

            def fy(y: float) -> float:
                return H - y if on and mode == "flip_v" else y

            def rect(x0: float, y0: float, x1: float, y1: float, color: str) -> None:
                canvas.create_rectangle(fx(x0), fy(y0), fx(x1), fy(y1), fill=col(color), outline="")

            rect(0, 0, W, H, "#102a43")
            for i, c in enumerate(["#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#a855f7"]):
                rect(20 + i * 56, 30, 70 + i * 56, 80, c)
            canvas.create_oval(fx(380), fy(130), fx(560), fy(310), fill=col("#facc15"), outline="")
            canvas.create_polygon(fx(60), fy(300), fx(240), fy(300), fx(60), fy(140), fill=col("#34d399"), outline="")
            canvas.create_text(fx(300) if mode != "flip_h" or not on else fx(300), fy(110), text="MakePrank", fill=col("#f9fafb"),
                               font=(FONT, 26, "bold"))

        def toggle() -> None:
            state["on"] = not state["on"]
            draw()

        draw()
        scope.every(1.5, self._alive(win, toggle))

    def _wallpaper_preview(self, scope: Scope, action: dict[str, Any]) -> None:
        p = action["params"]
        win = self._window(scope, action["title"], 700, 470, bg="#0f172a")
        canvas = tk.Canvas(win, width=660, height=380, bg="#0f172a", highlightthickness=0)
        canvas.pack(padx=14, pady=(12, 4))
        strips = 76
        for i in range(strips):
            canvas.create_rectangle(0, i * 5, 660, i * 5 + 6, fill=blend(p["color_top"], p["color_bottom"], i / (strips - 1)), outline="")
        for i in range(4):
            canvas.create_rectangle(18, 18 + i * 62, 54, 54 + i * 62, fill="#e5e7eb", outline="")
        canvas.create_rectangle(0, 356, 660, 380, fill="#0f172a", outline="")
        canvas.create_text(330, 150, text=action["text"] or action["title"], fill="white", font=(FONT, 20, "bold"))
        self._label(win, "Vorschau – dein echter Windows-Hintergrund bleibt unverändert.", 9, True, fg="#facc15", bg="#0f172a").pack()

    def _chat_message(self, scope: Scope, action: dict[str, Any]) -> None:
        sender = action["params"]["sender"] or "Unbekannt"
        win = self._window(scope, f"Chat – {sender}", 440, 480, bg="#0b1220")
        self._label(win, f"💬 {sender}", 13, True, bg=PANEL).pack(fill="x", ipady=8)
        box = tk.Frame(win, bg="#0b1220")
        box.pack(fill="both", expand=True, padx=12, pady=10)
        lines = [line for line in action["text"].splitlines() if line.strip()][:MAX_CHAT_LINES] or ["…"]
        state = {"i": 0}

        def post() -> None:
            if state["i"] >= len(lines):
                return
            bubble = tk.Label(box, text=lines[state["i"]], bg="#2563eb", fg="white", font=(FONT, 11), wraplength=300,
                              justify="left", padx=10, pady=6)
            bubble.pack(anchor="w", pady=4)
            state["i"] += 1

        post()
        scope.every(1.2, self._alive(win, post))
        self._label(win, "Nur eine Anzeige in MakePrank – nichts wird in andere Programme geschrieben.", 8, fg=MUTED, bg="#0b1220",
                    wrap=400).pack(side="bottom")
