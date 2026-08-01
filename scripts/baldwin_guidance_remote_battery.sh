#!/bin/bash
# Run the preregistered Baldwin guidance pilot sequentially on one worker.
set -Eeuo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: $0 RUN_ID IMPLEMENTATION_REVISION AUTHORIZATION_REVISION EVOLUTION_SEED" >&2
  exit 64
fi

readonly RUN_ID=$1 IMPLEMENTATION_REVISION=$2 AUTHORIZATION_REVISION=$3
readonly EVOLUTION_SEED=$4
readonly REPO=/root/mmca-clj
readonly BUNDLE=/root/baldwin-guidance-launch
readonly REGISTRATION=$BUNDLE/registration.edn
readonly SMOKE=$BUNDLE/smoke.edn
readonly AUTHORIZATION=$BUNDLE/authorization.edn
readonly OUT=/root/baldwin-runs/$RUN_ID
readonly STARTED=/root/${RUN_ID}.started
readonly DONE=/root/${RUN_ID}.done

[[ $RUN_ID =~ ^[A-Za-z0-9._-]+$ ]] || exit 64
[[ $IMPLEMENTATION_REVISION =~ ^[0-9a-f]{40}$ ]] || exit 64
[[ $AUTHORIZATION_REVISION =~ ^[0-9a-f]{40}$ ]] || exit 64
[[ $EVOLUTION_SEED =~ ^[0-9]+$ ]] || exit 64
[[ -f $REGISTRATION && -f $SMOKE && -f $AUTHORIZATION ]] || exit 1
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
[[ $(git rev-parse HEAD) == "$IMPLEMENTATION_REVISION" ]]
[[ -z $(git status --porcelain --untracked-files=all) ]]

echo "verifying immutable launch authorization"
clojure -M "$BUNDLE/check-launch-bundle.clj" \
  "$REGISTRATION" "$SMOKE" "$AUTHORIZATION" "$IMPLEMENTATION_REVISION" \
  "$AUTHORIZATION_REVISION" "$EVOLUTION_SEED" "$OUT/launch-authorization.validation.edn"
cp "$REGISTRATION" "$OUT/registration.edn"
cp "$SMOKE" "$OUT/smoke.edn"
cp "$AUTHORIZATION" "$OUT/authorization.edn"

IFS=$'\t' read -r MODE GENERATIONS POPULATION SEEDS SITES COST HGT FIELD_RATE \
  WARMUP P0_MODE MUTATION_MODE SEED_OFFSET REGISTERED_SEED ARM_TIMEOUT < <(
  REGISTRATION_PATH="$REGISTRATION" clojure -M -e '
    (require (quote [clojure.edn :as edn]) (quote [clojure.string :as str]))
    (let [r (edn/read-string (slurp (System/getenv "REGISTRATION_PATH")))
          x (:production-protocol r)]
      (println (str/join "\t"
        [(:mode x) (:generations x) (:population x) (:evaluation-seeds x)
         (:evaluation-sites x) (:cost x) (if (:hgt x) 1 0) (:field-rate x)
         (:warmup x) (name (:p0-mode x)) (name (:mutation-mode x))
         (:seed-offset x) (:pilot-evolution-seed x) (:arm-timeout-minutes x)])))')
[[ $EVOLUTION_SEED == "$REGISTERED_SEED" ]]
[[ $MUTATION_MODE == legacy ]]

echo "running source gates"
clj-kondo --lint src/mmca/baldwin_selection.clj \
  src/mmca/baldwin_guidance.clj src/mmca/baldwin_guidance_preregistration.clj \
  scripts/baldwin_selection.clj scripts/analyze_baldwin_guidance.clj \
  scripts/hinton_nowlan_positive.clj test/mmca/baldwin_selection_test.clj \
  test/mmca/baldwin_guidance_test.clj >"$OUT/clj-kondo.log" 2>&1
clojure -M:test >"$OUT/tests.log" 2>&1
clojure -M scripts/hinton_nowlan_positive.clj >"$OUT/positive-control.tsv"

echo "creating revision-bound preflight certificate"
clojure -M scripts/baldwin_selection.clj \
  --mode "$MODE" --learning-budget 120 --gens "$GENERATIONS" --pop "$POPULATION" \
  --seeds "$SEEDS" --sites "$SITES" --seed-offset "$SEED_OFFSET" \
  --evolution-seed "$EVOLUTION_SEED" --field-rate "$FIELD_RATE" \
  --c "$COST" --warmup "$WARMUP" --hgt "$HGT" --neutral 1 \
  --revision "$IMPLEMENTATION_REVISION" --preflight-only 1 \
  --preflight-certificate "$OUT/preflight.edn" \
  >"$OUT/preflight.stdout" 2>"$OUT/preflight.stderr"

common_args=(
  --mode "$MODE" --gens "$GENERATIONS" --pop "$POPULATION"
  --seeds "$SEEDS" --sites "$SITES" --seed-offset "$SEED_OFFSET"
  --field-rate "$FIELD_RATE" --warmup "$WARMUP" --c "$COST" --hgt "$HGT"
  --p0-mode "$P0_MODE" --evolution-seed "$EVOLUTION_SEED"
  --revision "$IMPLEMENTATION_REVISION"
  --preflight-certificate "$OUT/preflight.edn"
)

verify_manifest() {
  local path=$1 budget=$2 neutral=$3 output=$4
  clojure -M "$BUNDLE/validate-arm.clj" \
    "$path" "$budget" "$neutral" "$GENERATIONS" "$POPULATION" \
    "$EVOLUTION_SEED" "$FIELD_RATE" "$output"
}

run_arm() {
  local arm=$1 budget=$2 neutral=$3
  echo "running $arm"
  /usr/bin/time -f "$arm\t%e\t%M" -a -o "$OUT/timings.tsv" \
    timeout "${ARM_TIMEOUT}m" clojure -M scripts/baldwin_selection.clj \
      "${common_args[@]}" --learning-budget "$budget" --neutral "$neutral" \
      --record "$OUT/$arm.edn" --manifest "$OUT/$arm.manifest.edn" \
      >"$OUT/$arm.tsv" 2>"$OUT/$arm.stderr"
  verify_manifest "$OUT/$arm.manifest.edn" "$budget" "$neutral" \
    "$OUT/$arm.validation.edn"
}

run_arm mutation-only 120 1
run_arm no-learning-evolution 0 0
run_arm learning-evolution 120 0

clojure -M scripts/analyze_baldwin_guidance.clj \
  "$REGISTRATION" "$OUT" "$OUT/result.edn" >"$OUT/analysis.stdout"

(cd "$OUT" && find . -maxdepth 1 -type f \
  ! -name battery.log ! -name CHECKSUMS.remote.sha256 \
  -printf '%f\0' | sort -z | xargs -0 sha256sum >CHECKSUMS.remote.sha256)
printf 'success:%s\n' "$RUN_ID" >"$DONE"
trap - EXIT
echo "battery complete"
