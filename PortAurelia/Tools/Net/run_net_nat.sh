#!/usr/bin/env bash
# Internet test: host and friend behind different simulated home routers.
#   Tools/Net/run_net_nat.sh <host_router> <friend_router> <expected: direkt|Relay> [outdir] [extra client args]
# Router types: cone, restricted, portrestricted, symmetric, blocked (see natsim.py).
# Env: BROKERS (default: one local broker + one that is down), MQTT_PY (python with amqtt),
#      NAT_DELAY / NAT_LOSS (ms / %), NO_BROKER=1 (meeting point unreachable),
#      BROKER_ARGS (e.g. a wss listener), GAME_ENV (extra env for both games, e.g. HH_MQTT_CA).
set -u
cd "$(dirname "$0")/../.."
HT=$1; CT=$2; EXP=$3; OUT=${4:-/tmp/hh_net_nat}; shift 4 || shift $#
GODOT=Tools/Godot/Godot_v4.4.1-stable_linux.x86_64
MQTT_PY=${MQTT_PY:-python3}
rm -rf "$OUT"; mkdir -p "$OUT/sv_h" "$OUT/sv_c"
python3 Tools/Net/natsim.py --router "127.0.1.:127.0.101.1:$HT" --router "127.0.2.:127.0.102.1:$CT" \
  --stun 127.0.50.1:3478 --stun 127.0.50.2:3478 --delay "${NAT_DELAY:-15}" --jitter 4 --loss "${NAT_LOSS:-0}" > "$OUT/natsim.log" 2>&1 &
NS=$!
BR=""
if [ "${NO_BROKER:-0}" != "1" ]; then
  $MQTT_PY Tools/Net/mqtt_broker.py --ws 127.0.0.1:18083 ${BROKER_ARGS:-} > "$OUT/broker.log" 2>&1 &
  BR=$!
fi
sleep 1.5
BROKERS=${BROKERS:-ws://127.0.0.1:18083/mqtt,ws://127.0.0.1:18099/mqtt}
COMMON="HH_NET_DEBUG=1 HH_NO_UPNP=1 HH_NO_LAN=1 HH_STUN=127.0.50.1:3478,127.0.50.2:3478 HH_MQTT=$BROKERS HH_TEST_PUBLIC=127.0.10 ${GAME_ENV:-}"
env $COMMON ${HOST_ENV:-} HH_BIND_IP=127.0.1.2 HH_SAVES_DIR="$OUT/sv_h" timeout 400 $GODOT --headless --path Game res://tests/net_nat_test.tscn -- --role host --dir "$OUT" > "$OUT/host.log" 2>&1 &
H=$!
sleep 1
env $COMMON HH_BIND_IP=127.0.2.2 HH_SAVES_DIR="$OUT/sv_c" timeout 400 $GODOT --headless --path Game res://tests/net_nat_test.tscn -- --role client --dir "$OUT" --expect "$EXP" "$@" > "$OUT/client.log" 2>&1
wait $H
kill $NS 2>/dev/null
[ -n "$BR" ] && kill $BR 2>/dev/null
sleep 0.5
grep -hE "^\[(host|client)\]|SCRIPT ERROR" "$OUT/host.log" "$OUT/client.log"
tail -1 "$OUT/natsim.log"
