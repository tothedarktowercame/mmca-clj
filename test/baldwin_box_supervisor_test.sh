#!/bin/bash
set -Eeuo pipefail

readonly REPO=$(cd "$(dirname "$0")/.." && pwd)
readonly TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT

mkdir -p "$TMP/bin" "$TMP/remote/root/baldwin-runs/test-run" "$TMP/dest"
printf 'failed:test-run\n' >"$TMP/remote/root/test-run.done"
printf 'partial diagnostic output\n' \
  >"$TMP/remote/root/baldwin-runs/test-run/battery.log"
printf '/root/baldwin-runs/test-run/battery.log\tbattery.log\t0\n' \
  >"$TMP/artifacts.tsv"
printf '/root/baldwin-runs/test-run/result.edn\tresult.edn\t10\n' \
  >>"$TMP/artifacts.tsv"

cat >"$TMP/bin/api" <<'EOF'
#!/bin/bash
printf '{"state":"present","status":"running"}\n'
EOF
cat >"$TMP/bin/systemd-run" <<'EOF'
#!/bin/bash
printf 'armed\n' >"$BALDWIN_TEST_ROOT/deadman-armed"
EOF
cat >"$TMP/bin/systemctl" <<'EOF'
#!/bin/bash
printf 'stopped\n' >"$BALDWIN_TEST_ROOT/deadman-stopped"
EOF
cat >"$TMP/bin/teardown" <<'EOF'
#!/bin/bash
printf 'called\n' >"$BALDWIN_TEST_ROOT/teardown-called"
EOF
cat >"$TMP/bin/ssh" <<'EOF'
#!/bin/bash
cmd=${!#}
case $cmd in
  *"cat /root/test-run.started"*) printf 'started:test-run\n' ;;
  *"cat /root/test-run.done"*) printf '%s:test-run\n' "${BALDWIN_TEST_TERMINAL_STATE:-failed}" ;;
  "test -f "*)
    path=${cmd#"test -f '"}
    path=${path%"'"}
    [[ -f $BALDWIN_TEST_ROOT/remote$path ]]
    ;;
  *) exit 1 ;;
esac
EOF
cat >"$TMP/bin/scp" <<'EOF'
#!/bin/bash
for arg in "$@"; do
  previous=${current-}
  current=$arg
done
source_path=${previous#*:}
cp "$BALDWIN_TEST_ROOT/remote$source_path" "$current"
EOF
chmod +x "$TMP/bin/"*

set +e
BALDWIN_TEST_ROOT=$TMP \
BALDWIN_SUPERVISOR_TEARDOWN_BIN="$TMP/bin/teardown" \
BALDWIN_SUPERVISOR_API_CLIENT="$TMP/bin/api" \
BALDWIN_SUPERVISOR_SSH_BIN="$TMP/bin/ssh" \
BALDWIN_SUPERVISOR_SCP_BIN="$TMP/bin/scp" \
BALDWIN_SUPERVISOR_SYSTEMD_RUN_BIN="$TMP/bin/systemd-run" \
BALDWIN_SUPERVISOR_SYSTEMCTL_BIN="$TMP/bin/systemctl" \
  "$REPO/scripts/baldwin_box_supervisor.sh" \
    123 192.0.2.1 test-run 300 "$TMP/artifacts.tsv" "$TMP/dest"
rc=$?
set -e

[[ $rc -eq 1 ]]
[[ -f $TMP/deadman-armed ]]
[[ ! -e $TMP/deadman-stopped ]]
[[ ! -e $TMP/teardown-called ]]
[[ -f $TMP/dest/test-run.failure/FAILURE-REPORT.txt ]]
[[ -f $TMP/dest/test-run.failure/battery.log ]]
[[ -f $TMP/dest/test-run.failure/REMOTE-STATE.txt ]]
[[ ! -e $TMP/dest/test-run.failure/result.edn ]]
grep -F $'result.edn\t/root/baldwin-runs/test-run/result.edn' \
  "$TMP/dest/test-run.failure/MISSING.tsv" >/dev/null
grep -F 'worker_retained=true' \
  "$TMP/dest/test-run.failure/FAILURE-REPORT.txt" >/dev/null
(cd "$TMP/dest/test-run.failure" && sha256sum --check CHECKSUMS.sha256 >/dev/null)

printf 'PASS: failure artifacts banked\n'
printf 'PASS: worker retained\n'
printf 'PASS: independent dead-man remains armed\n'

# The success path must retain its original invariant: validate and bank the
# exact artifact set, then delete the worker and cancel the dead-man.
rm -f "$TMP/deadman-armed" "$TMP/deadman-stopped" "$TMP/teardown-called"
mkdir -p "$TMP/success-dest"
(
  cd "$TMP/remote/root/baldwin-runs/test-run"
  sha256sum battery.log >CHECKSUMS.remote.sha256
)
printf '/root/baldwin-runs/test-run/battery.log\tbattery.log\t0\n' \
  >"$TMP/success-artifacts.tsv"
printf '/root/baldwin-runs/test-run/CHECKSUMS.remote.sha256\tCHECKSUMS.remote.sha256\t10\n' \
  >>"$TMP/success-artifacts.tsv"

BALDWIN_TEST_ROOT=$TMP \
BALDWIN_TEST_TERMINAL_STATE=success \
BALDWIN_SUPERVISOR_TEARDOWN_BIN="$TMP/bin/teardown" \
BALDWIN_SUPERVISOR_API_CLIENT="$TMP/bin/api" \
BALDWIN_SUPERVISOR_SSH_BIN="$TMP/bin/ssh" \
BALDWIN_SUPERVISOR_SCP_BIN="$TMP/bin/scp" \
BALDWIN_SUPERVISOR_SYSTEMD_RUN_BIN="$TMP/bin/systemd-run" \
BALDWIN_SUPERVISOR_SYSTEMCTL_BIN="$TMP/bin/systemctl" \
  "$REPO/scripts/baldwin_box_supervisor.sh" \
    123 192.0.2.1 test-run 300 "$TMP/success-artifacts.tsv" "$TMP/success-dest"

[[ -f $TMP/teardown-called ]]
[[ -f $TMP/deadman-stopped ]]
[[ -d $TMP/success-dest/test-run ]]
[[ ! -e $TMP/success-dest/test-run.failure ]]
(cd "$TMP/success-dest/test-run" && sha256sum --check CHECKSUMS.sha256 >/dev/null)
printf 'PASS: success artifacts validated before teardown\n'
