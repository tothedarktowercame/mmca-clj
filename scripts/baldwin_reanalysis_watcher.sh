#!/bin/bash
# Bank a completed analysis-only recovery without altering or deleting its worker.
set -Eeuo pipefail

readonly SSH=${BALDWIN_REANALYSIS_SSH_BIN:-/usr/bin/ssh}
readonly SCP=${BALDWIN_REANALYSIS_SCP_BIN:-/usr/bin/scp}
readonly POLL_SECONDS=${BALDWIN_REANALYSIS_POLL_SECONDS:-30}

if [[ $# -ne 4 ]]; then
  echo "usage: $0 WORKER_HOST RUN_ID MAX_MIN DEST_ROOT" >&2
  exit 64
fi

readonly WORKER_HOST=$1 RUN_ID=$2 MAX_MIN=$3 DEST_ROOT=$4
readonly REMOTE_DIR=/root/baldwin-runs/$RUN_ID
readonly REMOTE_MARKER=/root/${RUN_ID}.reanalysis.done
readonly DEST=$DEST_ROOT/${RUN_ID}.reanalysis
readonly LOG=$DEST_ROOT/${RUN_ID}.reanalysis-watcher.log

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || exit 64
[[ $MAX_MIN =~ ^[0-9]+$ && $MAX_MIN -gt 0 ]] || exit 64
[[ $POLL_SECONDS =~ ^[0-9]+$ && $POLL_SECONDS -gt 0 ]] || exit 64
[[ ! -e $DEST ]] || { echo "destination already exists: $DEST" >&2; exit 64; }
mkdir -p "$DEST_ROOT"

say() {
  printf '%s %s\n' "$(date --utc +%FT%TZ)" "$*" | tee -a "$LOG" >&2
}

ssh_box() {
  timeout 60 "$SSH" -n -o BatchMode=yes -o ConnectTimeout=15 \
    "$WORKER_HOST" "$@"
}

bank_success() {
  local incoming name
  incoming=$(mktemp -d "$DEST_ROOT/.incoming-${RUN_ID}.reanalysis.XXXXXX")
  for name in result.recovered.edn analysis.recovered.stdout \
      analysis.recovered.stderr reanalysis.timing.tsv \
      REANALYSIS-CHECKSUMS.sha256; do
    timeout 600 "$SCP" -q -o BatchMode=yes \
      "$WORKER_HOST:$REMOTE_DIR/$name" "$incoming/$name"
  done
  (cd "$incoming" && sha256sum --check REANALYSIS-CHECKSUMS.sha256)
  mv "$incoming" "$DEST"
  say "recovered analysis verified and banked at $DEST"
}

bank_failure() {
  local incoming name
  incoming=$(mktemp -d "$DEST_ROOT/.incoming-${RUN_ID}.reanalysis-failure.XXXXXX")
  for name in analysis.recovered.stderr reanalysis.timing.tsv; do
    if ssh_box "test -f '$REMOTE_DIR/$name'"; then
      timeout 600 "$SCP" -q -o BatchMode=yes \
        "$WORKER_HOST:$REMOTE_DIR/$name" "$incoming/$name"
    fi
  done
  ssh_box "cat '$REMOTE_MARKER'" >"$incoming/TERMINAL-STATE.txt"
  (cd "$incoming" && sha256sum -- * >CHECKSUMS.sha256)
  mv "$incoming" "$DEST.failure"
  say "reanalysis failure banked at $DEST.failure; worker retained"
}

deadline=$((SECONDS + MAX_MIN * 60))
say "watching reanalysis run=$RUN_ID on $WORKER_HOST for at most ${MAX_MIN}m"
while (( SECONDS < deadline )); do
  state=$(ssh_box "cat '$REMOTE_MARKER' 2>/dev/null" || true)
  case "$state" in
    success)
      bank_success
      exit 0
      ;;
    failed:*)
      bank_failure
      exit 2
      ;;
  esac
  sleep "$POLL_SECONDS"
done

say "reanalysis watcher timed out; worker retained"
exit 4
