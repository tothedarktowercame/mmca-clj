#!/usr/bin/env python3
"""Independent Linode instance status/delete path used by the Baldwin watchdog.

This deliberately does not import or execute linode-cli. The primary teardown
uses that CLI; verification uses the HTTPS API directly so a missing or broken
CLI cannot also make verification report success.
"""

from __future__ import annotations

import argparse
import configparser
import json
import pathlib
import sys
import urllib.error
import urllib.request

API_ROOT = "https://api.linode.com/v4/linode/instances"
ABSENT = 3


def token_from_config(path: pathlib.Path) -> str:
    config = configparser.ConfigParser()
    if not config.read(path):
        raise RuntimeError(f"cannot read Linode CLI config: {path}")
    profile = config.defaults().get("default-user")
    if not profile or profile not in config:
        raise RuntimeError("Linode CLI config has no valid default-user profile")
    token = config[profile].get("token")
    if not token:
        raise RuntimeError(f"Linode CLI profile {profile!r} has no token")
    return token


def request(
    instance_id: int | None, method: str, config_path: pathlib.Path
) -> tuple[int, dict]:
    token = token_from_config(config_path)
    req = urllib.request.Request(
        API_ROOT if instance_id is None else f"{API_ROOT}/{instance_id}",
        method=method,
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            body = response.read()
            return response.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        body = exc.read()
        payload = json.loads(body) if body else {}
        return exc.code, payload


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("list", "status", "delete"))
    parser.add_argument("instance_id", type=int, nargs="?")
    parser.add_argument(
        "--config",
        type=pathlib.Path,
        default=pathlib.Path.home() / ".config" / "linode-cli",
    )
    args = parser.parse_args()

    if args.action != "list" and args.instance_id is None:
        parser.error(f"{args.action} requires INSTANCE_ID")
    method = "GET" if args.action in {"list", "status"} else "DELETE"
    status, payload = request(args.instance_id, method, args.config)
    if args.action == "list":
        if status != 200:
            print(
                json.dumps(
                    {"state": "api-error", "http_status": status, "errors": payload.get("errors", [])}
                ),
                file=sys.stderr,
            )
            return 1
        print(json.dumps({"state": "authenticated", "instance_count": len(payload.get("data", []))}))
        return 0
    if status == 404:
        print(json.dumps({"instance_id": args.instance_id, "state": "absent"}))
        return ABSENT if args.action == "status" else 0
    if status != 200:
        print(
            json.dumps(
                {
                    "instance_id": args.instance_id,
                    "state": "api-error",
                    "http_status": status,
                    "errors": payload.get("errors", []),
                }
            ),
            file=sys.stderr,
        )
        return 1
    if args.action == "status":
        print(
            json.dumps(
                {
                    "instance_id": payload.get("id"),
                    "label": payload.get("label"),
                    "state": "present",
                    "status": payload.get("status"),
                    "ipv4": payload.get("ipv4", []),
                },
                sort_keys=True,
            )
        )
    else:
        print(json.dumps({"instance_id": args.instance_id, "state": "delete-requested"}))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # explicit error, never absence
        print(json.dumps({"state": "client-error", "error": str(exc)}), file=sys.stderr)
        raise SystemExit(1) from exc
