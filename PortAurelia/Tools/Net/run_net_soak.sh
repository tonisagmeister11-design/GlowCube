#!/usr/bin/env bash
# Long multiplayer session over the network simulator.
#   Tools/Net/run_net_soak.sh [seconds] [outdir]
set -u
cd "$(dirname "$0")/../.."
SECS=${1:-480}; OUT=${2:-/tmp/hh_net_soak}
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
rm -rf "$OUT"; mkdir -p "$OUT/sv_h" "$OUT/sv_c"
python3 Tools/Net/netsim.py --listen 7790 --target 127.0.0.1:7777 --delay 30 --jitter 12 --loss 2 > "$OUT/netsim.log" 2>&1 &
NS=$!
HH_SAVES_DIR="$OUT/sv_h" HH_NO_UPNP=1 timeout $((SECS + 600)) $GODOT --headless --path Game res://tests/net_soak_test.tscn -- --role host --file "$OUT/code.txt" --secs "$SECS" > "$OUT/host.log" 2>&1 &
H=$!
sleep 2
HH_JOIN_ADDR=127.0.0.1:7790 HH_SAVES_DIR="$OUT/sv_c" HH_NO_UPNP=1 timeout $((SECS + 600)) $GODOT --headless --path Game res://tests/net_soak_test.tscn -- --role client --file "$OUT/code.txt" --secs "$SECS" > "$OUT/client.log" 2>&1
wait $H
kill $NS 2>/dev/null; sleep 0.5
grep -hE "^\[(host|client)\]" "$OUT/host.log" "$OUT/client.log"
echo "script errors: host $(grep -c 'SCRIPT ERROR' "$OUT/host.log"), client $(grep -c 'SCRIPT ERROR' "$OUT/client.log")"
grep -h "SCRIPT ERROR" -A2 "$OUT/host.log" "$OUT/client.log" | sort | uniq -c | sort -rn | head -10
tail -1 "$OUT/netsim.log"
