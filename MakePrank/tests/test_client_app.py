"""Receiver client: consent, stop paths, integrity, remote control (real Tk + real TLS on loopback)."""
import threading
import time
import tkinter as tk
from tkinter import messagebox

import pytest

from makeprank.audio import NullBackend
from makeprank.catalog import default_action
from makeprank.client_app import ClientApp
from makeprank.creator_client import ConnectionFailed, CreatorConnection
from makeprank.package_format import ConsentStore, file_sha256, new_config, save_package
from makeprank.session import StopReason
from makeprank.settings import Settings
from makeprank.ui_common import ConsentDialog

from helpers import make_png


@pytest.fixture
def env(tmp_path, monkeypatch):
    try:
        root = tk.Tk()
    except tk.TclError as exc:
        pytest.skip(f"keine Anzeige: {exc}")
    root.withdraw()
    errors = []
    monkeypatch.setattr(messagebox, "showerror", lambda *a, **k: errors.append(a))
    monkeypatch.setattr(messagebox, "showwarning", lambda *a, **k: errors.append(a))
    monkeypatch.setattr(messagebox, "showinfo", lambda *a, **k: None)
    app = ClientApp(root, settings=Settings(), consent=ConsentStore(tmp_path / "consents.json"), audio_backend=NullBackend())
    app.errors = errors
    app.tmp = tmp_path
    yield app
    try:
        app.shutdown()
        root.destroy()
    except tk.TclError:
        pass


def toplevels(app):
    return [w for w in app.root.winfo_children() if isinstance(w, tk.Toplevel)]


def make_pkg(tmp_path, kinds=("toast", "error_dialog"), duration=30, network="none", name="pkg.mpk"):
    cfg = new_config("App-Test")
    cfg["network"] = network
    cfg["max_duration_seconds"] = 600
    cfg["actions"] = []
    for kind in kinds:
        action = default_action(kind)
        action["duration_seconds"] = duration
        cfg["actions"].append(action)
    return save_package(tmp_path / name, cfg)


def accept_consent(monkeypatch, app, tick=True, network=False):
    """Replace the modal wait with a driver that ticks the box (or not) and presses the button."""
    original = ConsentDialog.__init__

    def patched(self, parent, config, *, mode="local", wait=True):
        original(self, parent, config, mode=mode, wait=False)
        patched.dialog = self
        patched.initial = (self.consent_var.get(), str(self.start_button.cget("state")))
        if tick:
            self.consent_var.set(True)
            self._update()
            self.network_var.set(network)
            self.accept()
        else:
            self.decline()

    monkeypatch.setattr(ConsentDialog, "__init__", patched)
    return patched


