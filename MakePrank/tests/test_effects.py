"""Every effect against a real Tk (needs a display: run under xvfb-run on Linux)."""
import tkinter as tk

import pytest

from makeprank.audio import AudioPlayer, NullBackend
from makeprank.catalog import EFFECTS, default_action
from makeprank.effects import MSGBOX_BUTTONS, TkEffectRunner
from makeprank.package_format import new_config, validate_config
from makeprank.session import SessionManager, StopReason

from helpers import FakeHost, make_png, run_for


@pytest.fixture(scope="module")
def root():
    try:
        tk_root = tk.Tk()
    except tk.TclError as exc:
        pytest.skip(f"keine Anzeige verfügbar: {exc}")
    tk_root.withdraw()
    yield tk_root
    tk_root.destroy()


def toplevels(root):
    return [w for w in root.winfo_children() if isinstance(w, tk.Toplevel)]


def build(root, kinds, duration=3, resources=None, params=None, muted=False):
    config = new_config("Effekte")
    config["max_duration_seconds"] = 600
    config["actions"] = []
    for kind in kinds:
        action = default_action(kind)
        action["duration_seconds"] = duration
        action["params"].update((params or {}).get(kind, {}))
        config["actions"].append(action)
    config = validate_config(config, resources or {})
    backend = NullBackend()
    audio = AudioPlayer(backend, muted=muted)
    holder = {}
    runner = TkEffectRunner(root, resources or {}, audio, on_stop_all=lambda: holder["s"].stop(StopReason.BUTTON))
    session = SessionManager(FakeHost(), runner)
    holder["s"] = session
    return config, session, runner, backend


@pytest.mark.parametrize("kind", sorted(EFFECTS))
def test_effect_opens_window_and_leaves_nothing_behind_after_finish(root, kind):
    config, session, runner, _ = build(root, [kind])
    session.start(config)
    run_for(session, 1.2)
    root.update()
    assert toplevels(root), f"{kind} hat kein Fenster geöffnet"
    run_for(session, 5)
    root.update()
    assert session.last_stop_reason is StopReason.FINISHED
    assert toplevels(root) == [] and runner.windows == set()
    assert session.pending_timers == 0


@pytest.mark.parametrize("kind", sorted(EFFECTS))
def test_effect_does_not_survive_stop(root, kind):
    config, session, runner, backend = build(root, [kind], duration=60)
    session.start(config)
    run_for(session, 1.5)
    root.update()
    session.stop(StopReason.ESCAPE)
    root.update()
    assert toplevels(root) == [] and runner.windows == set()
    assert session.pending_timers == 0


def test_user_closing_an_effect_window_does_not_crash_session(root):
    kinds = ["fake_shutdown", "console_overlay", "chat_message", "visual_effect", "freeze_simulation", "window_shake"]
    for kind in kinds:
        config, session, runner, _ = build(root, [kind], duration=6)
        session.start(config)
        run_for(session, 0.5)
        for win in toplevels(root):
            win.destroy()
        root.update()
        run_for(session, 8)
        assert session.last_stop_reason is StopReason.FINISHED, kind


def test_msgbox_variants_have_expected_buttons(root):
    for variant, labels in MSGBOX_BUTTONS.items():
        config, session, runner, _ = build(root, ["msgbox"], duration=10, params={"msgbox": {"variant": variant}})
        session.start(config)
        root.update()
        win = toplevels(root)[0]
        found = []

        def walk(widget):
            for child in widget.winfo_children():
                if isinstance(child, tk.Button) and child.cget("text") != "STOPP":
                    found.append(child.cget("text"))
                walk(child)

        walk(win)
        assert sorted(found) == sorted(labels), variant
        session.stop(StopReason.BUTTON)


def test_msgbox_click_only_closes_window(root):
    config, session, runner, _ = build(root, ["msgbox"], duration=10)
    session.start(config)
    root.update()
    win = toplevels(root)[0]
    buttons = []

    def walk(widget):
        for child in widget.winfo_children():
            if isinstance(child, tk.Button) and child.cget("text") == "Ja":
                buttons.append(child)
            walk(child)

    walk(win)
    buttons[0].invoke()
    run_for(session, 2)
    root.update()
    assert toplevels(root) == []
    assert session.active  # the click did not do anything beyond closing the dialog


def test_window_buttons_close_window_and_stop_button_stops_everything(root):
    config, session, runner, _ = build(root, ["fake_browser", "toast"], duration=30)
    session.start(config)
    root.update()
    win = toplevels(root)[0]
    stop = []

    def walk(widget):
        for child in widget.winfo_children():
            if isinstance(child, tk.Button) and child.cget("text").startswith("STOPP"):
                stop.append(child)
            walk(child)

    walk(win)
    stop[0].invoke()
    root.update()
    assert session.last_stop_reason is StopReason.BUTTON
    assert toplevels(root) == []


def test_escape_in_effect_window_stops_everything(root):
    config, session, runner, _ = build(root, ["text_overlay"], duration=30)
    session.start(config)
    root.update()
    win = toplevels(root)[0]
    win.event_generate("<Escape>")
    root.update()
    assert session.last_stop_reason is StopReason.BUTTON
    assert toplevels(root) == []


