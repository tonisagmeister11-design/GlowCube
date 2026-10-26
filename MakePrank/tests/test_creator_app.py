"""Creator UI logic: catalog, editor, preview, export, pairing/control against a real receiver."""
import time
import tkinter as tk
from tkinter import messagebox

import pytest

from makeprank.audio import NullBackend
from makeprank.catalog import EFFECTS
from makeprank.creator_app import CreatorApp
from makeprank.package_format import load_package_full
from makeprank.receiver_server import ReceiverServer
from makeprank.session import StopReason
from makeprank.settings import Settings

from helpers import make_png
from test_network import Handler, SHA


@pytest.fixture
def app(tmp_path, monkeypatch):
    try:
        root = tk.Tk()
    except tk.TclError as exc:
        pytest.skip(f"keine Anzeige: {exc}")
    root.withdraw()
    shown = []
    monkeypatch.setattr(messagebox, "showerror", lambda *a, **k: shown.append(("error", a)))
    monkeypatch.setattr(messagebox, "showwarning", lambda *a, **k: shown.append(("warning", a)))
    monkeypatch.setattr(messagebox, "showinfo", lambda *a, **k: shown.append(("info", a)))
    monkeypatch.setattr(messagebox, "askokcancel", lambda *a, **k: True)
    creator = CreatorApp(root, settings=Settings(creator_name="Toni"), audio_backend=NullBackend())
    creator.shown = shown
    creator.tmp = tmp_path
    yield creator
    try:
        creator.shutdown()
        root.destroy()
    except tk.TclError:
        pass


def toplevels(app):
    return [w for w in app.root.winfo_children() if isinstance(w, tk.Toplevel)]


