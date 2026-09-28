#!/usr/bin/env bash
# Three players on three different networks: host behind router A, friend 1 behind B, friend 2 behind C.
#   Tools/Net/run_net_nat3.sh <A> <B> <C> <expect B> <expect C> [outdir]
set -u
cd "$(dirname "$0")/../.."
OUT=${6:-/tmp/hh_net_nat3}
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
MQTT_PY=${MQTT_PY:-python3}
rm -rf "$OUT"; mkdir -p "$OUT/sv_h" "$OUT/sv_b" "$OUT/sv_c"
python3 Tools/Net/natsim.py --router "127.0.1.:127.0.101.1:$1" --router "127.0.2.:127.0.102.1:$2" --router "127.0.3.:127.0.103.1:$3" \
  --stun 127.0.50.1:3478 --stun 127.0.50.2:3478 --delay 20 --jitter 5 > "$OUT/natsim.log" 2>&1 &
NS=$!
$MQTT_PY Tools/Net/mqtt_broker.py --ws 127.0.0.1:18083 > "$OUT/broker.log" 2>&1 &
BR=$!
sleep 1.5
COMMON="HH_NET_DEBUG=1 HH_NO_UPNP=1 HH_NO_LAN=1 HH_STUN=127.0.50.1:3478,127.0.50.2:3478 HH_MQTT=ws://127.0.0.1:18083/mqtt HH_TEST_PUBLIC=127.0.10"
env $COMMON HH_BIND_IP=127.0.1.2 HH_SAVES_DIR="$OUT/sv_h" timeout 400 $GODOT --headless --path Game res://tests/net_nat_test.tscn -- --role host --dir "$OUT" --n 3 > "$OUT/host.log" 2>&1 &
H=$!
sleep 1
env $COMMON HH_BIND_IP=127.0.2.2 HH_SAVES_DIR="$OUT/sv_b" timeout 400 $GODOT --headless --path Game res://tests/net_nat_test.tscn -- --role client --dir "$OUT" --n 3 --expect "$4" > "$OUT/b.log" 2>&1 &
B=$!
env $COMMON HH_BIND_IP=127.0.3.2 HH_SAVES_DIR="$OUT/sv_c" timeout 400 $GODOT --headless --path Game res://tests/net_nat_test.tscn -- --role client --dir "$OUT" --n 3 --expect "$5" > "$OUT/c.log" 2>&1
wait $H $B
kill $NS $BR 2>/dev/null
sleep 0.5
for f in host b c; do grep -hE "^\[(host|client)\]|SCRIPT ERROR" "$OUT/$f.log" | sed "s/^/$f: /"; done
tail -1 "$OUT/natsim.log"
