# Amended six-arm masking smoke

Status: **mechanically passed; scientific launch HOLD after readout review**.

The trace producer and smoke apparatus passed.  Both repetitions evaluated the
complete six-arm schedule (1,152 raw units per arm), were byte-identical, and
passed the source, treatment, schedule, aggregation, positive-control, artifact,
checksum, and deadline gates.  Confirmation environment seeds `101–108` remain
unspent.

The earlier launch authorization is superseded because its primary `fitness`
readout contains a deterministic capacity-refund confound:

```
fitness = band - 0.05 * dependence
```

Holding one locus changes dependence from `1.0` to `0.9875`, granting every held
row a constant `+0.000625` fitness offset independent of behavior.  That identity,
not improved function, produces both registered 16–0–0 held/plastic contrasts.

## Zero-cost reanalysis

The exact registered aggregation was repeated over `band` and `reach`:

| contrast | fitness | band | reach |
|---|---:|---:|---:|
| held-good vs plastic-good | 16–0–0 | 6–7–3 | 6–10–0 |
| held-current vs plastic-current | 16–0–0 | 6–7–3 | 6–9–1 |
| held-good vs held-current | 9–4–3 | 9–4–3 | 10–5–1 |
| held-good vs held-bad | 12–3–1 | 12–3–1 | 15–1–0 |
| plastic-good vs plastic-current | 11–4–1 | 11–4–1 | 11–5–0 |
| discovery held-good vs novel held-good | 7–7–2 | 7–7–2 | 5–11–0 |

The endpoint map retains discriminative value: all 1,062 fitness-selectable
endpoints also have `band >= base-band`, and the cost is constant when ranking
held candidates.  But the clean held-good/current and held-good/bad comparisons
miss the registered familywise bar on the task readout.  The balanced novel-tape
contrast is consequently vacuous: there is no established held-good advantage
whose degradation it could test.

The panel's stratified signal is exploratory rather than a new confirmation
claim.  On band, held-good minus held-current is strongest in the middle-dense
and middle-sparse strata and approximately null in late-sparse.  This smoke may
inform a new prospective positional/readout design, but it cannot retrospectively
authorize one.

## Required before paid compute

1. Register a behavior-level primary readout (`band` is the existing task score;
   `reach` remains diagnostic because larger reach is not monotonically better).
2. If a plasticity cost is scientifically required, instrument realized rewrite
   work and preregister its scale; do not reuse capacity as a proxy for effort.
3. Bind a new trace producer and smoke receipt to that amendment.
4. Reconsider the paid confirmation only after the new fail-closed validator
   passes.  Do not consume environments `101–108` meanwhile.

Evidence:

- Readout audit: `readout-reanalysis.edn`
- Raw/result/manifest hashes remain recorded in `smoke-receipt.edn`.
- Clojure implementation: `fc8bfcaa3fd26ed78cec90b7183592af156925f2`
- Lean amendment reviewed: `1445d9bd4de70b532cccbd06927285588f96fc1d`
