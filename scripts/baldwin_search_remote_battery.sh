#!/bin/bash
# Run the preregistered Baldwin search pilot sequentially on one worker.
set -Eeuo pipefail

if [[ $# -ne 3 ]]; then
  echo "usage: $0 RUN_ID REVISION EVOLUTION_SEED" >&2
  exit 64
fi

readonly RUN_ID=$1 REVISION=$2 EVOLUTION_SEED=$3
readonly REPO=/root/mmca-clj
readonly OUT=/root/baldwin-runs/$RUN_ID
readonly STARTED=/root/${RUN_ID}.started
readonly DONE=/root/${RUN_ID}.done
readonly REGISTRATION=$REPO/holes/BALDWIN-SEARCH-PREREGISTRATION.edn

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || exit 64
[[ $REVISION =~ ^[0-9a-f]{40}$ ]] || exit 64
[[ ! -e $OUT && ! -e $STARTED && ! -e $DONE ]] || exit 1
mkdir -p "$OUT"
exec > >(tee -a "$OUT/battery.log") 2>&1

failed() {
  local rc=$?
  trap - EXIT
  printf 'failed:%s\n' "$RUN_ID" >"$DONE"
  exit "$rc"
}
trap failed EXIT
printf 'started:%s\n' "$RUN_ID" >"$STARTED"

cd "$REPO"
[[ $(git rev-parse HEAD) == "$REVISION" ]]
[[ -z $(git status --porcelain --untracked-files=all) ]]

IFS=$'\t' read -r MODE GENERATIONS POPULATION SEEDS SITES WARMUP COST HGT \
  REGISTERED_SEED ARM_TIMEOUT_MIN FIELD_RATE < <(
  clojure -M -e \
    "(require '[mmca.baldwin-preregistration :as p] '[clojure.string :as s])
     (let [r (p/read-registration \"$REGISTRATION\") x (:production-protocol r)]
       (println (s/join \"\\t\" [(:mode x) (:generations x) (:population x)
         (:evaluation-seeds x) (:evaluation-sites x) (:warmup x) (:cost x)
         (if (:hgt x) 1 0) (:pilot-evolution-seed x) (:arm-timeout-minutes x)
         (:mutation-rate r)])))")
[[ $EVOLUTION_SEED == "$REGISTERED_SEED" ]] || {
  echo "pilot seed differs from preregistration" >&2
  exit 1
}

echo "validating preregistration and source gates"
clojure -M scripts/validate_baldwin_preregistration.clj "$REGISTRATION" \
  >"$OUT/preregistration.validation.edn"
clj-kondo --lint src/mmca/baldwin_selection.clj src/mmca/baldwin_spec.clj \
  src/mmca/baldwin_preregistration.clj src/mmca/baldwin_search_analysis.clj \
  scripts/baldwin_selection.clj scripts/analyze_baldwin_search.clj \
  scripts/check_baldwin_search_separation.clj \
  >"$OUT/clj-kondo.log" 2>&1
clojure -M:test -m mmca.test-runner >"$OUT/tests.log" 2>&1
clojure -M scripts/hinton_nowlan_positive.clj >"$OUT/positive-control.tsv"

echo "creating revision-bound preflight certificate"
clojure -M scripts/baldwin_selection.clj \
  --preflight-only 1 --preflight-certificate "$OUT/preflight.edn" \
  --revision "$REVISION" --seeds "$SEEDS" --sites "$SITES" \
  >"$OUT/preflight.stdout" 2>"$OUT/preflight.stderr"

readonly FIXED_P0=$(clojure -M -e \
  "(require '[mmca.baldwin-preregistration :as p]) (print (:fixed-p0 (p/read-registration \"$REGISTRATION\")))")
common_args=(
  --mode "$MODE" --gens "$GENERATIONS" --pop "$POPULATION"
  --seeds "$SEEDS" --sites "$SITES" --field-rate "$FIELD_RATE"
  --warmup "$WARMUP" --c "$COST" --hgt "$HGT"
  --evolution-seed "$EVOLUTION_SEED" --revision "$REVISION"
  --preflight-certificate "$OUT/preflight.edn"
)

run_arm() {
  local arm=$1 mutation=$2 p0=$3 neutral=$4
  local p0_args=()
  [[ $p0 == fixed ]] && p0_args=(--fixed-p0 "$FIXED_P0")
  echo "running $arm"
  /usr/bin/time -f "$arm\t%e\t%M" -a -o "$OUT/timings.tsv" \
    timeout "${ARM_TIMEOUT_MIN}m" clojure -M scripts/baldwin_selection.clj \
    "${common_args[@]}" --mutation-mode "$mutation" --p0-mode "$p0" \
    "${p0_args[@]}" --neutral "$neutral" \
    --record "$OUT/$arm.edn" --manifest "$OUT/$arm.manifest.edn" \
    >"$OUT/$arm.tsv" 2>"$OUT/$arm.stderr"
  clojure -M scripts/validate_baldwin_run.clj \
    "$OUT/$arm.tsv" "$OUT/$arm.edn" "$MODE" "$GENERATIONS" "$POPULATION" \
    >"$OUT/$arm.validation.edn"
}

run_arm neutral independent variable 1
run_arm independent-variable independent variable 0
run_arm coupled-variable coupled variable 0
clojure -M scripts/check_baldwin_search_separation.clj \
  "$OUT/independent-variable.edn" "$OUT/coupled-variable.edn" \
  >"$OUT/treatment-separation.edn"
run_arm independent-fixed independent fixed 0
run_arm coupled-fixed coupled fixed 0

clojure -M scripts/analyze_baldwin_search.clj "$REGISTRATION" "$OUT" "$OUT/result.edn" \
  >"$OUT/analysis.stdout"

(cd "$OUT" && find . -maxdepth 1 -type f \
  ! -name battery.log ! -name CHECKSUMS.remote.sha256 \
  -printf '%f\0' | sort -z | xargs -0 sha256sum >CHECKSUMS.remote.sha256)
printf 'success:%s\n' "$RUN_ID" >"$DONE"
trap - EXIT
echo "battery complete"