def pump(app, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        app.root.update()
        time.sleep(0.01)


def test_invalid_package_is_rejected_before_anything_starts(env):
    bad = env.tmp / "bad.mpk"
    bad.write_bytes(b"kein zip")
    assert env.open_package(bad) is False
    assert env.errors and env.package is None
    assert not env.session.active and toplevels(env) == []
    assert str(env.start_button.cget("state")) == "disabled"


def test_consent_checkbox_not_preselected_and_start_disabled(env, monkeypatch):
    path = make_pkg(env.tmp)
    assert env.open_package(path)
    probe = accept_consent(monkeypatch, env, tick=False)
    env.request_start()
    assert probe.initial == (False, "disabled")


def test_declining_consent_starts_nothing(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env, tick=False)
    assert env.request_start() is False
    pump(env, 0.2)
    assert not env.session.active and toplevels(env) == []
    assert not env.consent.is_accepted(env.package.file_sha256)


def test_closing_consent_dialog_with_escape_declines(env):
    env.open_package(make_pkg(env.tmp))
    dialog = ConsentDialog(env.root, env.package.config, wait=False)
    dialog.window.event_generate("<Escape>")
    env.root.update()
    assert dialog.result is None


def test_start_button_cannot_be_forced_without_checkbox(env):
    env.open_package(make_pkg(env.tmp))
    dialog = ConsentDialog(env.root, env.package.config, wait=False)
    dialog.accept()  # even if invoked programmatically
    assert dialog.result is None
    dialog.window.destroy()


def test_accepting_runs_effects_and_records_consent_for_exact_hash(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    assert env.request_start()
    pump(env, 0.5)
    assert env.session.active and toplevels(env)
    assert env.consent.is_accepted(env.package.file_sha256)


def test_stop_button_stops_everything(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    env.stop_button.invoke()
    env.root.update()
    assert env.session.last_stop_reason is StopReason.BUTTON
    assert toplevels(env) == [] and env.runner.windows == set()


def test_escape_stops_everything(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    # Escape on the main window (global binding) ...
    env.root.deiconify()
    env.root.update()
    env.root.event_generate("<Escape>")
    env.root.update()
    assert env.session.last_stop_reason is StopReason.ESCAPE
    assert toplevels(env) == []


def test_escape_in_an_effect_window_stops_everything(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    win = toplevels(env)[0]
    win.focus_force()  # key events are only delivered to the focused window
    env.root.update()
    win.event_generate("<Escape>")
    env.root.update()
    assert not env.session.active and toplevels(env) == []


def test_closing_main_window_stops_everything(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    env.close()
    assert env.session.last_stop_reason is StopReason.WINDOW_CLOSE
    assert env.runner.windows == set()


def test_deleting_package_file_stops_session(env, monkeypatch):
    path = make_pkg(env.tmp)
    env.open_package(path)
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.3)
    path.unlink()
    pump(env, 1.5)
    assert env.session.last_stop_reason is StopReason.FILE_DELETED
    assert toplevels(env) == []


def test_modifying_package_file_stops_session(env, monkeypatch):
    path = make_pkg(env.tmp)
    env.open_package(path)
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.3)
    with open(path, "ab") as handle:
        handle.write(b"x")
    pump(env, 1.5)
    assert env.session.last_stop_reason is StopReason.FILE_CHANGED
    assert toplevels(env) == []


def test_changed_file_requires_new_consent_before_start(env, monkeypatch):
    path = make_pkg(env.tmp)
    env.open_package(path)
    old_hash = env.package.file_sha256
    save_package(path, new_config("Anderes Paket"))  # same path, different content
    accept_consent(monkeypatch, env)
    assert env.request_start() is False
    assert env.package is None and not env.session.active
    assert env.errors
    assert file_sha256(path) != old_hash


def test_session_timeout_stops_everything(env, monkeypatch):
    cfg = new_config("Kurz")
    cfg["max_duration_seconds"] = 1
    cfg["actions"] = [dict(default_action("toast"), duration_seconds=1)]
    path = save_package(env.tmp / "kurz.mpk", cfg)
    env.open_package(path)
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 2.0)
    assert not env.session.active
    assert env.session.last_stop_reason in (StopReason.FINISHED, StopReason.TIMEOUT)
    assert toplevels(env) == []


def test_pause_and_resume_buttons(env, monkeypatch):
    env.open_package(make_pkg(env.tmp))
    accept_consent(monkeypatch, env)
    env.request_start()
    env.pause_button.invoke()
    before = env.session.remaining_in_action
    pump(env, 0.5)
    assert env.session.remaining_in_action == before
    env.resume_button.invoke()
    pump(env, 0.4)
    assert env.session.remaining_in_action < before
    env.stop_all()


def test_mute_switch_silences_audio(env, monkeypatch):
    cfg = new_config("Ton")
    cfg["actions"] = [default_action("sound_effect")]
    env.open_package(save_package(env.tmp / "ton.mpk", cfg))
    env.mute_var.set(True)
    env._toggle_mute()
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    assert env.audio.backend.played == []
    env.stop_all()


def test_image_resource_package_runs(env, monkeypatch):
    action = default_action("screamer")
    action["params"]["image"] = "resource:bild.png"
    cfg = new_config("Bild")
    cfg["actions"] = [action]
    env.open_package(save_package(env.tmp / "bild.mpk", cfg, {"bild.png": make_png(20, 20)}))
    accept_consent(monkeypatch, env)
    env.request_start()
    pump(env, 0.4)
    assert env.session.active
    env.stop_all()


def test_network_is_off_by_default_and_needs_package_and_consent(env, monkeypatch):
    assert env.server is None
    assert str(env.pair_button.cget("state")) == "disabled"
    env.open_package(make_pkg(env.tmp, network="none"))
    assert str(env.remote_button.cget("state")) == "disabled"
    env.start_pairing()
    assert env.server is None
    env.open_package(make_pkg(env.tmp, network="pairing", name="net.mpk"))
    assert str(env.remote_button.cget("state")) == "normal"
    env.start_pairing()  # no consent to remote control yet
    assert env.server is None


def run_creator(env, monkeypatch, approve=True):
    """Pair a real creator against the client over loopback TLS; returns (connection, pairing)."""
    env.open_package(make_pkg(env.tmp, network="pairing", name="net.mpk"))
    accept_consent(monkeypatch, env)
    monkeypatch.setattr(messagebox, "askyesno", lambda *a, **k: approve)
    ConsentDialog  # noqa
    env.request_remote()
    assert env.remote_allowed
    env.start_pairing()
    assert env.server and env.server.listening
    result = {}

    def work():
        conn = CreatorConnection("Toni", heartbeat_interval=60, timeout=10)
        try:
            conn.connect(env.pairing.uri)
            result["conn"] = conn
        except ConnectionFailed as exc:
            result["error"] = str(exc)

    thread = threading.Thread(target=work, daemon=True)
    thread.start()
    deadline = time.monotonic() + 8
    while thread.is_alive() and time.monotonic() < deadline:
        env.root.update()
        time.sleep(0.01)
    return result


def call_in_thread(env, fn):
    holder = {}
    thread = threading.Thread(target=lambda: holder.setdefault("v", _try(fn)), daemon=True)
    thread.start()
    deadline = time.monotonic() + 8
    while thread.is_alive() and time.monotonic() < deadline:
        env.root.update()
        time.sleep(0.01)
    return holder.get("v")


def _try(fn):
    try:
        return fn()
    except Exception as exc:
        return exc


def test_remote_pairing_effects_and_disconnect_stop(env, monkeypatch):
    result = run_creator(env, monkeypatch)
    conn = result["conn"]
    sha = env.package.file_sha256
    pump(env, 0.5)
    assert "VERBUNDEN" in env.indicator.cget("text")
    ack = call_in_thread(env, lambda: conn.start_effect(sha, 0, "toast"))
    assert ack.ok
    pump(env, 0.4)
    assert env.session.active and env.session.source == "remote" and toplevels(env)
    # an effect that does not belong to the opened package is refused
    time.sleep(1.1)
    assert not call_in_thread(env, lambda: conn.start_effect("f" * 64, 0, "toast")).ok
    time.sleep(1.1)
    assert not call_in_thread(env, lambda: conn.start_effect(sha, 0, "error_dialog")).ok  # wrong effect for that index
    time.sleep(1.1)
    assert not call_in_thread(env, lambda: conn.start_effect(sha, 9, "toast")).ok
    # creator disconnects -> remote effects end
    call_in_thread(env, conn.disconnect)
    pump(env, 0.5)
    assert not env.session.active
    assert env.session.last_stop_reason is StopReason.DISCONNECT
    assert toplevels(env) == []
    assert "VERBUNDEN" not in env.indicator.cget("text")


def test_receiver_declining_pairing_blocks_remote(env, monkeypatch):
    result = run_creator(env, monkeypatch, approve=False)
    assert "abgelehnt" in result["error"]
    assert env.session.active is False


def test_recipient_can_stop_and_disconnect_locally(env, monkeypatch):
    result = run_creator(env, monkeypatch)
    conn = result["conn"]
    sha = env.package.file_sha256
    assert call_in_thread(env, lambda: conn.start_effect(sha, 0, "toast")).ok
    pump(env, 0.3)
    env.stop_button.invoke()
    env.root.update()
    assert not env.session.active and toplevels(env) == []
    env.disconnect_network()
    pump(env, 0.5)
    assert not (env.server and env.server.paired)
    assert isinstance(call_in_thread(env, conn.ping), ConnectionFailed)


def test_changing_package_file_revokes_remote_start(env, monkeypatch):
    result = run_creator(env, monkeypatch)
    conn = result["conn"]
    sha = env.package.file_sha256
    with open(env.package.path, "ab") as handle:
        handle.write(b"x")
    ack = call_in_thread(env, lambda: conn.start_effect(sha, 0, "toast"))
    # refused outright, or the recipient revoked and closed the connection while answering
    assert isinstance(ack, ConnectionFailed) or not ack.ok
    pump(env, 0.5)
    assert not env.session.active
    assert not env.remote_allowed and not (env.server and env.server.paired)


def test_closing_app_closes_network_sockets(env, monkeypatch):
    result = run_creator(env, monkeypatch)
    server = env.server
    env.close()
    assert server.wait(3)
    assert not server.listening and not server.paired
