#!/bin/bash
# Watch one run-specific marker, bank an exact artifact set, and always tear down.
set -Eeuo pipefail

readonly TEARDOWN=/home/joe/code/mmca-clj/scripts/baldwin_box_teardown.sh
readonly API_CLIENT=/home/joe/code/mmca-clj/scripts/linode_instance_api.py
readonly SSH=/usr/bin/ssh
readonly SCP=/usr/bin/scp

if [[ $# -ne 6 ]]; then
  echo "usage: $0 INSTANCE_ID IP RUN_ID MAX_MIN ARTIFACT_SPEC DEST_ROOT" >&2
  exit 64
fi

readonly INSTANCE_ID=$1
readonly IP=$2
readonly RUN_ID=$3
readonly MAX_MIN=$4
readonly ARTIFACT_SPEC=$5
readonly DEST_ROOT=$6
readonly FINAL_DEST=$DEST_ROOT/$RUN_ID
readonly LOG=$DEST_ROOT/${RUN_ID}.supervisor.log
readonly DEADMAN_UNIT=baldwin-deadman-${INSTANCE_ID}-${RUN_ID}

[[ $INSTANCE_ID =~ ^[0-9]+$ ]] || { echo "invalid instance id" >&2; exit 64; }
[[ $MAX_MIN =~ ^[0-9]+$ ]] || { echo "invalid timeout" >&2; exit 64; }
[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || { echo "invalid run id" >&2; exit 64; }
[[ -f $ARTIFACT_SPEC ]] || { echo "missing artifact spec: $ARTIFACT_SPEC" >&2; exit 64; }
[[ ! -e $FINAL_DEST ]] || { echo "destination already exists: $FINAL_DEST" >&2; exit 64; }
mkdir -p "$DEST_ROOT"

say() {
  printf '%s %s\n' "$(date --utc +%FT%TZ)" "$*" | tee -a "$LOG" >&2
}

ssh_box() {
  timeout 60 "$SSH" -o BatchMode=yes -o StrictHostKeyChecking=no \
    -o ConnectTimeout=15 "root@$IP" "$@"
}

teardown() {
  local rc=$?
  trap - EXIT INT TERM
  say "supervisor exiting with status $rc; invoking teardown"
  if "$TEARDOWN" "$INSTANCE_ID" "$LOG"; then
    systemctl --user stop "${DEADMAN_UNIT}.timer" "${DEADMAN_UNIT}.service" \
      >/dev/null 2>&1 || true
  else
    say "FATAL: teardown did not confirm; dead-man timer remains armed"
    exit 1
  fi
  exit "$rc"
}
trap teardown EXIT INT TERM

say "preflight for instance=$INSTANCE_ID ip=$IP run=$RUN_ID"
"$API_CLIENT" status "$INSTANCE_ID" >>"$LOG"
started=
for _attempt in 1 2 3 4 5 6; do
  started=$(ssh_box "cat /root/${RUN_ID}.started 2>/dev/null" || true)
  [[ $started == "started:$RUN_ID" ]] && break
  sleep 5
done
[[ $started == "started:$RUN_ID" ]] || {
  say "run-specific start marker was not observed"
  exit 1
}

say "arming independent ${MAX_MIN}-minute dead-man deletion"
systemd-run --user --unit "$DEADMAN_UNIT" --on-active="${MAX_MIN}m" \
  "$TEARDOWN" "$INSTANCE_ID" "$LOG" >>"$LOG"

deadline=$((SECONDS + MAX_MIN * 60))
state=
while (( SECONDS < deadline )); do
  state=$(ssh_box "cat /root/${RUN_ID}.done 2>/dev/null" || true)
  case "$state" in
    "success:$RUN_ID")
      say "run-specific success marker received"
      break
      ;;
    "failed:$RUN_ID")
      say "remote run reported failure"
      exit 1
      ;;
  esac
  sleep 30
done
[[ $state == "success:$RUN_ID" ]] || { say "hard timeout reached"; exit 1; }

incoming=$(mktemp -d "$DEST_ROOT/.incoming-${RUN_ID}.XXXXXX")
say "banking exact artifact set into $incoming"
while IFS=$'\t' read -r remote_path local_name min_bytes; do
  [[ -z $remote_path || $remote_path == \#* ]] && continue
  [[ $remote_path == /* ]] || { say "artifact path is not absolute: $remote_path"; exit 1; }
  [[ $local_name != */* && -n $local_name ]] || {
    say "artifact local name is unsafe: $local_name"
    exit 1
  }
  [[ $min_bytes =~ ^[0-9]+$ ]] || { say "invalid minimum size for $local_name"; exit 1; }
  timeout 600 "$SCP" -q -o BatchMode=yes -o StrictHostKeyChecking=no \
    "root@$IP:$remote_path" "$incoming/$local_name"
  size=$(stat --format=%s "$incoming/$local_name")
  (( size >= min_bytes )) || {
    say "artifact $local_name is only $size bytes; expected at least $min_bytes"
    exit 1
  }
done <"$ARTIFACT_SPEC"

cp "$ARTIFACT_SPEC" "$incoming/ARTIFACTS.tsv"
(
  cd "$incoming"
  sha256sum --check CHECKSUMS.remote.sha256
)
(
  cd "$incoming"
  sha256sum -- * >CHECKSUMS.sha256
)
mv "$incoming" "$FINAL_DEST"
say "artifacts validated and banked at $FINAL_DEST"
