"""MakePrank Empfänger-Client (Windows/Tk).

Always visible, always stoppable: STOPP button, Escape and closing the window end
every effect without needing the network or the creator.
"""
from __future__ import annotations

import argparse
import logging
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk
from typing import Any

from . import __version__
from .audio import AudioBackend, AudioPlayer
from .audit import SessionLog
from .catalog import EFFECTS
from .effects import TkEffectRunner
from .package_format import ConsentStore, LoadedPackage, PackageError, file_sha256, load_package_full, summarize_config
from .receiver_server import PairingInfo, ReceiverServer
from .session import SessionManager, SessionState, StopReason
from .settings import Settings, configure_logging, data_dir
from .ui_common import BAD, BG, CARD, MUTED, OK, ConsentDialog, MainThreadCalls, TkHost, apply_theme

APP_NAME = "MakePrank Empfänger"
logger = logging.getLogger("makeprank")


class ClientApp:
    def __init__(self, root: tk.Tk, *, settings: Settings | None = None, consent: ConsentStore | None = None,
                 audio_backend: AudioBackend | None = None, log: SessionLog | None = None):
        self.root = root
        self.settings = settings or Settings.load()
        self.consent = consent or ConsentStore(data_dir() / "consents.json")
        self.log = log or SessionLog(data_dir() / "sessions.jsonl")
        self.audio = AudioPlayer(audio_backend, muted=self.settings.muted)
        self.package: LoadedPackage | None = None
        self.remote_allowed = False
        self.server: ReceiverServer | None = None
        self.pairing: PairingInfo | None = None
        self.main = MainThreadCalls(root)
        self.runner = TkEffectRunner(root, {}, self.audio, on_stop_all=lambda: self.stop_all(StopReason.BUTTON))
        self.session = SessionManager(TkHost(root), self.runner, self.log)
        self.session.integrity_check = self._integrity
        self.session.on_change.append(self._refresh)
        self.log.add_listener(lambda event: self.main.post(lambda e=event: self._append_log(e)))
        self._pairing_started = 0.0
        self._tick_handle: Any = None

        root.title(f"{APP_NAME} · v{__version__}")
        root.geometry("860x820")
        root.minsize(760, 700)
        apply_theme(root)
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.bind_all("<Escape>", lambda _e: self.stop_all(StopReason.ESCAPE))
        self._build()
        self._refresh()
        self._tick()

    # ------------------------------------------------------------------ UI
    def _build(self) -> None:
        r = self.root
        header = ttk.Frame(r, padding=(20, 14, 20, 6))
        header.pack(fill="x")
        ttk.Label(header, text="MakePrank", style="Heading.TLabel").pack(side="left")
        ttk.Label(header, text="Empfänger-Client · alles sichtbar · jederzeit stoppbar", style="Muted.TLabel").pack(side="left", padx=14, pady=(8, 0))

        self.indicator = tk.Label(r, text="", font=("Segoe UI", 11, "bold"), anchor="w", padx=14, pady=6)
        self.indicator.pack(fill="x", padx=20)
        self.disconnect_button = ttk.Button(self.indicator, text="Trennen", command=self.disconnect_network)

        pkg = ttk.Frame(r, style="Card.TFrame", padding=14)
        pkg.pack(fill="x", padx=20, pady=(10, 6))
        ttk.Label(pkg, text="Paket", style="Sub.TLabel").pack(anchor="w")
        self.pkg_text = tk.Text(pkg, height=8, wrap="word", bg=CARD, fg="#f9fafb", relief="flat", font=("Segoe UI", 10))
        self.pkg_text.pack(fill="x", pady=6)
        self.pkg_text.configure(state="disabled")
        row = ttk.Frame(pkg, style="Card.TFrame")
        row.pack(fill="x")
        ttk.Button(row, text="Paket öffnen …", command=self.choose_package).pack(side="left")
        self.start_button = ttk.Button(row, text="Prüfen & starten …", style="Accent.TButton", command=self.request_start, state="disabled")
        self.start_button.pack(side="left", padx=8)
        self.remote_button = ttk.Button(row, text="Nur Fernsteuerung erlauben …", command=self.request_remote, state="disabled")
        self.remote_button.pack(side="left")

        run = ttk.Frame(r, style="Card.TFrame", padding=14)
        run.pack(fill="x", padx=20, pady=6)
        ttk.Label(run, text="Sitzung", style="Sub.TLabel").pack(anchor="w")
        self.status_label = ttk.Label(run, text="", style="Card.TLabel")
        self.status_label.pack(anchor="w", pady=(4, 0))
        self.effect_label = ttk.Label(run, text="", style="Card.TLabel")
        self.effect_label.pack(anchor="w")
        self.remaining_label = ttk.Label(run, text="", style="Card.TLabel")
        self.remaining_label.pack(anchor="w")
        controls = ttk.Frame(run, style="Card.TFrame")
        controls.pack(fill="x", pady=(8, 0))
        self.pause_button = ttk.Button(controls, text="Pause", command=self.session.pause)
        self.pause_button.pack(side="left")
        self.resume_button = ttk.Button(controls, text="Fortsetzen", command=self.session.resume)
        self.resume_button.pack(side="left", padx=6)
        self.mute_var = tk.BooleanVar(master=r, value=self.settings.muted)
        ttk.Checkbutton(controls, text="Ton stummschalten", variable=self.mute_var, style="Card.TCheckbutton",
                        command=self._toggle_mute).pack(side="left", padx=12)
        self.stop_button = ttk.Button(run, text="STOPP  (Esc)", style="Stop.TButton", command=lambda: self.stop_all(StopReason.BUTTON))
        self.stop_button.pack(fill="x", pady=(12, 0))

        net = ttk.Frame(r, style="Card.TFrame", padding=14)
        net.pack(fill="x", padx=20, pady=6)
        ttk.Label(net, text="Netzwerk (standardmäßig aus)", style="Sub.TLabel").pack(anchor="w")
        self.lan_var = tk.BooleanVar(master=r, value=False)
        ttk.Checkbutton(net, style="Card.TCheckbutton", variable=self.lan_var,
                        text="Im lokalen Netzwerk erreichbar machen (sonst nur Test auf diesem PC)").pack(anchor="w", pady=(4, 0))
        nrow = ttk.Frame(net, style="Card.TFrame")
        nrow.pack(fill="x", pady=6)
        self.pair_button = ttk.Button(nrow, text="Pairing starten", command=self.start_pairing, state="disabled")
        self.pair_button.pack(side="left")
        self.cancel_pair_button = ttk.Button(nrow, text="Pairing abbrechen", command=self.disconnect_network, state="disabled")
        self.cancel_pair_button.pack(side="left", padx=6)
        self.pair_info = ttk.Label(net, text="", style="Card.TLabel", wraplength=780, justify="left")
        self.pair_info.pack(anchor="w")
        self.link_entry = ttk.Entry(net, state="readonly")
        self.link_entry.pack(fill="x", pady=(4, 0))

        logf = ttk.Frame(r, padding=(20, 6, 20, 14))
        logf.pack(fill="both", expand=True)
        self.log_text = tk.Text(logf, height=7, bg=CARD, fg=MUTED, relief="flat", font=("Consolas", 9))
        self.log_text.pack(fill="both", expand=True)
        self.log_text.configure(state="disabled")

    def _append_log(self, event: dict[str, Any]) -> None:
        try:
            self.log_text.configure(state="normal")
            self.log_text.insert("end", self.log.format_line(event) + "\n")
            self.log_text.see("end")
            self.log_text.configure(state="disabled")
        except tk.TclError:
            pass

    def _set_pkg_text(self, text: str) -> None:
        self.pkg_text.configure(state="normal")
        self.pkg_text.delete("1.0", "end")
        self.pkg_text.insert("1.0", text)
        self.pkg_text.configure(state="disabled")

    def _refresh(self) -> None:
        active = self.session.active
        if active:
            state = "pausiert" if self.session.state is SessionState.PAUSED else "läuft"
            self.status_label.configure(text=f"Status: {state} ({self.session.source})", foreground=OK)
            self.effect_label.configure(text="Aktiver Effekt: " + ", ".join(self.session.active_effect_labels))
        else:
            reason = self.session.last_stop_reason.value if self.session.last_stop_reason else "—"
            self.status_label.configure(text=f"Status: keine Sitzung (zuletzt: {reason})", foreground=MUTED)
            self.effect_label.configure(text="Aktiver Effekt: keiner")
        self.pause_button.configure(state="normal" if self.session.state is SessionState.RUNNING else "disabled")
        self.resume_button.configure(state="normal" if self.session.state is SessionState.PAUSED else "disabled")
        has_pkg = self.package is not None
        self.start_button.configure(state="normal" if has_pkg else "disabled")
        pairing_ok = has_pkg and self.package.config["network"] == "pairing"  # type: ignore[union-attr]
        self.remote_button.configure(state="normal" if pairing_ok else "disabled")
        listening = bool(self.server and (self.server.listening or self.server.paired))
        self.pair_button.configure(state="normal" if (pairing_ok and self.remote_allowed and not listening) else "disabled")
        self.cancel_pair_button.configure(state="normal" if listening else "disabled")
        self._refresh_indicator()

    def _refresh_indicator(self) -> None:
        if self.server and self.server.paired:
            self.indicator.configure(text=f"● VERBUNDEN mit Creator „{self.server.creator_name}“ (Client {self.server.client_id[:8]}) – Fernsteuerung aktiv",
                                     bg="#14532d", fg="#dcfce7")
            self.disconnect_button.pack(side="right")
        elif self.server and self.server.listening:
            self.indicator.configure(text="● Pairing aktiv – wartet auf Creator", bg="#713f12", fg="#fef3c7")
            self.disconnect_button.pack_forget()
        else:
            self.indicator.configure(text="○ Keine Netzwerkverbindung", bg="#1f2937", fg="#d1d5db")
            self.disconnect_button.pack_forget()

    def _tick(self) -> None:
        if self.session.active:
            self.remaining_label.configure(
                text=f"Restdauer Effekt: {self.session.remaining_in_action:.0f} s · Sitzung: {self.session.remaining_seconds:.0f} s")
        else:
            self.remaining_label.configure(text="")
        if self.server and self.server.listening and not self.server.paired and self.pairing:
            left = max(0, int(self.server.pairing_ttl - (self.server.clock() - (self._pairing_started))))
            self.pair_info.configure(text=self._pair_text(left))
        elif not (self.server and (self.server.listening or self.server.paired)):
            if self.pairing:
                self.pairing = None
                self.pair_info.configure(text="")
                self._set_link("")
        self._refresh_indicator()
        listening = bool(self.server and (self.server.listening or self.server.paired))
        self.cancel_pair_button.configure(state="normal" if listening else "disabled")
        if self.package is not None and self.package.config["network"] == "pairing":
            self.pair_button.configure(state="normal" if (self.remote_allowed and not listening) else "disabled")
        try:
            self._tick_handle = self.root.after(300, self._tick)
        except tk.TclError:
            pass

    # ------------------------------------------------------------------ package & consent
    def choose_package(self) -> None:
        path = filedialog.askopenfilename(parent=self.root, title="MakePrank-Paket öffnen",
                                          filetypes=[("MakePrank-Paket", "*.mpk"), ("Alle Dateien", "*.*")])
        if path:
            self.open_package(path)

    def open_package(self, path: str | Path) -> bool:
        """Validate completely before anything can start. Resets every earlier consent."""
        self.stop_all(StopReason.REPLACED)
        self.revoke_remote()
        try:
            package = load_package_full(path)
        except PackageError as exc:
            self.package = None
            self._set_pkg_text("")
            messagebox.showerror("Paket ungültig", f"Das Paket wurde abgelehnt und nichts wurde gestartet:\n\n{exc}", parent=self.root)
            self.log.record("package_rejected", str(exc)[:200])
            self._refresh()
            return False
        self.package = package
        self.runner.resources = package.resources
        self._set_pkg_text(summarize_config(package.config))
        self.log.record("package_opened", package.config["name"], package.file_sha256)
        self._refresh()
        return True

    def _verify_unchanged(self) -> bool:
        pkg = self.package
        if pkg is None or pkg.path is None:
            return False
        try:
            current = file_sha256(pkg.path)
        except OSError:
            current = None
        if current != pkg.file_sha256:
            self.log.record("package_changed", "Paket wurde seit dem Öffnen verändert – neue Zustimmung nötig", pkg.file_sha256)
            messagebox.showwarning("Paket verändert", "Die Paketdatei hat sich geändert. Bitte öffne sie erneut und stimme neu zu.", parent=self.root)
            self.package = None
            self.revoke_remote()
            self._set_pkg_text("")
            self._refresh()
            return False
        return True

    def request_start(self) -> bool:
        pkg = self.package
        if pkg is None or not self._verify_unchanged():
            return False
        dialog = ConsentDialog(self.root, pkg.config, mode="local")
        if dialog.result is None:
            self.log.record("consent_declined", "Zustimmung abgelehnt – nichts gestartet", pkg.file_sha256)
            return False
        self.consent.accept(pkg.file_sha256, network=dialog.result["network"])
        self.remote_allowed = bool(dialog.result["network"]) and pkg.config["network"] == "pairing"
        self.start_local()
        return True

    def request_remote(self) -> bool:
        pkg = self.package
        if pkg is None or not self._verify_unchanged():
            return False
        dialog = ConsentDialog(self.root, pkg.config, mode="remote")
        if dialog.result is None:
            self.log.record("consent_declined", "Fernsteuerung nicht erlaubt", pkg.file_sha256)
            return False
        self.consent.accept(pkg.file_sha256, network=True)
        self.remote_allowed = True
        self._refresh()
        return True

    def revoke_remote(self) -> None:
        self.remote_allowed = False
        if self.server and (self.server.listening or self.server.paired):
            self.server.stop_pairing()

    def start_local(self) -> None:
        pkg = self.package
        if pkg is None or pkg.path is None:
            return
        self.session.start(pkg.config, package_path=pkg.path, package_sha=pkg.file_sha256, source="local")

    def _integrity(self) -> StopReason | None:
        pkg = self.package
        if pkg is None or pkg.path is None:
            return StopReason.FILE_DELETED
        if not pkg.path.is_file():
            return StopReason.FILE_DELETED
        try:
            if file_sha256(pkg.path) != pkg.file_sha256:
                return StopReason.FILE_CHANGED
        except OSError:
            return StopReason.FILE_DELETED
        return None

    # ------------------------------------------------------------------ stopping
    def stop_all(self, reason: StopReason = StopReason.BUTTON) -> None:
        """Local stop: independent of network and creator."""
        self.session.stop(reason)
        self.audio.stop()

    def _toggle_mute(self) -> None:
        self.audio.muted = self.mute_var.get()
        self.settings.muted = self.audio.muted
        self.settings.save()
        if self.audio.muted:
            self.audio.stop()

    def shutdown(self) -> None:
        """Stop effects, close sockets and cancel timers (also used by tests)."""
        self.stop_all(StopReason.WINDOW_CLOSE)
        if self.server:
            self.server.stop_pairing()
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

    # ------------------------------------------------------------------ network
    def start_pairing(self) -> None:
        pkg = self.package
        if pkg is None or not self.remote_allowed or pkg.config["network"] != "pairing" or not self._verify_unchanged():
            return
        if self.server and (self.server.listening or self.server.paired):
            return
        bind = "0.0.0.0" if self.lan_var.get() else "127.0.0.1"
        self.server = ReceiverServer(_Handler(self), receiver_name=self.settings.effective_receiver_name[:40],
                                     bind_host=bind, log=self.log)
        try:
            self.pairing = self.server.start_pairing()
        except OSError as exc:
            messagebox.showerror("Pairing nicht möglich", str(exc), parent=self.root)
            return
        self._pairing_started = self.server.clock()
        self.pair_info.configure(text=self._pair_text(int(self.pairing.expires_in)))
        self._set_link(self.pairing.uri)
        self._refresh()

    def _pair_text(self, left: int) -> str:
        assert self.pairing is not None
        return (f"Pairing-Code: {self.pairing.display_code}    Adresse: {self.pairing.host}:{self.pairing.port}    "
                f"gültig noch {left} s\nZertifikat-Fingerabdruck: {self.pairing.fingerprint[:16]}… "
                "(der vollständige Link unten enthält alles, was der Creator braucht)")

    def _set_link(self, text: str) -> None:
        self.link_entry.configure(state="normal")
        self.link_entry.delete(0, "end")
        self.link_entry.insert(0, text)
        self.link_entry.configure(state="readonly")

    def disconnect_network(self) -> None:
        if self.server:
            self.server.stop_pairing()
        self._refresh()

    # called (via MainThreadCalls) from the server thread -----------------------
    def _confirm_pairing(self, creator_name: str, peer: str) -> bool:
        answer = messagebox.askyesno(
            "Verbindung bestätigen",
            f"Ein Creator möchte sich verbinden:\n\nName: {creator_name}\nAdresse: {peer}\n\n"
            "Er darf danach NUR die Effekte des geöffneten Pakets auslösen. Du kannst jederzeit STOPP drücken oder trennen.\n\nVerbinden?",
            parent=self.root)
        return bool(answer)

    def _remote_start(self, sha: str, index: int, effect_id: str) -> tuple[bool, str]:
        pkg = self.package
        if pkg is None or not self.remote_allowed:
            return False, "Fernsteuerung ist nicht erlaubt."
        if not self._verify_silent():
            self.revoke_remote()
            return False, "Paket wurde verändert."
        if sha != pkg.file_sha256:
            return False, "Dieses Paket wurde nicht akzeptiert."
        actions = pkg.config["actions"]
        if not 0 <= index < len(actions):
            return False, "Unbekannte Aktion."
        action = actions[index]
        if action["type"] != effect_id or effect_id not in EFFECTS:
            return False, "Effekt passt nicht zum Paket."
        single = dict(pkg.config)
        single["actions"] = [action]
        single["max_duration_seconds"] = action["duration_seconds"] + 1
        single["estimated_duration_seconds"] = action["duration_seconds"]
        self.session.start(single, package_path=pkg.path, package_sha=pkg.file_sha256, source="remote")
        return True, "Effekt gestartet."

    def _verify_silent(self) -> bool:
        pkg = self.package
        try:
            return pkg is not None and pkg.path is not None and file_sha256(pkg.path) == pkg.file_sha256
        except OSError:
            return False

    def _remote_disconnected(self, reason: str) -> None:
        if self.session.active and self.session.source == "remote":
            kind = StopReason.NETWORK_TIMEOUT if "Timeout" in reason else StopReason.DISCONNECT
            self.stop_all(kind)
        self._refresh()

    def _status(self) -> dict[str, Any]:
        state = self.session.state.value if self.session.active else "idle"
        return {"state": state, "package_sha256": self.package.file_sha256 if self.package else "",
                "remaining_seconds": self.session.remaining_seconds, "effects": self.session.active_effect_labels}


