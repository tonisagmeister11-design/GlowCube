#!/usr/bin/env bash
# Creative, drops and gifts test: host + client over the network simulator.
#   Tools/Net/run_net_full.sh [delay_ms] [jitter_ms] [loss_percent] [outdir]
set -u
cd "$(dirname "$0")/../.."
D=${1:-25}; J=${2:-10}; L=${3:-2}; OUT=${4:-/tmp/hh_net_gift}
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
rm -rf "$OUT"; mkdir -p "$OUT/sv_h" "$OUT/sv_c"
python3 Tools/Net/netsim.py --listen 7790 --target 127.0.0.1:7777 --delay "$D" --jitter "$J" --loss "$L" > "$OUT/netsim.log" 2>&1 &
NS=$!
HH_NET_DEBUG=1 HH_SAVES_DIR="$OUT/sv_h" HH_NO_UPNP=1 timeout 700 $GODOT --headless --path Game res://tests/net_gift_test.tscn -- --role host --file "$OUT/code.txt" --dir "$OUT" > "$OUT/host.log" 2>&1 &
H=$!
sleep 2
HH_NET_DEBUG=1 HH_JOIN_ADDR=127.0.0.1:7790 HH_SAVES_DIR="$OUT/sv_c" HH_NO_UPNP=1 timeout 700 $GODOT --headless --path Game res://tests/net_gift_test.tscn -- --role client --file "$OUT/code.txt" --dir "$OUT" > "$OUT/client.log" 2>&1
wait $H
kill $NS 2>/dev/null; sleep 0.5
grep -hE "^\[(host|client)\]|SCRIPT ERROR" "$OUT/host.log" "$OUT/client.log"
tail -1 "$OUT/netsim.log"
