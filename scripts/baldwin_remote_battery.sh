#!/bin/bash
# Run the paid Baldwin battery sequentially on one worker.
set -Eeuo pipefail

if [[ $# -lt 3 || $# -gt 5 ]]; then
  echo "usage: $0 RUN_ID REVISION EVOLUTION_SEED [GENERATIONS] [POPULATION]" >&2
  exit 64
fi

readonly RUN_ID=$1
readonly REVISION=$2
readonly EVOLUTION_SEED=$3
readonly GENERATIONS=${4:-30}
readonly POPULATION=${5:-24}
readonly REPO=/root/mmca-clj
readonly OUT=/root/baldwin-runs/$RUN_ID
readonly STARTED=/root/${RUN_ID}.started
readonly DONE=/root/${RUN_ID}.done
readonly SEEDS=3
readonly SITES=10
readonly WARMUP=8
readonly FIELD_RATE=0.02
readonly ARM_TIMEOUT_MIN=75

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || { echo "invalid run id" >&2; exit 64; }
[[ $REVISION =~ ^[0-9a-f]{40}$ ]] || { echo "revision must be a full commit SHA" >&2; exit 64; }
[[ $EVOLUTION_SEED =~ ^[0-9]+$ ]] || { echo "invalid evolution seed" >&2; exit 64; }
[[ ! -e $OUT && ! -e $STARTED && ! -e $DONE ]] || {
  echo "run id already has remote state; refusing to mix runs"
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
  echo "checked-out revision does not match requested revision"
  exit 1
}
git diff --quiet
git diff --cached --quiet
[[ -z $(git status --porcelain --untracked-files=all) ]] || {
  echo "worker checkout has untracked files; refusing an unidentifiable source state"
  exit 1
}

echo "running source gates"
clj-kondo --lint src/mmca/baldwin_selection.clj src/mmca/baldwin_artifacts.clj \
  scripts/baldwin_selection.clj scripts/assimilation_probe.clj \
  scripts/assimilation_map.clj scripts/assimilation_path.clj \
  scripts/baldwin_mutation_null.clj scripts/validate_baldwin_run.clj \
  scripts/treatment_separation.clj scripts/hinton_nowlan_positive.clj \
  test/mmca/baldwin_selection_test.clj test/mmca/baldwin_artifacts_test.clj \
  test/mmca/hinton_nowlan_test.clj \
  >"$OUT/clj-kondo.log" 2>&1
clojure -M:test -m mmca.test-runner >"$OUT/tests.log" 2>&1
clojure -M scripts/hinton_nowlan_positive.clj >"$OUT/positive_control.tsv"

echo "creating one revision-bound preflight certificate"
clojure -M scripts/baldwin_selection.clj \
  --preflight-only 1 --preflight-certificate "$OUT/preflight.edn" \
  --revision "$REVISION" --seeds "$SEEDS" --sites "$SITES" \
  >"$OUT/preflight.stdout" 2>"$OUT/preflight.stderr"

common_args=(
  --gens "$GENERATIONS" --pop "$POPULATION"
  --seeds "$SEEDS" --sites "$SITES" --field-rate "$FIELD_RATE"
  --warmup "$WARMUP" --evolution-seed "$EVOLUTION_SEED"
  --revision "$REVISION" --preflight-certificate "$OUT/preflight.edn"
)

echo "one-generation production-shape canary"
timeout 20m clojure -M scripts/baldwin_selection.clj \
  --mode hold-only --gens 1 --pop 4 --seeds "$SEEDS" --sites "$SITES" \
  --field-rate "$FIELD_RATE" --warmup 0 --c 0.05 \
  --evolution-seed "$EVOLUTION_SEED" --revision "$REVISION" \
  --preflight-certificate "$OUT/preflight.edn" \
  --record "$OUT/canary.edn" --manifest "$OUT/canary.manifest.edn" \
  >"$OUT/canary.tsv" 2>"$OUT/canary.stderr"
clojure -M scripts/validate_baldwin_run.clj \
  "$OUT/canary.tsv" "$OUT/canary.edn" hold-only 1 4 \
  >"$OUT/canary.validation.edn"

echo "cheap empirical mutation-only null"
clojure -M scripts/baldwin_mutation_null.clj \
  "$GENERATIONS" "$POPULATION" "$FIELD_RATE" "$EVOLUTION_SEED" \
  >"$OUT/null.tsv" 2>"$OUT/null.manifest.edn"

run_arm() {
  local label=$1
  local mode=$2
  local cost=$3
  echo "running arm $label mode=$mode cost=$cost"
  /usr/bin/time -f "$label\\t%e\\t%M" -a -o "$OUT/timings.tsv" \
    timeout "${ARM_TIMEOUT_MIN}m" clojure -M scripts/baldwin_selection.clj \
    "${common_args[@]}" --mode "$mode" --c "$cost" \
    --record "$OUT/${label}.edn" --manifest "$OUT/${label}.manifest.edn" \
    >"$OUT/${label}.tsv" 2>"$OUT/${label}.stderr"
  clojure -M scripts/validate_baldwin_run.clj \
    "$OUT/${label}.tsv" "$OUT/${label}.edn" "$mode" \
    "$GENERATIONS" "$POPULATION" >"$OUT/${label}.validation.edn"
}

run_arm c05 hold-only 0.05

echo "checking that c05 and c2 can change selection ordering at the cost onset"
clojure -M scripts/treatment_separation.clj \
  "$OUT/c05.edn" "$WARMUP" 0 0.05 >"$OUT/separation_c0_c05.edn"
clojure -M scripts/treatment_separation.clj \
  "$OUT/c05.edn" "$WARMUP" 0.05 2 >"$OUT/separation_c05_c2.edn"

run_arm c2 hold-only 2
run_arm static static-search 0

echo "probing current inherited alleles and the greedy partial-hold path"
timeout 30m clojure -M scripts/assimilation_probe.clj "$OUT/c05.edn" 0.05 \
  >"$OUT/probe.tsv" 2>"$OUT/probe.log"
timeout 75m clojure -M scripts/assimilation_path.clj "$OUT/c05.edn" 0.05 12 3 10 0.9 \
  >"$OUT/path.tsv" 2>"$OUT/path.log"

echo "mapping all 256 rules at the four least-damaging unprepared loci"
awk -F '\t' 'NR > 1 && $8 == "false" {print $1 "\t" $3}' "$OUT/probe.tsv" \
  | sort -t $'\t' -k2,2nr | head -4 | cut -f1 >"$OUT/map_loci.txt"
[[ $(wc -l <"$OUT/map_loci.txt") -eq 4 ]] || {
  echo "probe did not yield four unprepared loci"
  exit 1
}
first_chunk=1
while read -r locus; do
  chunk="$OUT/map_chunk.tmp"
  timeout 75m clojure -M scripts/assimilation_map.clj \
    "$OUT/c05.edn" "$locus" "$((locus + 1))" 0.05 3 10 \
    >"$chunk" 2>>"$OUT/map.log"
  [[ $(wc -l <"$chunk") -eq 257 ]] || {
    echo "map chunk for locus $locus has the wrong row count"
    exit 1
  }
  if [[ $first_chunk -eq 1 ]]; then
    cp "$chunk" "$OUT/map.tsv"
    first_chunk=0
  else
    tail -n +2 "$chunk" >>"$OUT/map.tsv"
  fi
done <"$OUT/map_loci.txt"
[[ $(wc -l <"$OUT/map.tsv") -eq 1025 ]]
rm "$OUT/map_chunk.tmp"

(cd "$OUT" && find . -maxdepth 1 -type f \
  ! -name battery.log ! -name CHECKSUMS.remote.sha256 \
  -printf '%f\0' | sort -z | xargs -0 sha256sum >CHECKSUMS.remote.sha256)
printf 'success:%s\n' "$RUN_ID" >"$DONE"
trap - EXIT
echo "battery complete"
