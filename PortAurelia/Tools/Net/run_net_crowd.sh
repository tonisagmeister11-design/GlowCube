#!/usr/bin/env bash
# Crowd test: host + (N-1) clients through the network simulator.
#   Tools/Net/run_net_crowd.sh [N] [outdir]
set -u
cd "$(dirname "$0")/../.."
N=${1:-5}; OUT=${2:-/tmp/hh_net_crowd}
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
rm -rf "$OUT"; mkdir -p "$OUT"
python3 Tools/Net/netsim.py --listen 7790 --target 127.0.0.1:7777 --delay 30 --jitter 12 --loss 2 > "$OUT/netsim.log" 2>&1 &
NS=$!
mkdir -p "$OUT/sv_host"
HH_SAVES_DIR="$OUT/sv_host" HH_NO_UPNP=1 timeout 500 $GODOT --headless --path Game res://tests/net_crowd_test.tscn -- --role host --n "$N" --file "$OUT/code.txt" > "$OUT/host.log" 2>&1 &
PIDS="$!"
sleep 2
for i in $(seq 2 "$N"); do
  mkdir -p "$OUT/sv_$i"
  HH_JOIN_ADDR=127.0.0.1:7790 HH_SAVES_DIR="$OUT/sv_$i" HH_NO_UPNP=1 timeout 500 $GODOT --headless --path Game res://tests/net_crowd_test.tscn -- --role client --name "P$i" --n "$N" --file "$OUT/code.txt" > "$OUT/p$i.log" 2>&1 &
  PIDS="$PIDS $!"
  sleep 1
done
for p in $PIDS; do wait $p; done
kill $NS 2>/dev/null; sleep 0.5
grep -hE "^\[|SCRIPT ERROR" "$OUT"/host.log "$OUT"/p*.log | grep -vE "^\[(export|ALSA)"
tail -1 "$OUT/netsim.log"