def pump(app, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        app.root.update()
        time.sleep(0.01)


def test_all_pages_exist(app):
    titles = [app.notebook.tab(i, "text") for i in range(app.notebook.index("end"))]
    assert titles == ["Dashboard", "Effektkatalog", "Paket-Editor", "Vorschau", "Export", "Clients/Pairing",
                      "Sitzungsprotokoll", "Einstellungen", "Hilfe"]


def test_catalog_lists_every_effect_and_filters(app):
    assert set(app.catalog_tree.get_children()) == set(EFFECTS)
    app.search_var.set("toast")
    assert list(app.catalog_tree.get_children()) == ["toast"]
    app.search_var.set("")
    app.category_var.set("Dialoge")
    assert set(app.catalog_tree.get_children()) == {"error_dialog", "msgbox"}
    app.category_var.set("Alle")
    app.catalog_tree.selection_set("screamer")
    app.root.update()
    text = app.effect_details.get("1.0", "end")
    assert "Risiko" in text and "Bild" in text


def test_add_edit_move_remove_actions(app):
    app.add_selected_effect("toast")
    app.add_selected_effect("msgbox")
    assert [a["type"] for a in app.config["actions"]] == ["error_dialog", "toast", "msgbox"]
    app.title_var.set("Neuer Titel")
    app.dur_var.set("9")
    app.param_vars["variant"].set("retry_cancel")
    assert app.apply_action()
    assert app.config["actions"][2]["params"]["variant"] == "retry_cancel"
    assert app.config["actions"][2]["duration_seconds"] == 9
    app.move_action(-1)
    assert [a["type"] for a in app.config["actions"]] == ["error_dialog", "msgbox", "toast"]
    app.remove_action()
    assert [a["type"] for a in app.config["actions"]] == ["error_dialog", "toast"]


def test_invalid_input_is_rejected_with_message(app):
    app.dur_var.set("999")
    assert app.apply_action() is False
    app.dur_var.set("abc")
    assert app.apply_action() is False
    assert [kind for kind, _ in app.shown].count("error") == 2


def test_last_action_cannot_be_removed(app):
    app.remove_action()
    assert len(app.config["actions"]) == 1


def test_every_effect_can_be_added_exported_and_reloaded(app):
    app.new_project()
    app.config["actions"] = []
    for kind in EFFECTS:
        app.config["actions"].append(__import__("makeprank.catalog", fromlist=["x"]).default_action(kind))
    app.max_var.set("1800")
    for a in app.config["actions"]:
        a["duration_seconds"] = 3
    dest = app.export(str(app.tmp / "alle.mpk"))
    assert dest and dest.is_file()
    loaded = load_package_full(dest)
    assert [a["type"] for a in loaded.config["actions"]] == list(EFFECTS)
    assert loaded.config["audio"] is True


def test_export_refuses_invalid_package(app):
    app.max_var.set("0")
    assert app.export(str(app.tmp / "kaputt.mpk")) is None
    assert not (app.tmp / "kaputt.mpk").exists()
    assert any(kind == "error" for kind, _ in app.shown)


def test_export_records_hash_and_summary_matches(app):
    dest = app.export(str(app.tmp / "ok.mpk"))
    from makeprank.package_format import file_sha256
    assert app.exported_file_sha == file_sha256(dest)
    app.notebook.select(4)
    app.root.update()
    text = app.export_text.get("1.0", "end")
    assert "Geschätzte Dauer" in text and "Audio:" in text and "Fehlerdialog" in text


def test_network_flag_is_exported(app):
    app.network_var.set(True)
    dest = app.export(str(app.tmp / "net.mpk"))
    assert load_package_full(dest).config["network"] == "pairing"


def test_open_project_round_trip_with_image(app):
    png = app.tmp / "mein bild.png"
    png.write_bytes(make_png(24, 24))
    ref = app.import_image(None, str(png))
    assert ref == "resource:mein_bild.png"
    app.add_selected_effect("screamer")
    app.param_vars["image"].set(ref)
    assert app.apply_action()
    dest = app.export(str(app.tmp / "bild.mpk"))
    app.new_project()
    assert app.resources == {}
    assert app.open_project(str(dest))
    assert "mein_bild.png" in app.resources
    assert app.config["actions"][-1]["params"]["image"] == ref


def test_bad_images_are_refused(app):
    exe = app.tmp / "virus.png"
    exe.write_bytes(b"MZ\x90\x00" + b"\0" * 100)
    assert app.import_image(None, str(exe)) is None
    big = app.tmp / "gross.png"
    big.write_bytes(make_png(4000, 4000))
    assert app.import_image(None, str(big)) is None
    assert app.resources == {}


def only(app, kind):
    """Make ``kind`` the single action of the package."""
    from makeprank.catalog import default_action
    app.config["actions"] = [default_action(kind)]
    app._fill_actions()
    app._select_action(0)


def test_preview_runs_stops_and_is_silent_by_default(app):
    only(app, "sound_effect")
    assert app.start_preview()
    pump(app, 0.4)
    assert app.session.active and app.session.source == "preview"
    assert app.audio.backend.played == []  # muted unless the box is ticked
    app.stop_everything()
    app.root.update()
    assert not app.session.active and toplevels(app) == []


def test_preview_plays_sound_only_when_enabled(app):
    only(app, "sound_effect")
    app.preview_audio_var.set(True)
    app.audio.muted = False
    app.start_preview()
    pump(app, 0.3)
    assert len(app.audio.backend.played) == 1
    app.stop_everything()


def test_example_preview_from_catalog_and_escape(app):
    app.catalog_tree.selection_set("fake_shutdown")
    app.preview_selected_effect()
    pump(app, 0.3)
    assert toplevels(app)
    win = toplevels(app)[0]
    win.focus_force()
    app.root.update()
    win.event_generate("<Escape>")
    app.root.update()
    assert toplevels(app) == [] and app.session.last_stop_reason is StopReason.GLOBAL_STOP


def test_settings_validation(app):
    app.set_name.set("<script>")
    app.save_settings()
    assert app.settings.creator_name == "Toni"
    app.set_name.set("Neuer Name")
    app.save_settings.__func__  # exists
    app.settings.save = lambda *a, **k: None
    app.save_settings()
    assert app.settings.creator_name == "Neuer Name"


def test_invalid_pairing_links_are_not_followed(app):
    for link in ("", "http://evil.example/", "makeprank://pair?host=8.8.8.8&port=1&fp=" + "a" * 64 + "&code=ABCD-EFGH"):
        app.link_var.set(link)
        app.connect_from_link()
        assert not app.connections
        assert "Ungültig" in app.client_msg.cget("text")


class AnyPackageHandler(Handler):
    def on_start_effect(self, sha, index, effect_id):
        self.started.append((sha, index, effect_id))
        return True, "ok"


def pair_with_receiver(app, handler=None, **server_kw):
    handler = handler or AnyPackageHandler()
    server = ReceiverServer(handler, **server_kw)
    info = server.start_pairing()
    app.link_var.set(info.uri)
    app.connect_from_link()
    deadline = time.monotonic() + 8
    while not app.connections and time.monotonic() < deadline:
        pump(app, 0.05)
    return server, handler, info


def test_end_to_end_export_pair_send_effect_stop_and_block(app):
    app.add_selected_effect("toast")
    dest = app.export(str(app.tmp / "e2e.mpk"))
    sha = app.exported_file_sha
    server, handler, info = pair_with_receiver(app, effect_min_interval=0.0)
    try:
        assert len(app.connections) == 1
        pump(app, 0.2)
        assert app.client_tree.get_children()
        app.selected = 1
        assert app.send_selected_effect()
        assert handler.started[-1] == (sha, 1, "toast")
        app.send_stop()
        assert handler.stopped == 1
        # global stop also tells every managed session to stop
        app.stop_everything()
        assert handler.stopped == 2
        # editing the package after export blocks sending until it is exported again
        app.title_var.set("Geändert")
        app.apply_action()
        assert app.send_selected_effect() is False
        assert "verändert" in app.client_msg.cget("text")
        conn = next(iter(app.connections.values()))
        app.block_selected()
        assert not conn.connected
        assert handler.event.wait(3)
    finally:
        server.stop_pairing()
    # a blocked receiver is refused on the next attempt (before any network traffic)
    server2 = ReceiverServer(AnyPackageHandler())
    info2 = server2.start_pairing()
    try:
        app.link_var.set(info2.uri)
        before = len(app.connections)
        app.connect_from_link()
        assert "gesperrt" in app.client_msg.cget("text")
        assert len(app.connections) == before
    finally:
        server2.stop_pairing()


def test_sending_requires_export_first(app):
    server, handler, info = pair_with_receiver(app)
    try:
        app.selected = 0
        assert app.send_selected_effect() is False
        assert handler.started == []
    finally:
        server.stop_pairing()


def test_log_page_records_events(app):
    app.start_preview()
    pump(app, 0.2)
    app.stop_everything()
    pump(app, 0.2)
    text = app.log_text.get("1.0", "end")
    assert "session_start" in text and "session_stop" in text
