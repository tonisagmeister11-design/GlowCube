"""Build smoke test: start an executable, make sure it stays alive for a few seconds, then stop it.

Usage: python tools/smoke_test.py <path-to-exe> [seconds]
Exit code 0 = the program started and kept running; 1 = it crashed/exited early.
"""
from __future__ import annotations

import subprocess
import sys
import time


def main() -> int:
    if len(sys.argv) < 2:
        print("Aufruf: smoke_test.py <exe> [sekunden]")
        return 2
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 4.0
    proc = subprocess.Popen([sys.argv[1]], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if proc.poll() is not None:
            output = proc.stdout.read().decode("utf-8", "replace") if proc.stdout else ""
            print(f"FEHLER: {sys.argv[1]} wurde nach weniger als {seconds} s beendet (Code {proc.returncode}).\n{output}")
            return 1
        time.sleep(0.2)
    proc.terminate()
    try:
        proc.wait(5)
    except subprocess.TimeoutExpired:
        proc.kill()
    print(f"OK: {sys.argv[1]} lief {seconds} s ohne Absturz.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