class _Handler:
    """Adapter run on the server thread; every UI/session access is marshalled to Tk."""

    def __init__(self, app: ClientApp):
        self.app = app

    def confirm_pairing(self, creator_name: str, peer: str) -> bool:
        return bool(self.app.main.call(lambda: self.app._confirm_pairing(creator_name, peer), timeout=65, default=False))

    def on_paired(self, creator_name: str, client_id: str) -> None:
        self.app.main.call(self.app._refresh)

    def on_start_effect(self, package_sha256: str, action_index: int, effect_id: str) -> tuple[bool, str]:
        return self.app.main.call(lambda: self.app._remote_start(package_sha256, action_index, effect_id),
                                  default=(False, "Empfänger antwortet nicht."))

    def on_stop_session(self) -> None:
        self.app.main.call(lambda: self.app.stop_all(StopReason.REMOTE_STOP))

    def status(self) -> dict[str, Any]:
        return self.app.main.call(self.app._status, default={"state": "idle"})

    def on_disconnected(self, reason: str) -> None:
        self.app.main.call(lambda: self.app._remote_disconnected(reason), default=None)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="MakePrank Empfänger-Client")
    parser.add_argument("package", nargs="?", help=".mpk-Datei, die geöffnet werden soll (es startet nichts ohne Zustimmung)")
    args = parser.parse_args(argv)
    configure_logging()
    root = tk.Tk()
    app = ClientApp(root)
    if args.package:
        root.after(200, lambda: app.open_package(args.package))
    root.mainloop()


if __name__ == "__main__":
    main()
