#!/bin/bash
# Independently mirror a Chicago-supervised Baldwin run to this host and alert
# as soon as either a success archive or a retained failure archive appears.
set -Eeuo pipefail

readonly SSH=${BALDWIN_WATCHER_SSH_BIN:-/usr/bin/ssh}
readonly RSYNC=${BALDWIN_WATCHER_RSYNC_BIN:-/usr/bin/rsync}
readonly POLL_SECONDS=${BALDWIN_WATCHER_POLL_SECONDS:-30}

if [[ $# -ne 5 ]]; then
  echo "usage: $0 CHICAGO_HOST RUN_ID INSTANCE_ID MAX_MIN DEST_ROOT" >&2
  exit 64
fi

readonly CHICAGO_HOST=$1 RUN_ID=$2 INSTANCE_ID=$3 MAX_MIN=$4 DEST_ROOT=$5
readonly REMOTE_ROOT=/home/joe/code/mmca-clj/data/baldwin-runs
readonly LOCAL_SUCCESS=$DEST_ROOT/$RUN_ID
readonly LOCAL_FAILURE=$DEST_ROOT/${RUN_ID}.failure
readonly LOG=$DEST_ROOT/${RUN_ID}.local-watcher.log
readonly ALERT=$DEST_ROOT/${RUN_ID}.ALERT.txt

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || exit 64
[[ $INSTANCE_ID =~ ^[0-9]+$ ]] || exit 64
[[ $MAX_MIN =~ ^[0-9]+$ && $MAX_MIN -gt 0 ]] || exit 64
[[ $POLL_SECONDS =~ ^[0-9]+$ && $POLL_SECONDS -gt 0 ]] || exit 64
mkdir -p "$DEST_ROOT"

say() {
  printf '%s %s\n' "$(date --utc +%FT%TZ)" "$*" | tee -a "$LOG" >&2
}

alert() {
  local urgency=$1 message=$2
  say "ALERT: $message"
  printf '%s\n' "$message" >"$ALERT"
  logger -p "user.$urgency" -t baldwin-local-watcher -- "$message" || true
  if command -v notify-send >/dev/null 2>&1; then
    notify-send --urgency="$([[ $urgency == err ]] && echo critical || echo normal)" \
      "Baldwin experiment $RUN_ID" "$message" >/dev/null 2>&1 || true
  fi
}

remote_state() {
  "$SSH" -o BatchMode=yes -o ConnectTimeout=15 "$CHICAGO_HOST" \
    "if [[ -d '$REMOTE_ROOT/${RUN_ID}.failure' ]]; then
       echo failure
     elif [[ -d '$REMOTE_ROOT/$RUN_ID' ]]; then
       echo success
     else
       systemctl --user show 'baldwin-supervisor-${RUN_ID}.service' \
         -p ActiveState -p SubState --value 2>/dev/null | paste -sd / - \
         || echo unknown
     fi"
}

mirror_terminal() {
  local kind=$1 remote local incoming
  if [[ $kind == success ]]; then
    remote=$REMOTE_ROOT/$RUN_ID
    local=$LOCAL_SUCCESS
  else
    remote=$REMOTE_ROOT/${RUN_ID}.failure
    local=$LOCAL_FAILURE
  fi
  [[ -d $local ]] && return 0
  incoming=$(mktemp -d "$DEST_ROOT/.incoming-${RUN_ID}.${kind}.XXXXXX")
  if ! "$RSYNC" -a --protect-args "$CHICAGO_HOST:$remote/" "$incoming/"; then
    rm -rf -- "$incoming"
    return 1
  fi
  if [[ -f $incoming/CHECKSUMS.sha256 ]]; then
    if ! (cd "$incoming" && sha256sum --check CHECKSUMS.sha256); then
      rm -rf -- "$incoming"
      return 1
    fi
  fi
  mv "$incoming" "$local"
}

deadline=$((SECONDS + MAX_MIN * 60))
previous=
say "watching run=$RUN_ID instance=$INSTANCE_ID via $CHICAGO_HOST for at most ${MAX_MIN}m"
while (( SECONDS < deadline )); do
  state=$(remote_state 2>>"$LOG" || true)
  if [[ $state != "$previous" ]]; then
    say "remote supervisor state: ${state:-unreachable}"
    previous=$state
  fi
  case $state in
    failure)
      if mirror_terminal failure; then
        alert err "Run failed; diagnostics mirrored to $LOCAL_FAILURE. Worker retained under Chicago dead-man for inspection."
        exit 2
      fi
      alert err "Run failed, but diagnostic mirroring failed. Inspect Chicago and worker immediately."
      exit 3
      ;;
    success)
      if mirror_terminal success; then
        alert notice "Run completed; validated artifacts mirrored to $LOCAL_SUCCESS."
        exit 0
      fi
      alert err "Run completed on Chicago, but local artifact mirroring failed."
      exit 3
      ;;
  esac
  sleep "$POLL_SECONDS"
done

alert err "Local watcher reached its ${MAX_MIN}m deadline without a terminal archive; inspect Chicago immediately."
exit 4
