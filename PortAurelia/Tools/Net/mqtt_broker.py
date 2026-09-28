#!/usr/bin/env python3
"""Local MQTT broker (amqtt) standing in for the public brokers in the multiplayer tests.

  mqtt_broker.py --ws 127.0.0.1:18083 [--wss 127.0.0.1:18443 --cert cert.pem --key key.pem]

Needs the "amqtt" package (pip install amqtt, e.g. in a venv).
"""
import argparse
import asyncio
import logging

from amqtt.broker import Broker


async def main(a):
    listeners = {"default": {"type": "ws", "bind": a.ws}}
    if a.wss:
        listeners["secure"] = {"type": "ws", "bind": a.wss, "ssl": True, "certfile": a.cert, "keyfile": a.key}
    cfg = {
        "listeners": listeners,
        "plugins": {"amqtt.plugins.authentication.AnonymousAuthPlugin": {"allow_anonymous": True}},
    }
    broker = Broker(cfg)
    await broker.start()
    print("broker up", a.ws, a.wss or "", flush=True)
    while True:
        await asyncio.sleep(3600)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--ws", default="127.0.0.1:18083")
    ap.add_argument("--wss", default="")
    ap.add_argument("--cert", default="")
    ap.add_argument("--key", default="")
    args = ap.parse_args()
    logging.basicConfig(level=logging.WARNING)
    try:
        asyncio.run(main(args))
    except KeyboardInterrupt:
        pass
