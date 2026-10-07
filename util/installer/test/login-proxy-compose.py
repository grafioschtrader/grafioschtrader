"""Derive a disposable Caddy/login topology from the real, resolved deployment configuration."""

import json
import ipaddress
import os
from pathlib import Path
import sys

config = json.load(sys.stdin)
repo = str(Path(sys.argv[1]).resolve())
image = os.environ.get("GT_TEST_JAVA_IMAGE", "gt-installer-mail-application")
backend = config["services"]["backend"]
web = config["services"]["web"]
trust = backend["environment"]["G_SECURITY_LOGIN_TRUSTEDPROXIES"]
address = web["networks"]["proxy"]["ipv4_address"]
assert trust.split(",") == ["127.0.0.1", "::1", address]
assert backend["environment"]["SERVER_FORWARD_HEADERS_STRATEGY"] == "none"
assert "ports" not in backend
assert set(backend["networks"]) == {"default", "proxy"}
assert set(web["networks"]) == {"proxy"}
pool = config["networks"]["proxy"]["ipam"]["config"][0]
assert ipaddress.ip_address(address) in ipaddress.ip_network(pool["subnet"])
assert ipaddress.ip_address(address) not in ipaddress.ip_network(pool["ip_range"])
config["services"] = {
    "backend": {
        "image": image,
        "environment": {"G_SECURITY_LOGIN_TRUSTEDPROXIES": trust, "GT_LOGIN_PROXY_TEST": "yes"},
        "networks": backend["networks"],
        "volumes": [f"{repo}:/repo:ro"],
        "command": ["bash", "/repo/util/installer/test/login-proxy-container.sh"],
    },
    "web": {
        "image": os.environ.get("GT_TEST_CADDY_IMAGE", "caddy:2-alpine"),
        "environment": {"GT_SITE_ADDRESS": ":80"},
        "networks": web["networks"],
        "volumes": [f"{repo}/docker/Caddyfile:/etc/caddy/Caddyfile:ro"],
    },
    **{name: {"image": image, "networks": ["proxy"], "command": ["sleep", "infinity"]}
       for name in ("client1", "client2")},
}
config.pop("volumes", None)
config.pop("name", None)
for network in config["networks"].values():
    network.pop("name", None)
json.dump(config, sys.stdout)
