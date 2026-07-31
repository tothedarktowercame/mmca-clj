#!/bin/bash
# Must pass before creating a paid Baldwin worker.
set -Eeuo pipefail

readonly LINODE_CLI=/home/joe/.local/bin/linode-cli
readonly API_CLIENT=/home/joe/code/mmca-clj/scripts/linode_instance_api.py
readonly TEARDOWN=/home/joe/code/mmca-clj/scripts/baldwin_box_teardown.sh
readonly CONFIG=/home/joe/.config/linode-cli

for executable in "$LINODE_CLI" "$API_CLIENT" "$TEARDOWN" \
                  /usr/bin/curl /usr/bin/python3 /usr/bin/ssh /usr/bin/scp \
                  /usr/bin/systemd-run /bin/systemctl /usr/bin/timeout; do
  [[ -x $executable ]] || { echo "FATAL: required executable missing: $executable" >&2; exit 1; }
done

[[ -f $CONFIG ]] || { echo "FATAL: missing Linode config: $CONFIG" >&2; exit 1; }
permissions=$(stat --format=%a "$CONFIG")
[[ $permissions == 600 ]] || {
  echo "FATAL: $CONFIG permissions are $permissions, expected 600" >&2
  exit 1
}

echo "checking linode-cli authentication" >&2
timeout 60 "$LINODE_CLI" linodes list --json >/dev/null

echo "checking independent HTTPS authentication" >&2
timeout 60 "$API_CLIENT" list >/dev/null

echo "checking user systemd manager" >&2
systemctl --user show-environment >/dev/null
test_unit=baldwin-preflight-$$
systemd-run --user --wait --collect --unit "$test_unit" /usr/bin/true >/dev/null

echo "cloud preflight passed: both control paths and the dead-man scheduler are available" >&2
