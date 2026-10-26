import os
import subprocess
import sys
import time
from pathlib import Path

import pytest

from makeprank.catalog import EFFECTS
from makeprank.package_format import load_package_full

ROOT = Path(__file__).resolve().parents[1]


def test_example_packages_are_valid_and_current():
    welcome = load_package_full(ROOT / "examples" / "WelcomeDemo.mpk")
    assert welcome.config["format_version"] == 2 and welcome.config["network"] == "pairing"
    everything = load_package_full(ROOT / "examples" / "AlleEffekte.mpk")
    assert [a["type"] for a in everything.config["actions"]] == list(EFFECTS)
    assert everything.config["audio"] is True and "schachbrett.png" in everything.resources


@pytest.mark.parametrize("module", ["makeprank.creator_app", "makeprank.client_app"])
def test_entrypoint_starts_and_stays_up(module, tmp_path):
    """Starts the real program (needs a display); it must keep running until we stop it."""
    if sys.platform != "win32" and not os.environ.get("DISPLAY"):
        pytest.skip("keine Anzeige (unter xvfb-run ausführen)")
    env = dict(os.environ, MAKEPRANK_HOME=str(tmp_path), PYTHONPATH=str(ROOT))
    proc = subprocess.Popen([sys.executable, "-m", module], env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        time.sleep(2.5)
        assert proc.poll() is None, proc.stdout.read().decode("utf-8", "replace")
    finally:
        proc.terminate()
        try:
            proc.wait(5)
        except subprocess.TimeoutExpired:
            proc.kill()


def test_client_start_with_package_argument_does_not_start_effects(tmp_path):
    if sys.platform != "win32" and not os.environ.get("DISPLAY"):
        pytest.skip("keine Anzeige")
    env = dict(os.environ, MAKEPRANK_HOME=str(tmp_path), PYTHONPATH=str(ROOT))
    proc = subprocess.Popen([sys.executable, "-m", "makeprank.client_app", str(ROOT / "examples" / "AlleEffekte.mpk")],
                            env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    try:
        time.sleep(2.5)
        assert proc.poll() is None
        assert not (tmp_path / "consents.json").exists()  # nothing accepted, so nothing was started
    finally:
        proc.terminate()
        proc.wait(5)
