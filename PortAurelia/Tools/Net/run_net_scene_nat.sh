#!/usr/bin/env bash
# Any two-player test scene between two simulated home networks (natsim + local MQTT broker),
# i.e. with the real internet connection logic (meeting point, hole punching, relay).
#   Tools/Net/run_net_scene_nat.sh <scene> <host_router> <friend_router> [outdir]
# e.g. Tools/Net/run_net_scene_nat.sh net_full_test portrestricted symmetric   (missions over the relay)
set -u
cd "$(dirname "$0")/../.."
SCENE=$1; HT=$2; CT=$3; OUT=${4:-/tmp/hh_net_scene_nat}
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
MQTT_PY=${MQTT_PY:-python3}
rm -rf "$OUT"; mkdir -p "$OUT/sv_h" "$OUT/sv_c"
python3 Tools/Net/natsim.py --router "127.0.1.:127.0.101.1:$HT" --router "127.0.2.:127.0.102.1:$CT" \
  --stun 127.0.50.1:3478 --stun 127.0.50.2:3478 --delay "${NAT_DELAY:-20}" --jitter 6 --loss "${NAT_LOSS:-1}" > "$OUT/natsim.log" 2>&1 &
NS=$!
$MQTT_PY Tools/Net/mqtt_broker.py --ws 127.0.0.1:18083 > "$OUT/broker.log" 2>&1 &
BR=$!
sleep 1.5
COMMON="HH_NET_DEBUG=1 HH_NO_UPNP=1 HH_NO_LAN=1 HH_STUN=127.0.50.1:3478,127.0.50.2:3478 HH_MQTT=ws://127.0.0.1:18083/mqtt,ws://127.0.0.1:18099/mqtt HH_TEST_PUBLIC=127.0.10 ${GAME_ENV:-}"
env $COMMON HH_BIND_IP=127.0.1.2 HH_SAVES_DIR="$OUT/sv_h" timeout 900 $GODOT --headless --path Game res://tests/$SCENE.tscn -- --role host --file "$OUT/code.txt" --dir "$OUT" ${EXTRA:-} > "$OUT/host.log" 2>&1 &
H=$!
sleep 1
env $COMMON HH_BIND_IP=127.0.2.2 HH_SAVES_DIR="$OUT/sv_c" timeout 900 $GODOT --headless --path Game res://tests/$SCENE.tscn -- --role client --file "$OUT/code.txt" --dir "$OUT" ${EXTRA:-} > "$OUT/client.log" 2>&1
wait $H
kill $NS $BR 2>/dev/null; sleep 0.5
grep -hE "^\[(host|client)\] (FAIL|===)|SCRIPT ERROR" "$OUT/host.log" "$OUT/client.log"
echo "link: $(grep -h 'NETDBG attempt {' "$OUT/client.log" | tail -1 | grep -oE '"kind": "[A-Za-z]+"')"
tail -1 "$OUT/natsim.log"
