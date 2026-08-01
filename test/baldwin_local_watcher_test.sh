#!/bin/bash
set -Eeuo pipefail

readonly REPO=$(cd "$(dirname "$0")/.." && pwd)
readonly TMP=$(mktemp -d)
trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/bin" "$TMP/remote/success" "$TMP/remote/failure" "$TMP/dest"

printf 'successful result\n' >"$TMP/remote/success/result.edn"
(cd "$TMP/remote/success" && sha256sum result.edn >CHECKSUMS.sha256)
printf 'failure report\n' >"$TMP/remote/failure/FAILURE-REPORT.txt"
(cd "$TMP/remote/failure" && sha256sum FAILURE-REPORT.txt >CHECKSUMS.sha256)

cat >"$TMP/bin/ssh" <<'EOF'
#!/bin/bash
printf '%s\n' "$BALDWIN_TEST_STATE"
EOF
cat >"$TMP/bin/rsync" <<'EOF'
#!/bin/bash
for arg in "$@"; do
  previous=${current-}
  current=$arg
done
case $BALDWIN_TEST_STATE in
  failure) source_dir=$BALDWIN_TEST_ROOT/remote/failure ;;
  success) source_dir=$BALDWIN_TEST_ROOT/remote/success ;;
  *) exit 1 ;;
esac
cp -a "$source_dir/." "$current/"
EOF
chmod +x "$TMP/bin/"*

set +e
BALDWIN_TEST_ROOT=$TMP BALDWIN_TEST_STATE=failure \
BALDWIN_WATCHER_SSH_BIN="$TMP/bin/ssh" \
BALDWIN_WATCHER_RSYNC_BIN="$TMP/bin/rsync" \
BALDWIN_WATCHER_POLL_SECONDS=1 \
  "$REPO/scripts/baldwin_local_watcher.sh" test-host test-run 123 1 "$TMP/dest"
failure_rc=$?
set -e
[[ $failure_rc -eq 2 ]]
[[ -f $TMP/dest/test-run.failure/FAILURE-REPORT.txt ]]
grep -F 'Run failed' "$TMP/dest/test-run.ALERT.txt" >/dev/null

rm -f "$TMP/dest/test-run.ALERT.txt"
BALDWIN_TEST_ROOT=$TMP BALDWIN_TEST_STATE=success \
BALDWIN_WATCHER_SSH_BIN="$TMP/bin/ssh" \
BALDWIN_WATCHER_RSYNC_BIN="$TMP/bin/rsync" \
BALDWIN_WATCHER_POLL_SECONDS=1 \
  "$REPO/scripts/baldwin_local_watcher.sh" test-host success-run 123 1 "$TMP/dest"
[[ -f $TMP/dest/success-run/result.edn ]]
grep -F 'Run completed' "$TMP/dest/success-run.ALERT.txt" >/dev/null

printf 'PASS: failure bundle mirrored and alerted\n'
printf 'PASS: success bundle mirrored and alerted\n'