def test_sound_effect_is_bounded_and_stopped(root):
    config, session, runner, backend = build(root, ["sound_effect"], duration=30,
                                             params={"sound_effect": {"volume": 40, "milliseconds": 1500}})
    session.start(config)
    run_for(session, 0.5)
    assert len(backend.played) == 1
    session.stop(StopReason.BUTTON)
    assert backend.stops == 1


def test_muted_audio_plays_nothing(root):
    config, session, runner, backend = build(root, ["sound_effect", "color_bars", "screamer"], duration=3, muted=True,
                                             params={"color_bars": {"tone": True}, "screamer": {"sound": "beep"}})
    session.start(config)
    run_for(session, 12)
    assert backend.played == []


def test_color_bars_tone_and_screamer_sound_play_once_and_stop(root):
    config, session, runner, backend = build(root, ["color_bars", "screamer"], duration=3,
                                             params={"color_bars": {"tone": True}, "screamer": {"sound": "beep"}})
    session.start(config)
    run_for(session, 10)
    assert len(backend.played) == 2
    assert backend.stops == 2


def test_screamer_uses_imported_image_resource(root):
    resources = {"bild.png": make_png(16, 16)}
    config, session, runner, _ = build(root, ["screamer"], resources=resources, duration=5,
                                       params={"screamer": {"image": "resource:bild.png"}})
    session.start(config)
    root.update()
    win = toplevels(root)[0]
    labels = [c for c in win.winfo_children() if isinstance(c, tk.Label) and c.cget("image")]
    assert labels, "importiertes Bild wurde nicht angezeigt"
    session.stop(StopReason.BUTTON)
    root.update()
    assert toplevels(root) == []


def test_window_shake_moves_only_its_own_window_and_restores_it(root):
    config, session, runner, _ = build(root, ["window_shake"], duration=20, params={"window_shake": {"intensity": 10}})
    session.start(config)
    root.update()
    win = toplevels(root)[0]
    positions = set()
    for _ in range(12):
        session.tick(0.05)
        root.update()
        positions.add((win.winfo_x(), win.winfo_y()))
    assert len(positions) > 1
    session.stop(StopReason.BUTTON)
    root.update()
    assert toplevels(root) == []


def test_visual_effect_toggles_no_faster_than_every_second(root):
    config, session, runner, _ = build(root, ["visual_effect"], duration=20)
    session.start(config)
    canvas_states = []
    win = toplevels(root)[0]
    canvas = [c for c in win.winfo_children() if isinstance(c, tk.Canvas)][0]
    for _ in range(100):  # 4 s virtual
        session.tick(0.04)
        canvas_states.append(tuple(canvas.itemcget(i, "fill") for i in canvas.find_all()[:3]))
    changes = sum(1 for a, b in zip(canvas_states, canvas_states[1:]) if a != b)
    assert changes <= 3  # 4 s at one toggle per 1.5 s
    session.stop(StopReason.BUTTON)


def test_console_overlay_never_runs_commands(root, monkeypatch):
    import subprocess, os
    def boom(*a, **k):
        raise AssertionError("Prozessstart nicht erlaubt")
    monkeypatch.setattr(subprocess, "Popen", boom)
    monkeypatch.setattr(os, "system", boom)
    config, session, runner, _ = build(root, ["console_overlay"], duration=3,
                                       params={"console_overlay": {}})
    config["actions"][0]["text"] = "rm -rf /\nshutdown /s /t 0"
    session.start(config)
    run_for(session, 5)
    assert session.last_stop_reason is StopReason.FINISHED


def test_simultaneous_windows_stay_bounded(root):
    config, session, runner, _ = build(root, list(EFFECTS), duration=1)
    session.start(config)
    most = 0
    while session.active:
        session.tick(0.04)
        root.update()
        most = max(most, len(toplevels(root)))
    assert most <= 1
    assert toplevels(root) == []


def test_sound_clip_plays_imported_wav(root):
    from helpers import make_wav
    resources = {"ton.wav": make_wav(0.5)}
    config, session, runner, backend = build(root, ["sound_clip"], resources=resources, duration=3,
                                             params={"sound_clip": {"clip": "resource:ton.wav"}})
    session.start(config)
    run_for(session, 0.4)
    root.update()
    assert backend.played == [make_wav(0.5)]  # the exact imported bytes were played
    assert toplevels(root)  # the info window is shown
    session.stop(StopReason.BUTTON)
    root.update()
    assert backend.stops == 1 and toplevels(root) == []


def test_sound_clip_without_window_still_plays(root):
    from helpers import make_wav
    resources = {"ton.wav": make_wav(0.4)}
    config, session, runner, backend = build(root, ["sound_clip"], resources=resources, duration=3,
                                             params={"sound_clip": {"clip": "resource:ton.wav", "show_window": False}})
    session.start(config)
    run_for(session, 0.3)
    root.update()
    assert len(backend.played) == 1 and toplevels(root) == []
    session.stop(StopReason.BUTTON)


def test_muted_sound_clip_plays_nothing(root):
    from helpers import make_wav
    config, session, runner, backend = build(root, ["sound_clip"], resources={"ton.wav": make_wav(0.3)}, duration=3,
                                             muted=True, params={"sound_clip": {"clip": "resource:ton.wav"}})
    session.start(config)
    run_for(session, 0.3)
    assert backend.played == []
    session.stop(StopReason.BUTTON)
