"""MakePrank Creator / Control Center (Windows/Tk)."""
from __future__ import annotations

import argparse
import logging
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any

from . import __version__
from .audio import AudioBackend, AudioPlayer
from .audit import SessionLog
from .catalog import BUILTIN_IMAGES, CATEGORIES, EFFECTS, ParamSpec, action_has_audio, default_action
from .creator_client import ConnectionFailed, CreatorConnection
from .effects import TkEffectRunner
from .package_format import (
    NETWORK_MODES, PackageError, clean_action, file_sha256, inspect_image, load_package_full, new_config,
    save_package, summarize_config, validate_config,
)
from .session import SessionManager, StopReason
from .settings import Settings, configure_logging, data_dir
from .ui_common import BG, CARD, MUTED, OK, MainThreadCalls, TkHost, apply_theme

APP_NAME = "MakePrank Creator"
logger = logging.getLogger("makeprank")

HELP_TEXT = """MakePrank – Kurzhilfe

1. Effektkatalog: Effekt auswählen und „Zum Paket hinzufügen“ klicken.
2. Paket-Editor: Reihenfolge, Texte, Parameter und Dauer einstellen, dann „Aktion übernehmen“.
3. Vorschau: spielt das Paket mit derselben Engine wie beim Empfänger – ausschließlich in MakePrank-Fenstern.
4. Export: zeigt die Zusammenfassung, die auch der Empfänger sieht, und schreibt die .mpk-Datei (nur Daten + optionale Bilder).
5. Clients/Pairing: Der Empfänger startet bewusst „Pairing starten“ und gibt dir den Link. Er muss deine Verbindung bestätigen.
   Danach kannst du einzelne Effekte seines Pakets auslösen oder die Sitzung stoppen. Er kann jederzeit selbst stoppen/trennen.

Was MakePrank NIE tut: Systembefehle, echtes Herunterfahren/Neustarten, Dateien ändern/löschen, fremde Fenster steuern,
Tastatur/Maus übernehmen, versteckt oder dauerhaft im Hintergrund laufen, Autostart.
Pakete beweisen nicht, wer sie erstellt hat: Der Hash erkennt Veränderungen, ist aber keine Signatur.
"""


