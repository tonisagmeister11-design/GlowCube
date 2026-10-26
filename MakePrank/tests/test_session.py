"""Session manager: virtual time, pause, stop reasons, integrity checks, cleanup."""
from makeprank.catalog import default_action
from makeprank.package_format import new_config, validate_config
from makeprank.session import SessionManager, SessionState, StopReason

from helpers import FakeHost, RecordingRunner, run_for


def make_config(kinds=("error_dialog", "toast"), duration=3, maximum=60):
    config = new_config("Sitzung")
    config["max_duration_seconds"] = maximum
    config["actions"] = []
    for kind in kinds:
        action = default_action(kind)
        action["duration_seconds"] = duration
        config["actions"].append(action)
    return validate_config(config)


def make_session(runner=None):
    host = FakeHost()
    runner = runner or RecordingRunner()
    return SessionManager(host, runner), host, runner


def test_runs_all_actions_then_finishes_and_cleans_up():
    session, _, runner = make_session()
    session.start(make_config())
    run_for(session, 10)
    assert runner.started == ["error_dialog", "toast"]
    assert sorted(runner.cleaned) == ["error_dialog", "toast"]
    assert session.state is SessionState.STOPPED
    assert session.last_stop_reason is StopReason.FINISHED
    assert session.pending_timers == 0


def test_stop_cleans_up_current_effect_and_cancels_timers():
    session, host, runner = make_session()
    session.start(make_config(duration=30))
    run_for(session, 1)
    assert runner.cleaned == []
    session.stop(StopReason.BUTTON)
    assert runner.cleaned == ["error_dialog"]
    assert session.pending_timers == 0 and not host.scheduled
    run_for(session, 100)
    assert runner.started == ["error_dialog"]  # nothing starts after stop


def test_stop_is_idempotent():
    session, _, runner = make_session()
    session.start(make_config())
    session.stop(StopReason.ESCAPE)
    session.stop(StopReason.BUTTON)
    assert session.last_stop_reason is StopReason.ESCAPE
    assert runner.cleaned == ["error_dialog"]


def test_pause_freezes_virtual_time():
    session, _, runner = make_session()
    session.start(make_config(duration=4))
    run_for(session, 1)
    remaining = session.remaining_in_action
    session.pause()
    assert session.state is SessionState.PAUSED
    for _ in range(200):
        session.tick(0.04)
    assert session.remaining_in_action == remaining
    assert runner.started == ["error_dialog"]
    session.resume()
    run_for(session, 10)
    assert runner.started == ["error_dialog", "toast"]


def test_max_duration_timeout_stops_everything():
    config = make_config(kinds=("error_dialog",) * 3, duration=10, maximum=30)
    config["max_duration_seconds"] = 30
    session, _, runner = make_session()
    session.start(config)
    run_for(session, 31)
    assert session.last_stop_reason in (StopReason.FINISHED, StopReason.TIMEOUT)
    assert session.state is SessionState.STOPPED


def test_timeout_when_budget_is_smaller_than_effect_is_not_possible_but_cleanup_holds():
    config = make_config(kinds=("error_dialog",), duration=20, maximum=20)
    session, _, runner = make_session()
    session.start(config)
    run_for(session, 40)
    assert session.last_stop_reason is StopReason.FINISHED  # exactly the whole budget still ends normally
    assert runner.cleaned == ["error_dialog"]


def test_integrity_check_stops_session():
    session, _, runner = make_session()
    state = {"reason": None}
    session.integrity_check = lambda: state["reason"]
    session.start(make_config(duration=30))
    run_for(session, 2)
    assert session.active
    state["reason"] = StopReason.FILE_DELETED
    run_for(session, 2)
    assert session.last_stop_reason is StopReason.FILE_DELETED
    assert runner.cleaned == ["error_dialog"]


def test_effect_start_error_stops_session_with_error():
    session, _, runner = make_session(RecordingRunner(fail_on=1))
    session.start(make_config(duration=2))
    run_for(session, 5)
    assert session.last_stop_reason is StopReason.ERROR
    assert runner.cleaned == ["error_dialog"]


def test_new_start_replaces_running_session():
    session, _, runner = make_session()
    session.start(make_config(duration=30))
    session.start(make_config(kinds=("toast",), duration=30))
    assert runner.cleaned == ["error_dialog"]
    assert session.active
    session.stop(StopReason.GLOBAL_STOP)
    assert runner.cleaned == ["error_dialog", "toast"]


def test_cleanup_failure_does_not_block_other_cleanups():
    class BadRunner(RecordingRunner):
        def start(self, scope, action):
            super().start(scope, action)
            scope.add_cleanup(lambda: (_ for _ in ()).throw(RuntimeError("kaputt")))

    session, _, runner = make_session(BadRunner())
    session.start(make_config(duration=30))
    session.stop(StopReason.BUTTON)
    assert runner.cleaned == ["error_dialog"]
    assert session.state is SessionState.STOPPED


def test_session_log_records_start_effects_and_stop():
    session, _, _ = make_session()
    session.start(make_config(duration=2))
    run_for(session, 10)
    kinds = [event["kind"] for event in session.log.events]
    assert kinds[0] == "session_start" and kinds[-1] == "session_stop"
    assert kinds.count("effect_start") == 2 and kinds.count("effect_stop") == 2
