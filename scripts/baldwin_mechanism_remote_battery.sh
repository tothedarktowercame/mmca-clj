#!/bin/bash
# Run the preregistered Baldwin mechanism diagnostics sequentially on one worker.
set -Eeuo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: $0 RUN_ID REVISION INPUT_RECORD INPUT_SHA256" >&2
  exit 64
fi

readonly RUN_ID=$1 REVISION=$2 INPUT_RECORD=$3 INPUT_SHA256=$4
readonly REPO=/root/mmca-clj
readonly OUT=/root/baldwin-runs/$RUN_ID
readonly STARTED=/root/${RUN_ID}.started
readonly DONE=/root/${RUN_ID}.done
readonly MAP_CHUNK_WIDTH=5

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || { echo "invalid run id" >&2; exit 64; }
[[ $REVISION =~ ^[0-9a-f]{40}$ ]] || { echo "revision must be a full commit SHA" >&2; exit 64; }
[[ $INPUT_SHA256 =~ ^[0-9a-f]{64}$ ]] || { echo "invalid input checksum" >&2; exit 64; }
[[ -f $INPUT_RECORD ]] || { echo "missing input record: $INPUT_RECORD" >&2; exit 1; }
[[ ! -e $OUT && ! -e $STARTED && ! -e $DONE ]] || {
  echo "run id already has remote state; refusing to mix runs" >&2
  exit 1
}

mkdir -p "$OUT"
exec > >(tee -a "$OUT/battery.log") 2>&1

failed() {
  local rc=$?
  trap - EXIT
  printf 'failed:%s\n' "$RUN_ID" >"$DONE"
  echo "battery failed with status $rc"
  exit "$rc"
}
trap failed EXIT
printf 'started:%s\n' "$RUN_ID" >"$STARTED"

cd "$REPO"
[[ $(git rev-parse HEAD) == "$REVISION" ]] || {
  echo "checked-out revision does not match requested revision" >&2
  exit 1
}
git diff --quiet
git diff --cached --quiet
[[ -z $(git status --porcelain --untracked-files=all) ]] || {
  echo "worker checkout has untracked files; refusing unidentified source" >&2
  exit 1
}
printf '%s  %s\n' "$INPUT_SHA256" "$INPUT_RECORD" | sha256sum --check

echo "running source gates"
clj-kondo --lint \
  src/mmca/baldwin_mechanism.clj \
  scripts/baldwin_target_stationarity.clj \
  scripts/baldwin_context_stationarity.clj \
  scripts/baldwin_allele_sensitivity.clj \
  scripts/assimilation_map.clj \
  test/mmca/baldwin_mechanism_test.clj \
  >"$OUT/clj-kondo.log" 2>&1
clojure -M:test -m mmca.test-runner >"$OUT/tests.log" 2>&1
clojure -M scripts/hinton_nowlan_positive.clj >"$OUT/positive-control.tsv"

echo "reproducing the four-cell stationarity grid"
timeout 30m clojure -M scripts/baldwin_target_stationarity.clj \
  "$INPUT_RECORD" "$OUT/target-stationarity.edn" 8 20260802 \
  >"$OUT/target-stationarity.stdout" 2>"$OUT/target-stationarity.stderr"

echo "testing the 16-context representation"
timeout 30m clojure -M scripts/baldwin_context_stationarity.clj \
  "$INPUT_RECORD" "$OUT/context-stationarity.edn" 8 \
  >"$OUT/context-stationarity.stdout" 2>"$OUT/context-stationarity.stderr"

echo "measuring paired inherited-allele sensitivity"
/usr/bin/time -f 'allele-sensitivity\t%e\t%M' -a -o "$OUT/timings.tsv" \
  timeout 180m clojure -M scripts/baldwin_allele_sensitivity.clj \
  "$INPUT_RECORD" "$OUT/allele-sensitivity.edn" 3 8 \
  >"$OUT/allele-sensitivity.stdout" 2>"$OUT/allele-sensitivity.stderr"

echo "mapping all 80 loci by all 256 held rules in resumable chunks"
: >"$OUT/assimilation-map.tsv"
for start in $(seq 0 "$MAP_CHUNK_WIDTH" 79); do
  end=$((start + MAP_CHUNK_WIDTH))
  (( end <= 80 )) || end=80
  label=$(printf 'assimilation-map-%02d-%02d' "$start" "$end")
  chunk="$OUT/${label}.tsv"
  /usr/bin/time -f "$label\t%e\t%M" -a -o "$OUT/timings.tsv" \
    timeout 120m clojure -M scripts/assimilation_map.clj \
    "$INPUT_RECORD" "$start" "$end" 0.05 3 10 \
    >"$chunk" 2>"$OUT/${label}.stderr"
  [[ $(wc -l <"$chunk") -eq $((1 + (end - start) * 256)) ]] || {
    echo "$label has the wrong row count" >&2
    exit 1
  }
  if (( start == 0 )); then
    cp "$chunk" "$OUT/assimilation-map.tsv"
  else
    tail -n +2 "$chunk" >>"$OUT/assimilation-map.tsv"
  fi
done
[[ $(wc -l <"$OUT/assimilation-map.tsv") -eq 20481 ]]

{
  printf 'run_id=%s\n' "$RUN_ID"
  printf 'revision=%s\n' "$REVISION"
  printf 'input_sha256=%s\n' "$INPUT_SHA256"
  printf 'completed_at=%s\n' "$(date --utc +%FT%TZ)"
} >"$OUT/manifest.txt"
(cd "$OUT" && find . -maxdepth 1 -type f \
  ! -name battery.log ! -name CHECKSUMS.remote.sha256 \
  -printf '%f\0' | sort -z | xargs -0 sha256sum >CHECKSUMS.remote.sha256)
printf 'success:%s\n' "$RUN_ID" >"$DONE"
trap - EXIT
echo "battery complete"