class CreatorApp:
    def __init__(self, root: tk.Tk, *, settings: Settings | None = None, log: SessionLog | None = None,
                 audio_backend: AudioBackend | None = None):
        self.root = root
        self.settings = settings or Settings.load()
        self.log = log or SessionLog(data_dir() / "creator-sessions.jsonl")
        self.audio = AudioPlayer(audio_backend, muted=True)  # preview is silent unless explicitly enabled
        self.config = new_config()
        self.resources: dict[str, bytes] = {}
        self.selected: int | None = None
        self.exported_path: Path | None = None
        self.exported_file_sha = ""
        self.exported_content_sha = ""
        self.connections: dict[str, CreatorConnection] = {}
        self.connected_at: dict[str, float] = {}
        self.blocked: set[str] = set()
        self._tick_handle: Any = None
        self.main = MainThreadCalls(root)
        self.runner = TkEffectRunner(root, self.resources, self.audio, on_stop_all=self.stop_everything)
        self.session = SessionManager(TkHost(root), self.runner, self.log)
        self.session.on_change.append(self._refresh_status)
        self.log.add_listener(lambda event: self.main.post(lambda e=event: self._append_log(e)))

        root.title(f"{APP_NAME} · v{__version__}")
        root.geometry("1180x800")
        root.minsize(1000, 700)
        apply_theme(root)
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.bind_all("<Escape>", lambda _e: self.stop_everything())
        self._build()
        self._load_into_editor()
        self._refresh_status()
        self._tick()

    # ------------------------------------------------------------------ layout
    def _build(self) -> None:
        header = ttk.Frame(self.root, padding=(20, 12, 20, 4))
        header.pack(fill="x")
        ttk.Label(header, text="MakePrank", style="Heading.TLabel").pack(side="left")
        ttk.Label(header, text="Creator · sichtbare, zustimmungsbasierte Simulationen", style="Muted.TLabel").pack(side="left", padx=14, pady=(8, 0))
        self.status_var = tk.StringVar(value="Bereit")
        ttk.Label(header, textvariable=self.status_var, foreground=OK).pack(side="right", padx=12)
        ttk.Button(header, text="ALLES STOPPEN", style="Stop.TButton", command=self.stop_everything).pack(side="right")

        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(fill="both", expand=True, padx=16, pady=10)
        self.pages: dict[str, ttk.Frame] = {}
        for key, title, builder in (
            ("dashboard", "Dashboard", self._page_dashboard), ("catalog", "Effektkatalog", self._page_catalog),
            ("editor", "Paket-Editor", self._page_editor), ("preview", "Vorschau", self._page_preview),
            ("export", "Export", self._page_export), ("clients", "Clients/Pairing", self._page_clients),
            ("log", "Sitzungsprotokoll", self._page_log), ("settings", "Einstellungen", self._page_settings),
            ("help", "Hilfe", self._page_help),
        ):
            frame = ttk.Frame(self.notebook, padding=14)
            self.notebook.add(frame, text=title)
            self.pages[key] = frame
            builder(frame)
        self.notebook.bind("<<NotebookTabChanged>>", lambda _e: self._on_tab())

    def _on_tab(self) -> None:
        if self.notebook.index("current") == 4:
            self._refresh_export()
        if self.notebook.index("current") == 0:
            self._refresh_dashboard()

    # ---- dashboard
    def _page_dashboard(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Dashboard", style="Heading.TLabel").pack(anchor="w")
        self.dash_text = tk.Text(f, height=10, bg=CARD, fg="#f9fafb", relief="flat", font=("Segoe UI", 11))
        self.dash_text.pack(fill="x", pady=10)
        row = ttk.Frame(f)
        row.pack(fill="x")
        ttk.Button(row, text="Neues Paket", command=self.new_project).pack(side="left")
        ttk.Button(row, text="Paket öffnen …", command=self.open_project).pack(side="left", padx=6)
        ttk.Button(row, text="Exportieren …", style="Accent.TButton", command=self.export).pack(side="left")
        ttk.Button(row, text="Vorschau", command=self.start_preview).pack(side="left", padx=6)
        ttk.Label(f, text="Letzte Ereignisse", style="Muted.TLabel").pack(anchor="w", pady=(14, 2))
        self.dash_log = tk.Text(f, height=10, bg=CARD, fg=MUTED, relief="flat", font=("Consolas", 9))
        self.dash_log.pack(fill="both", expand=True)

    def _refresh_dashboard(self) -> None:
        try:
            cfg = self._validated_config()
            lines = [f"Paket: {cfg['name']}", f"Effekte: {len(cfg['actions'])}",
                     f"Geschätzte Dauer: {cfg['estimated_duration_seconds']} s", f"Audio: {'ja' if cfg['audio'] else 'nein'}",
                     f"Netzwerk: {'Pairing erlaubt' if cfg['network'] == 'pairing' else 'aus'}",
                     f"Bilder: {len(cfg['resources'])}"]
        except PackageError as exc:
            lines = [f"Paket hat Fehler: {exc}"]
        lines.append(f"Verbundene Empfänger: {sum(1 for c in self.connections.values() if c.connected)}")
        lines.append(f"Vorschau: {'läuft' if self.session.active else 'inaktiv'}")
        self.dash_text.configure(state="normal")
        self.dash_text.delete("1.0", "end")
        self.dash_text.insert("1.0", "\n".join(lines))
        self.dash_text.configure(state="disabled")

    # ---- catalog
    def _page_catalog(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Effektkatalog", style="Heading.TLabel").pack(anchor="w")
        bar = ttk.Frame(f)
        bar.pack(fill="x", pady=8)
        self.search_var = tk.StringVar(master=self.root)
        self.category_var = tk.StringVar(master=self.root, value="Alle")
        ttk.Label(bar, text="Suche:").pack(side="left")
        entry = ttk.Entry(bar, textvariable=self.search_var, width=28)
        entry.pack(side="left", padx=6)
        ttk.Combobox(bar, textvariable=self.category_var, values=("Alle",) + CATEGORIES, state="readonly", width=22).pack(side="left")
        self.search_var.trace_add("write", lambda *_: self._fill_catalog())
        self.category_var.trace_add("write", lambda *_: self._fill_catalog())
        body = ttk.Frame(f)
        body.pack(fill="both", expand=True)
        self.catalog_tree = ttk.Treeview(body, columns=("category", "risk", "audio"), show="tree headings", height=14)
        self.catalog_tree.heading("#0", text="Effekt")
        self.catalog_tree.heading("category", text="Kategorie")
        self.catalog_tree.heading("risk", text="Risiko")
        self.catalog_tree.heading("audio", text="Audio")
        self.catalog_tree.column("#0", width=280)
        self.catalog_tree.column("category", width=150)
        self.catalog_tree.column("risk", width=70)
        self.catalog_tree.column("audio", width=60)
        self.catalog_tree.pack(side="left", fill="both", expand=True)
        self.catalog_tree.bind("<<TreeviewSelect>>", lambda _e: self._show_effect_details())
        side = ttk.Frame(body, style="Card.TFrame", padding=12)
        side.pack(side="left", fill="both", padx=(10, 0))
        self.effect_details = tk.Text(side, width=48, height=16, wrap="word", bg=CARD, fg="#f9fafb", relief="flat", font=("Segoe UI", 10))
        self.effect_details.pack(fill="both", expand=True)
        self.effect_details.configure(state="disabled")
        btns = ttk.Frame(side, style="Card.TFrame")
        btns.pack(fill="x", pady=(8, 0))
        ttk.Button(btns, text="Zum Paket hinzufügen", style="Accent.TButton", command=self.add_selected_effect).pack(side="left")
        ttk.Button(btns, text="Beispielvorschau", command=self.preview_selected_effect).pack(side="left", padx=6)
        self._fill_catalog()

    def _fill_catalog(self) -> None:
        query = self.search_var.get().strip().lower()
        category = self.category_var.get()
        self.catalog_tree.delete(*self.catalog_tree.get_children())
        for kind, spec in EFFECTS.items():
            if category not in ("Alle", spec.category):
                continue
            hay = f"{spec.label} {spec.description} {spec.category}".lower()
            if query and query not in hay:
                continue
            audio = "ja" if (spec.always_audio or any(p.name in ("tone", "sound") for p in spec.params)) else "nein"
            self.catalog_tree.insert("", "end", iid=kind, text=spec.label, values=(spec.category, spec.risk, audio))

    def _selected_catalog_kind(self) -> str | None:
        selection = self.catalog_tree.selection()
        return selection[0] if selection else None

    def _show_effect_details(self) -> None:
        kind = self._selected_catalog_kind()
        if not kind:
            return
        spec = EFFECTS[kind]
        lines = [spec.label, "", spec.description, "", f"Risiko: {spec.risk} – {spec.risk_note}", "", "Parameter:"]
        for p in spec.params:
            extra = (f" ({p.minimum}–{p.maximum})" if p.kind == "int" else f" [{', '.join(p.choices)}]" if p.kind == "choice" else "")
            lines.append(f"  • {p.label}{extra}")
        if not spec.params:
            lines.append("  (keine)")
        self.effect_details.configure(state="normal")
        self.effect_details.delete("1.0", "end")
        self.effect_details.insert("1.0", "\n".join(lines))
        self.effect_details.configure(state="disabled")

    def add_selected_effect(self, kind: str | None = None) -> None:
        kind = kind or self._selected_catalog_kind()
        if not kind:
            messagebox.showinfo("Kein Effekt gewählt", "Bitte wähle zuerst einen Effekt aus.", parent=self.root)
            return
        if len(self.config["actions"]) >= 30:
            messagebox.showwarning("Limit erreicht", "Ein Paket darf höchstens 30 Aktionen enthalten.", parent=self.root)
            return
        self.config["actions"].append(default_action(kind))
        self.selected = len(self.config["actions"]) - 1
        self._fill_actions()
        self._select_action(self.selected)
        self.notebook.select(2)

    def preview_selected_effect(self) -> None:
        kind = self._selected_catalog_kind()
        if not kind:
            return
        cfg = new_config("Beispielvorschau")
        cfg["actions"] = [default_action(kind)]
        self._start_preview_config(validate_config(cfg))

    # ---- editor
    def _page_editor(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Paket-Editor", style="Heading.TLabel").pack(anchor="w")
        top = ttk.Frame(f)
        top.pack(fill="x", pady=6)
        self.name_var = tk.StringVar(master=self.root)
        self.desc_var = tk.StringVar(master=self.root)
        self.creator_var = tk.StringVar(master=self.root)
        self.max_var = tk.StringVar(master=self.root)
        self.network_var = tk.BooleanVar(master=self.root)
        for col, (label, var, width) in enumerate((("Name", self.name_var, 22), ("Beschreibung", self.desc_var, 36), ("Absender", self.creator_var, 16))):
            ttk.Label(top, text=label + ":").grid(row=0, column=col * 2, sticky="w", padx=(0, 4))
            ttk.Entry(top, textvariable=var, width=width).grid(row=0, column=col * 2 + 1, padx=(0, 12))
        ttk.Label(top, text="Max. Sitzung (s):").grid(row=1, column=0, sticky="w", pady=6)
        ttk.Spinbox(top, from_=1, to=1800, textvariable=self.max_var, width=8).grid(row=1, column=1, sticky="w")
        ttk.Checkbutton(top, text="Fernsteuerung per Pairing erlauben (Empfänger muss zusätzlich zustimmen)",
                        variable=self.network_var).grid(row=1, column=2, columnspan=4, sticky="w")
        body = ttk.Frame(f)
        body.pack(fill="both", expand=True)
        left = ttk.Frame(body)
        left.pack(side="left", fill="both")
        self.action_tree = ttk.Treeview(left, columns=("type", "dur"), show="tree headings", height=14)
        self.action_tree.heading("#0", text="#  Titel")
        self.action_tree.heading("type", text="Effekt")
        self.action_tree.heading("dur", text="s")
        self.action_tree.column("#0", width=190)
        self.action_tree.column("type", width=170)
        self.action_tree.column("dur", width=40)
        self.action_tree.pack(fill="both", expand=True)
        self.action_tree.bind("<<TreeviewSelect>>", lambda _e: self._on_action_select())
        btns = ttk.Frame(left)
        btns.pack(fill="x", pady=6)
        ttk.Button(btns, text="↑", width=3, command=lambda: self.move_action(-1)).pack(side="left")
        ttk.Button(btns, text="↓", width=3, command=lambda: self.move_action(1)).pack(side="left", padx=4)
        ttk.Button(btns, text="Entfernen", command=self.remove_action).pack(side="left")
        ttk.Button(btns, text="+ Fehlerdialog", command=lambda: self.add_selected_effect("error_dialog")).pack(side="left", padx=6)
        form = ttk.Frame(body, style="Card.TFrame", padding=12)
        form.pack(side="left", fill="both", expand=True, padx=(12, 0))
        self.type_var = tk.StringVar(master=self.root)
        self.title_var = tk.StringVar(master=self.root)
        self.dur_var = tk.StringVar(master=self.root)
        ttk.Label(form, text="Effekt", style="Card.TLabel").grid(row=0, column=0, sticky="w")
        self.type_combo = ttk.Combobox(form, textvariable=self.type_var, values=[s.label for s in EFFECTS.values()], state="readonly", width=34)
        self.type_combo.grid(row=0, column=1, sticky="w", pady=3)
        self.type_combo.bind("<<ComboboxSelected>>", lambda _e: self._on_type_change())
        ttk.Label(form, text="Titel", style="Card.TLabel").grid(row=1, column=0, sticky="w")
        ttk.Entry(form, textvariable=self.title_var, width=36).grid(row=1, column=1, sticky="w", pady=3)
        ttk.Label(form, text="Dauer (s)", style="Card.TLabel").grid(row=2, column=0, sticky="w")
        ttk.Spinbox(form, from_=1, to=120, textvariable=self.dur_var, width=6).grid(row=2, column=1, sticky="w", pady=3)
        ttk.Label(form, text="Text", style="Card.TLabel").grid(row=3, column=0, sticky="nw")
        self.text_box = tk.Text(form, width=40, height=5, wrap="word")
        self.text_box.grid(row=3, column=1, sticky="w", pady=3)
        self.param_frame = ttk.Frame(form, style="Card.TFrame")
        self.param_frame.grid(row=4, column=0, columnspan=2, sticky="we", pady=6)
        self.param_vars: dict[str, tk.Variable] = {}
        self.apply_button = ttk.Button(form, text="Aktion übernehmen", style="Accent.TButton", command=self.apply_action)
        self.apply_button.grid(row=5, column=0, columnspan=2, sticky="w", pady=6)

    def _label_to_kind(self, label: str) -> str:
        for kind, spec in EFFECTS.items():
            if spec.label == label:
                return kind
        raise KeyError(label)

    def _fill_actions(self) -> None:
        self.action_tree.delete(*self.action_tree.get_children())
        for i, action in enumerate(self.config["actions"]):
            self.action_tree.insert("", "end", iid=str(i), text=f"{i + 1}. {action['title']}",
                                    values=(EFFECTS[action["type"]].label, action["duration_seconds"]))

    def _select_action(self, index: int | None) -> None:
        if index is None or not (0 <= index < len(self.config["actions"])):
            self.selected = None
            return
        self.selected = index
        self.action_tree.selection_set(str(index))
        self._load_action_into_form(self.config["actions"][index])

    def _on_action_select(self) -> None:
        sel = self.action_tree.selection()
        # selection_set() in _select_action raises this event again; ignore it for the row already shown.
        if sel and int(sel[0]) != self.selected:
            self._select_action(int(sel[0]))

    def _load_action_into_form(self, action: dict[str, Any]) -> None:
        spec = EFFECTS[action["type"]]
        self.type_var.set(spec.label)
        self.title_var.set(action["title"])
        self.dur_var.set(str(action["duration_seconds"]))
        self.text_box.delete("1.0", "end")
        self.text_box.insert("1.0", action["text"])
        self._build_param_widgets(spec, action.get("params", {}))

    def _on_type_change(self) -> None:
        spec = EFFECTS[self._label_to_kind(self.type_var.get())]
        self.title_var.set(spec.default_title)
        self.dur_var.set(str(spec.default_duration))
        self.text_box.delete("1.0", "end")
        self.text_box.insert("1.0", spec.default_text)
        self._build_param_widgets(spec, spec.default_params())

    def _build_param_widgets(self, spec, values: dict[str, Any]) -> None:
        for child in self.param_frame.winfo_children():
            child.destroy()
        self.param_vars = {}
        for row, p in enumerate(spec.params):
            ttk.Label(self.param_frame, text=p.label, style="Card.TLabel").grid(row=row, column=0, sticky="w", padx=(0, 8), pady=2)
            value = values.get(p.name, p.default)
            if p.kind == "bool":
                var: tk.Variable = tk.BooleanVar(master=self.root, value=bool(value))
                ttk.Checkbutton(self.param_frame, variable=var, style="Card.TCheckbutton").grid(row=row, column=1, sticky="w")
            elif p.kind == "choice":
                var = tk.StringVar(master=self.root, value=value)
                ttk.Combobox(self.param_frame, textvariable=var, values=p.choices, state="readonly", width=22).grid(row=row, column=1, sticky="w")
            elif p.kind == "image":
                var = tk.StringVar(master=self.root, value=value)
                choices = list(BUILTIN_IMAGES) + [f"resource:{name}" for name in sorted(self.resources)]
                cell = ttk.Frame(self.param_frame, style="Card.TFrame")
                cell.grid(row=row, column=1, sticky="w")
                ttk.Combobox(cell, textvariable=var, values=choices, state="readonly", width=24).pack(side="left")
                ttk.Button(cell, text="Bild importieren …", command=lambda v=var: self.import_image(v)).pack(side="left", padx=4)
            elif p.kind == "int":
                var = tk.StringVar(master=self.root, value=str(value))
                ttk.Spinbox(self.param_frame, from_=p.minimum, to=p.maximum, textvariable=var, width=8).grid(row=row, column=1, sticky="w")
            else:
                var = tk.StringVar(master=self.root, value=str(value))
                ttk.Entry(self.param_frame, textvariable=var, width=26).grid(row=row, column=1, sticky="w")
            self.param_vars[p.name] = var

    def _action_from_form(self) -> dict[str, Any]:
        kind = self._label_to_kind(self.type_var.get())
        spec = EFFECTS[kind]
        params: dict[str, Any] = {}
        for p in spec.params:
            raw = self.param_vars[p.name].get()
            if p.kind == "int":
                try:
                    raw = int(raw)
                except (TypeError, ValueError) as exc:
                    raise PackageError(f"{p.label}: erwartet eine ganze Zahl.") from exc
            params[p.name] = raw
        try:
            duration = int(self.dur_var.get())
        except ValueError as exc:
            raise PackageError("Dauer: erwartet eine ganze Zahl.") from exc
        return clean_action({"type": kind, "title": self.title_var.get(), "text": self.text_box.get("1.0", "end-1c"),
                             "duration_seconds": duration, "params": params})

    def apply_action(self) -> bool:
        try:
            action = self._action_from_form()
        except PackageError as exc:
            messagebox.showerror("Aktion ungültig", str(exc), parent=self.root)
            return False
        if self.selected is None:
            self.config["actions"].append(action)
            self.selected = len(self.config["actions"]) - 1
        else:
            self.config["actions"][self.selected] = action
        self._fill_actions()
        self.action_tree.selection_set(str(self.selected))
        return True

    def move_action(self, delta: int) -> None:
        i = self.selected
        actions = self.config["actions"]
        if i is None or not 0 <= i + delta < len(actions):
            return
        actions[i], actions[i + delta] = actions[i + delta], actions[i]
        self._fill_actions()
        self._select_action(i + delta)

    def remove_action(self) -> None:
        i = self.selected
        if i is None or len(self.config["actions"]) <= 1:
            if i is not None:
                messagebox.showinfo("Mindestens eine Aktion", "Ein Paket braucht mindestens eine Aktion.", parent=self.root)
            return
        del self.config["actions"][i]
        self._fill_actions()
        self._select_action(min(i, len(self.config["actions"]) - 1))

    def import_image(self, var: tk.StringVar | None = None, path: str | None = None) -> str | None:
        path = path or filedialog.askopenfilename(parent=self.root, title="Bild importieren (PNG/GIF)",
                                                  filetypes=[("PNG/GIF", "*.png *.gif")])
        if not path:
            return None
        source = Path(path)
        name = "".join(ch for ch in source.stem.lower().replace(" ", "_") if ch.isalnum() or ch in "_-")[:36] or "bild"
        name = f"{name}{source.suffix.lower()}"
        try:
            data = source.read_bytes()
            inspect_image(name, data)
            candidate = dict(self.resources)
            candidate[name] = data
            validate_config({k: v for k, v in self.config.items() if k not in ("content_sha256", "audio", "estimated_duration_seconds", "resources")}, candidate)
        except (OSError, PackageError) as exc:
            messagebox.showerror("Bild abgelehnt", str(exc), parent=self.root)
            return None
        self.resources[name] = data
        self.runner.resources = self.resources
        ref = f"resource:{name}"
        if var is not None:
            var.set(ref)
        return ref

    # ---- preview
    def _page_preview(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Live-Vorschau", style="Heading.TLabel").pack(anchor="w")
        ttk.Label(f, text="Die Vorschau nutzt dieselbe Engine wie der Empfänger und öffnet nur MakePrank-Fenster. "
                          "Es wird keine Systemaktion ausgeführt. Esc oder ALLES STOPPEN beendet sofort.",
                  style="Muted.TLabel", wraplength=900).pack(anchor="w", pady=6)
        self.preview_audio_var = tk.BooleanVar(master=self.root, value=False)
        ttk.Checkbutton(f, text="Ton in der Vorschau abspielen (standardmäßig stumm)", variable=self.preview_audio_var,
                        command=lambda: setattr(self.audio, "muted", not self.preview_audio_var.get())).pack(anchor="w")
        row = ttk.Frame(f)
        row.pack(anchor="w", pady=10)
        ttk.Button(row, text="Vorschau starten", style="Accent.TButton", command=self.start_preview).pack(side="left")
        ttk.Button(row, text="Pause", command=self.session.pause).pack(side="left", padx=6)
        ttk.Button(row, text="Fortsetzen", command=self.session.resume).pack(side="left")
        ttk.Button(row, text="Stopp", command=lambda: self.stop_preview(StopReason.BUTTON)).pack(side="left", padx=6)
        self.preview_status = ttk.Label(f, text="", style="TLabel")
        self.preview_status.pack(anchor="w", pady=6)

    def _validated_config(self) -> dict[str, Any]:
        cfg = self._config_from_form()
        return validate_config(cfg, self.resources)

    def _config_from_form(self) -> dict[str, Any]:
        try:
            maximum = int(self.max_var.get())
        except ValueError as exc:
            raise PackageError("Maximale Sitzungsdauer: erwartet eine ganze Zahl.") from exc
        cfg = dict(self.config)
        cfg.update(name=self.name_var.get().strip(), description=self.desc_var.get(), creator_label=self.creator_var.get(),
                   max_duration_seconds=maximum, network="pairing" if self.network_var.get() else "none")
        return cfg

    def _load_into_editor(self) -> None:
        self.name_var.set(self.config["name"])
        self.desc_var.set(self.config.get("description", ""))
        self.creator_var.set(self.config.get("creator_label", "") or self.settings.creator_name)
        self.max_var.set(str(self.config["max_duration_seconds"]))
        self.network_var.set(self.config.get("network") == "pairing")
        self._fill_actions()
        self._select_action(0)

    def start_preview(self) -> bool:
        try:
            cfg = self._validated_config()
        except PackageError as exc:
            messagebox.showerror("Paket ungültig", str(exc), parent=self.root)
            return False
        self._start_preview_config(cfg)
        return True

    def _start_preview_config(self, cfg: dict[str, Any]) -> None:
        self.audio.muted = not (hasattr(self, "preview_audio_var") and self.preview_audio_var.get())
        self.session.start(cfg, source="preview")

    def stop_preview(self, reason: StopReason = StopReason.BUTTON) -> None:
        self.session.stop(reason)
        self.audio.stop()

    # ---- export
    def _page_export(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Export", style="Heading.TLabel").pack(anchor="w")
        ttk.Label(f, text="Das sieht der Empfänger vor seiner Zustimmung:", style="Muted.TLabel").pack(anchor="w", pady=4)
        self.export_text = tk.Text(f, height=22, wrap="word", bg=CARD, fg="#f9fafb", relief="flat", font=("Segoe UI", 10))
        self.export_text.pack(fill="both", expand=True)
        row = ttk.Frame(f)
        row.pack(fill="x", pady=8)
        ttk.Button(row, text="Als .mpk exportieren …", style="Accent.TButton", command=self.export).pack(side="left")
        self.export_result = ttk.Label(f, text="", style="Muted.TLabel", wraplength=900)
        self.export_result.pack(anchor="w")

    def _refresh_export(self) -> None:
        try:
            text = summarize_config(self._validated_config())
        except PackageError as exc:
            text = f"Das Paket ist noch nicht exportierbar:\n\n{exc}"
        self.export_text.configure(state="normal")
        self.export_text.delete("1.0", "end")
        self.export_text.insert("1.0", text)
        self.export_text.configure(state="disabled")

    def export(self, path: str | None = None) -> Path | None:
        try:
            cfg = self._validated_config()
        except PackageError as exc:
            messagebox.showerror("Export nicht möglich", str(exc), parent=self.root)
            return None
        if path is None:
            if not messagebox.askokcancel("Export bestätigen", summarize_config(cfg), parent=self.root):
                return None
            path = filedialog.asksaveasfilename(parent=self.root, defaultextension=".mpk", initialfile=cfg["name"],
                                                filetypes=[("MakePrank-Paket", "*.mpk")])
            if not path:
                return None
        try:
            dest = save_package(path, cfg, self.resources)
        except (PackageError, OSError) as exc:
            messagebox.showerror("Export fehlgeschlagen", str(exc), parent=self.root)
            return None
        self.config["created_utc"] = cfg["created_utc"]
        self.exported_path = dest
        self.exported_file_sha = file_sha256(dest)
        self.exported_content_sha = cfg["content_sha256"]
        self.export_result.configure(text=f"Exportiert: {dest}\nSHA-256: {self.exported_file_sha}")
        self.log.record("export", dest.name, self.exported_file_sha)
        return dest

    # ---- project open/new
    def new_project(self) -> None:
        self.stop_preview(StopReason.REPLACED)
        self.config = new_config()
        self.resources.clear()
        self.exported_path, self.exported_file_sha, self.exported_content_sha = None, "", ""
        self._load_into_editor()
        self._refresh_dashboard()

    def open_project(self, path: str | None = None) -> bool:
        path = path or filedialog.askopenfilename(parent=self.root, filetypes=[("MakePrank-Paket", "*.mpk")])
        if not path:
            return False
        try:
            pkg = load_package_full(path)
        except PackageError as exc:
            messagebox.showerror("Paket ungültig", str(exc), parent=self.root)
            return False
        self.stop_preview(StopReason.REPLACED)
        self.config = dict(pkg.config)
        self.resources.clear()
        self.resources.update(pkg.resources)
        self.runner.resources = self.resources
        self.exported_path, self.exported_file_sha, self.exported_content_sha = pkg.path, pkg.file_sha256, pkg.config["content_sha256"]
        self._load_into_editor()
        return True

    # ---- clients / pairing
    def _page_clients(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Clients / Pairing", style="Heading.TLabel").pack(anchor="w")
        ttk.Label(f, text="Der Empfänger klickt „Pairing starten“, bestätigt deine Verbindung und gibt dir den Link. "
                          "Verbindungen sind standardmäßig nur im lokalen Netz erlaubt.", style="Muted.TLabel", wraplength=1000).pack(anchor="w", pady=4)
        row = ttk.Frame(f)
        row.pack(fill="x", pady=6)
        self.link_var = tk.StringVar(master=self.root)
        ttk.Entry(row, textvariable=self.link_var, width=90).pack(side="left")
        self.connect_button = ttk.Button(row, text="Verbinden", style="Accent.TButton", command=self.connect_from_link)
        self.connect_button.pack(side="left", padx=8)
        self.client_tree = ttk.Treeview(f, columns=("name", "status", "scope", "since", "last"), show="tree headings", height=8)
        for col, text, width in (("#0", "Client-ID", 150), ("name", "Empfänger", 160), ("status", "Status", 110),
                                 ("scope", "Berechtigungsumfang", 330), ("since", "Sitzungsdauer", 100), ("last", "Letzte Aktivität", 120)):
            self.client_tree.heading(col, text=text)
            self.client_tree.column(col, width=width)
        self.client_tree.pack(fill="x", pady=8)
        btns = ttk.Frame(f)
        btns.pack(fill="x")
        ttk.Button(btns, text="Gewählte Aktion senden", command=self.send_selected_effect).pack(side="left")
        ttk.Button(btns, text="Stopp senden", command=self.send_stop).pack(side="left", padx=6)
        ttk.Button(btns, text="Trennen", command=self.disconnect_selected).pack(side="left")
        ttk.Button(btns, text="Sperren / Widerrufen", command=self.block_selected).pack(side="left", padx=6)
        self.client_msg = ttk.Label(f, text="", style="Muted.TLabel", wraplength=1000)
        self.client_msg.pack(anchor="w", pady=8)

    def connect_from_link(self) -> None:
        link = self.link_var.get()
        conn = CreatorConnection(self.settings.creator_name, on_event=self._on_conn_event,
                                 allow_public=self.settings.allow_public_hosts)
        try:
            from .protocol import parse_pairing_uri, ProtocolError  # noqa: F401
            target = parse_pairing_uri(link, self.settings.allow_public_hosts)
        except Exception as exc:
            self.client_msg.configure(text=f"Ungültiger Link: {exc}")
            return
        if target.host in self.blocked:
            self.client_msg.configure(text="Dieser Empfänger ist gesperrt.")
            return
        self.connect_button.configure(state="disabled")
        self.client_msg.configure(text="Verbinde … der Empfänger muss die Verbindung bestätigen.")

        def work() -> None:
            try:
                conn.connect(target)
            except ConnectionFailed as exc:
                self.main.post(lambda: self._connect_failed(str(exc)))
                return
            self.main.post(lambda: self._connected(conn))

        threading.Thread(target=work, name="makeprank-connect", daemon=True).start()

    def _connect_failed(self, message: str) -> None:
        self.connect_button.configure(state="normal")
        self.client_msg.configure(text=message)
        self.log.record("connect_failed", message[:150])

    def _connected(self, conn: CreatorConnection) -> None:
        self.connect_button.configure(state="normal")
        self.connections[conn.client_id] = conn
        self.connected_at[conn.client_id] = time.monotonic()
        self.client_msg.configure(text=f"Verbunden mit {conn.receiver_name}.")
        self.log.record("client_connected", f"{conn.receiver_name} ({conn.client_id})")
        self._refresh_clients()

    def _on_conn_event(self, kind: str, detail: str) -> None:
        self.main.post(lambda: (self.log.record(f"client_{kind}", detail[:150]), self._refresh_clients()))

    def _refresh_clients(self) -> None:
        if not hasattr(self, "client_tree"):
            return
        selected = self.client_tree.selection()
        self.client_tree.delete(*self.client_tree.get_children())
        now = time.monotonic()
        for cid, conn in self.connections.items():
            status = "verbunden" if conn.connected else ("gesperrt" if conn.peer.split(":")[0] in self.blocked else "getrennt")
            since = f"{int(now - self.connected_at.get(cid, now))} s"
            last = time.strftime("%H:%M:%S", time.localtime(conn.last_activity)) if conn.last_activity else "—"
            self.client_tree.insert("", "end", iid=cid, text=cid, values=(conn.receiver_name, status,
                                    "Nur Effekte des geöffneten Pakets · START_EFFECT / STOP_SESSION", since, last))
        for cid in selected:
            if self.client_tree.exists(cid):
                self.client_tree.selection_set(cid)

    def _selected_connection(self) -> CreatorConnection | None:
        sel = self.client_tree.selection()
        if not sel and len(self.connections) == 1:
            return next(iter(self.connections.values()))
        return self.connections.get(sel[0]) if sel else None

    def send_selected_effect(self) -> bool:
        conn = self._selected_connection()
        if conn is None or not conn.connected:
            self.client_msg.configure(text="Bitte zuerst einen verbundenen Empfänger wählen.")
            return False
        if self.selected is None:
            self.client_msg.configure(text="Bitte im Paket-Editor eine Aktion wählen.")
            return False
        try:
            current = self._validated_config()
        except PackageError as exc:
            self.client_msg.configure(text=f"Paket ungültig: {exc}")
            return False
        if not self.exported_file_sha or current["content_sha256"] != self.exported_content_sha:
            self.client_msg.configure(text="Das Paket wurde seit dem Export verändert. Bitte neu exportieren und dem Empfänger die neue Datei geben.")
            return False
        action = current["actions"][self.selected]
        try:
            ack = conn.start_effect(self.exported_file_sha, self.selected, action["type"])
        except ConnectionFailed as exc:
            self.client_msg.configure(text=f"Senden fehlgeschlagen: {exc}")
            self._refresh_clients()
            return False
        self.client_msg.configure(text=("Gestartet: " if ack.ok else "Abgelehnt: ") + ack.detail)
        self.log.record("remote_effect", f"{EFFECTS[action['type']].label}: {ack.detail}", self.exported_file_sha)
        return ack.ok

    def send_stop(self) -> None:
        conn = self._selected_connection()
        if conn and conn.connected:
            try:
                conn.stop_session()
                self.client_msg.configure(text="Stopp gesendet.")
            except ConnectionFailed as exc:
                self.client_msg.configure(text=str(exc))

    def disconnect_selected(self) -> None:
        conn = self._selected_connection()
        if conn:
            conn.disconnect()
            self._refresh_clients()

    def block_selected(self) -> None:
        conn = self._selected_connection()
        if conn:
            self.blocked.add(conn.peer.split(":")[0])
            conn.disconnect()
            self.log.record("client_blocked", conn.client_id)
            self._refresh_clients()

    # ---- log / settings / help
    def _page_log(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Sitzungsprotokoll", style="Heading.TLabel").pack(anchor="w")
        ttk.Label(f, text="Zeit, Paket-Hash, Effekt, Start/Stopp, Fehler, Pairing/Trennungen. Es werden weder fremde Inhalte noch Tastatureingaben erfasst.",
                  style="Muted.TLabel", wraplength=1000).pack(anchor="w", pady=4)
        self.log_text = tk.Text(f, bg=CARD, fg="#d1d5db", relief="flat", font=("Consolas", 10))
        self.log_text.pack(fill="both", expand=True)
        self.log_text.configure(state="disabled")

    def _append_log(self, event: dict[str, Any]) -> None:
        line = self.log.format_line(event) + "\n"
        for widget in (getattr(self, "log_text", None), getattr(self, "dash_log", None)):
            if widget is None:
                continue
            try:
                widget.configure(state="normal")
                widget.insert("end", line)
                widget.see("end")
                widget.configure(state="disabled")
            except tk.TclError:
                pass

    def _page_settings(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Einstellungen", style="Heading.TLabel").pack(anchor="w")
        self.set_name = tk.StringVar(master=self.root, value=self.settings.creator_name)
        self.set_public = tk.BooleanVar(master=self.root, value=self.settings.allow_public_hosts)
        row = ttk.Frame(f)
        row.pack(anchor="w", pady=10)
        ttk.Label(row, text="Creator-Name (sieht der Empfänger):").pack(side="left")
        ttk.Entry(row, textvariable=self.set_name, width=26).pack(side="left", padx=8)
        ttk.Checkbutton(f, variable=self.set_public, text="Auch öffentliche IP-Adressen erlauben (nicht empfohlen; nur mit selbst konfigurierter Infrastruktur)").pack(anchor="w")
        ttk.Button(f, text="Speichern", style="Accent.TButton", command=self.save_settings).pack(anchor="w", pady=10)

    def save_settings(self) -> None:
        from .protocol import NAME_RE
        name = self.set_name.get().strip()
        if not NAME_RE.fullmatch(name):
            messagebox.showerror("Name ungültig", "1–40 Zeichen (Buchstaben, Ziffern, Leerzeichen, . ( ) -).", parent=self.root)
            return
        self.settings.creator_name = name
        self.settings.allow_public_hosts = bool(self.set_public.get())
        self.settings.save()

    def _page_help(self, f: ttk.Frame) -> None:
        ttk.Label(f, text="Hilfe", style="Heading.TLabel").pack(anchor="w")
        text = tk.Text(f, wrap="word", bg=CARD, fg="#f9fafb", relief="flat", font=("Segoe UI", 11))
        text.pack(fill="both", expand=True, pady=8)
        text.insert("1.0", HELP_TEXT)
        text.configure(state="disabled")

    # ------------------------------------------------------------------ global
    def stop_everything(self) -> None:
        """Stops local effects and every managed remote session; never needs the network to succeed."""
        self.stop_preview(StopReason.GLOBAL_STOP)
        for conn in list(self.connections.values()):
            if conn.connected:
                try:
                    conn.stop_session()
                except ConnectionFailed:
                    pass
        self._refresh_clients()

    def _refresh_status(self) -> None:
        if self.session.active:
            self.status_var.set(f"Vorschau läuft – {', '.join(self.session.active_effect_labels)}")
        else:
            self.status_var.set("Bereit")

    def _tick(self) -> None:
        if self.session.active and hasattr(self, "preview_status"):
            self.preview_status.configure(text=f"Restdauer Effekt {self.session.remaining_in_action:.0f} s · Sitzung {self.session.remaining_seconds:.0f} s")
        elif hasattr(self, "preview_status"):
            self.preview_status.configure(text="Keine Vorschau aktiv.")
        self._refresh_clients()
        try:
            self._tick_handle = self.root.after(500, self._tick)
        except tk.TclError:
            pass

    def shutdown(self) -> None:
        self.stop_preview(StopReason.WINDOW_CLOSE)
        for conn in list(self.connections.values()):
            conn.disconnect()
        self.main.close()
        if self._tick_handle is not None:
            try:
                self.root.after_cancel(self._tick_handle)
            except tk.TclError:
                pass
            self._tick_handle = None

    def close(self) -> None:
        self.shutdown()
        self.root.destroy()


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="MakePrank Creator")
    parser.add_argument("package", nargs="?", help=".mpk-Datei, die im Editor geöffnet werden soll")
    args = parser.parse_args(argv)
    configure_logging()
    root = tk.Tk()
    app = CreatorApp(root)
    if args.package:
        root.after(200, lambda: app.open_project(args.package))
    root.mainloop()


if __name__ == "__main__":
    main()
