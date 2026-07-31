#!/bin/bash
# Revision-bound local smoke for the prospective Baldwin 2x2 search experiment.
set -Eeuo pipefail

if [[ $# -ne 2 ]]; then
  echo "usage: $0 REVISION OUTPUT_DIRECTORY" >&2
  exit 64
fi

readonly REVISION=$1
readonly OUT=$2
readonly REPO=$(cd "$(dirname "$0")/.." && pwd)
readonly REGISTRATION=$REPO/holes/BALDWIN-SEARCH-PREREGISTRATION.edn
readonly GENERATIONS=2
readonly POPULATION=8
# The invariant profile is itself a measured quantity.  A cheaper 1x2 smoke made
# every sampled gamma level score zero and was correctly rejected as a dead axis;
# use the preregistered production evaluation shape rather than weakening I6.
readonly SEEDS=3
readonly SITES=10
readonly EVOLUTION_SEED=20260730

[[ $REVISION =~ ^[0-9a-f]{40}$ ]] || { echo "revision must be a full SHA" >&2; exit 64; }
[[ ! -e $OUT ]] || { echo "output directory already exists: $OUT" >&2; exit 1; }
mkdir -p "$OUT"

cd "$REPO"
readonly SCOPE=(
  holes/BALDWIN-SEARCH-PREREGISTRATION.edn
  src/mmca/baldwin_preregistration.clj
  src/mmca/baldwin_selection.clj
  src/mmca/baldwin_search_smoke.clj
  scripts/baldwin_selection.clj
  scripts/baldwin_search_smoke.clj
  scripts/baldwin_search_smoke.sh
)
git cat-file -e "$REVISION^{commit}"
git diff --quiet "$REVISION" -- "${SCOPE[@]}" || {
  echo "smoke implementation differs from requested revision" >&2
  exit 1
}
[[ -z $(git ls-files --others --exclude-standard -- "${SCOPE[@]}") ]] || {
  echo "smoke implementation contains untracked source" >&2
  exit 1
}

readonly FIXED_P0=$(clojure -M -e \
  "(require '[mmca.baldwin-preregistration :as p]) (print (:fixed-p0 (p/read-registration \"$REGISTRATION\")))")

echo "source gates"
clj-kondo --lint src/mmca/baldwin_selection.clj \
  src/mmca/baldwin_preregistration.clj src/mmca/baldwin_search_smoke.clj \
  scripts/baldwin_selection.clj scripts/baldwin_search_smoke.clj \
  >"$OUT/clj-kondo.log" 2>&1
clojure -M:test -m mmca.test-runner >"$OUT/tests.log" 2>&1
clojure -M scripts/hinton_nowlan_positive.clj >"$OUT/positive-control.tsv"

echo "revision-bound preflight"
timeout 15m clojure -M scripts/baldwin_selection.clj \
  --preflight-only 1 --preflight-certificate "$OUT/preflight.edn" \
  --revision "$REVISION" --seeds "$SEEDS" --sites "$SITES" \
  >"$OUT/preflight.stdout" 2>"$OUT/preflight.stderr"

run_arm() {
  local arm=$1 mutation=$2 p0=$3 neutral=$4
  local p0_args=()
  if [[ $p0 == fixed ]]; then
    p0_args=(--fixed-p0 "$FIXED_P0")
  fi
  echo "smoke arm: $arm"
  timeout 15m clojure -M scripts/baldwin_selection.clj \
    --mode hold-only --mutation-mode "$mutation" --p0-mode "$p0" \
    "${p0_args[@]}" --neutral "$neutral" --c 0.05 \
    --gens "$GENERATIONS" --pop "$POPULATION" --seeds "$SEEDS" --sites "$SITES" \
    --field-rate 0.02 --warmup 0 --hgt 0 --evolution-seed "$EVOLUTION_SEED" \
    --revision "$REVISION" --preflight-certificate "$OUT/preflight.edn" \
    --record "$OUT/$arm.edn" --manifest "$OUT/$arm.manifest.edn" \
    >"$OUT/$arm.tsv" 2>"$OUT/$arm.stderr"
  clojure -M scripts/validate_baldwin_run.clj \
    "$OUT/$arm.tsv" "$OUT/$arm.edn" hold-only \
    "$GENERATIONS" "$POPULATION" >"$OUT/$arm.validation.edn"
}

run_arm neutral independent variable 1
run_arm independent-variable independent variable 0
run_arm coupled-variable coupled variable 0
run_arm independent-fixed independent fixed 0
run_arm coupled-fixed coupled fixed 0

echo "building fail-closed receipt"
clojure -M scripts/baldwin_search_smoke.clj \
  "$REGISTRATION" "$REVISION" "$OUT" "$OUT/smoke.edn" \
  >"$OUT/smoke.stdout"
clojure -M scripts/validate_baldwin_preregistration.clj \
  "$REGISTRATION" "$OUT/smoke.edn" >"$OUT/preregistration.validation.edn"

echo "PASS: smoke receipt $OUT/smoke.edn"
