#!/usr/bin/env bash
set -euo pipefail

readonly REPO=/home/joe/code/mmca-clj
readonly REGISTRATION=$REPO/holes/BALDWIN-GUIDANCE-PREREGISTRATION.edn
readonly LEAN_REPO=/home/joe/code/mathlib4
readonly OUT=${1:?usage: baldwin_guidance_smoke.sh OUTPUT_DIRECTORY}
readonly REVISION=$(git -C "$REPO" rev-parse HEAD)
readonly LEAN_REVISION=$(git -C "$LEAN_REPO" rev-parse HEAD)
readonly PAREN_CHECK=/home/joe/code/futon4/dev/check-parens.el

mkdir -p "$OUT/a" "$OUT/b"

cd "$REPO"

clojure -M scripts/validate_baldwin_guidance_preregistration.clj \
  "$REGISTRATION" >"$OUT/registration.validation.edn"

clj-kondo --lint \
  src/mmca/baldwin_selection.clj \
  src/mmca/baldwin_guidance.clj \
  src/mmca/baldwin_guidance_preregistration.clj \
  scripts/analyze_baldwin_guidance.clj \
  scripts/baldwin_guidance_smoke_receipt.clj \
  scripts/validate_baldwin_guidance_preregistration.clj \
  test/mmca/baldwin_selection_test.clj \
  test/mmca/baldwin_guidance_test.clj >"$OUT/clj-kondo.log"

emacs -Q --batch -l "$PAREN_CHECK" \
  --eval "(arxana-check-parens-cli)" -- --no-defaults \
  src/mmca/baldwin_selection.clj \
  src/mmca/baldwin_guidance.clj \
  src/mmca/baldwin_guidance_preregistration.clj \
  scripts/analyze_baldwin_guidance.clj \
  scripts/baldwin_guidance_smoke_receipt.clj \
  scripts/validate_baldwin_guidance_preregistration.clj \
  test/mmca/baldwin_selection_test.clj \
  test/mmca/baldwin_guidance_test.clj >"$OUT/check-parens.log"

clojure -M:test >"$OUT/tests.log" 2>&1

# The preflight is revision- and task-bound, not run-bound. Run it once and
# require both deterministic repetitions to consume the same certificate.
timeout 20m clojure -M scripts/baldwin_selection.clj \
  --mode guidance-field --learning-budget 120 \
  --gens 2 --pop 4 --seeds 3 --sites 4 --seed-offset 0 \
  --evolution-seed 20260802 --field-rate 0.02 \
  --c 0 --warmup 0 --hgt 0 --neutral 1 \
  --revision "$REVISION" --preflight-only 1 \
  --preflight-certificate "$OUT/preflight.edn" \
  >"$OUT/preflight.stdout" 2>"$OUT/preflight.stderr"

run_once() {
  local target=$1
  clojure -M scripts/hinton_nowlan_positive.clj >"$target/positive-control.tsv"

  run_arm() {
    local arm=$1
    local budget=$2
    local neutral=$3
    timeout 20m clojure -M scripts/baldwin_selection.clj \
      --mode guidance-field --learning-budget "$budget" \
      --gens 2 --pop 4 --seeds 3 --sites 4 --seed-offset 0 \
      --evolution-seed 20260802 --field-rate 0.02 \
      --c 0 --warmup 0 --hgt 0 --neutral "$neutral" \
      --revision "$REVISION" \
      --preflight-certificate "$OUT/preflight.edn" \
      --record "$target/$arm.edn" --manifest "$target/$arm.manifest.edn" \
      >"$target/$arm.tsv" 2>"$target/$arm.stderr"
  }

  run_arm mutation-only 120 1
  run_arm no-learning-evolution 0 0
  run_arm learning-evolution 120 0

  clojure -M scripts/analyze_baldwin_guidance.clj \
    "$REGISTRATION" "$target" "$target/result.edn" \
    >"$target/analysis.stdout"
}

run_once "$OUT/a"
run_once "$OUT/b"

for artifact in positive-control.tsv \
  mutation-only.edn mutation-only.manifest.edn mutation-only.tsv \
  no-learning-evolution.edn no-learning-evolution.manifest.edn \
  no-learning-evolution.tsv learning-evolution.edn \
  learning-evolution.manifest.edn learning-evolution.tsv result.edn; do
  cmp "$OUT/a/$artifact" "$OUT/b/$artifact"
done

clojure -M scripts/baldwin_guidance_smoke_receipt.clj \
  "$REGISTRATION" "$OUT/a/result.edn" "$REVISION" "$LEAN_REVISION" \
  "$OUT/smoke.edn" \
  "$OUT/clj-kondo.log" "$OUT/check-parens.log" "$OUT/tests.log" \
  "$OUT/a/positive-control.tsv" "$OUT/preflight.edn" \
  "$OUT/a/mutation-only.edn" "$OUT/a/no-learning-evolution.edn" \
  "$OUT/a/learning-evolution.edn" "$OUT/a/result.edn" \
  >"$OUT/smoke.stdout"

clojure -M scripts/validate_baldwin_guidance_preregistration.clj \
  "$REGISTRATION" "$OUT/smoke.edn" >"$OUT/smoke.validation.edn"

sha256sum "$OUT"/preflight.edn "$OUT"/a/*.edn "$OUT"/a/*.tsv "$OUT/smoke.edn" \
  >"$OUT/CHECKSUMS.sha256"

cat "$OUT/smoke.edn"
