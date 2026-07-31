#!/bin/bash
# Delete exactly one Linode, then verify through an independent API client.
set -Eeuo pipefail

readonly LINODE_CLI=/home/joe/.local/bin/linode-cli
readonly API_CLIENT=/home/joe/code/mmca-clj/scripts/linode_instance_api.py

if [[ $# -ne 2 ]]; then
  echo "usage: $0 INSTANCE_ID LOG" >&2
  exit 64
fi

readonly INSTANCE_ID=$1
readonly LOG=$2

[[ $INSTANCE_ID =~ ^[0-9]+$ ]] || { echo "invalid instance id" >&2; exit 64; }
mkdir -p "$(dirname "$LOG")"

say() {
  printf '%s %s\n' "$(date --utc +%FT%TZ)" "$*" | tee -a "$LOG" >&2
}

verify_absent() {
  local output rc
  set +e
  output=$("$API_CLIENT" status "$INSTANCE_ID" 2>&1)
  rc=$?
  set -e
  printf '%s\n' "$output" >>"$LOG"
  [[ $rc -eq 3 ]]
}

if verify_absent; then
  say "instance $INSTANCE_ID was already absent"
  exit 0
fi

say "requesting deletion of instance $INSTANCE_ID through linode-cli"
if ! timeout 180 "$LINODE_CLI" linodes delete "$INSTANCE_ID" >>"$LOG" 2>&1; then
  say "primary delete failed; requesting deletion through independent HTTPS client"
  "$API_CLIENT" delete "$INSTANCE_ID" >>"$LOG" 2>&1
fi

for attempt in 1 2 3 4 5 6; do
  if verify_absent; then
    say "deletion independently confirmed: API GET returned 404"
    exit 0
  fi
  say "instance still present or verification failed (attempt $attempt/6)"
  sleep 10
done

say "FATAL: instance $INSTANCE_ID deletion is not confirmed"
exit 1
