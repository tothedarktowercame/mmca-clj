# River X→G feedback via matched ablation (correction round)

Reproduce: `clojure -M -m mmca.experiments.river-feedback`. Determinism:
`clojure -M:test` (see `mmca.river-feedback-test`). seeds 1–20, W=80, T=120.

Isolates the ORIGINAL paper river's feedback the right way. `run-original-paper-river`
vs `run-original-paper-river-ablated` share the exact Java seed, initial state,
RNG tape, and constant-zero quad-4cand construction; they differ ONLY in whether
the genotype step reads the live or the frozen phenotype. So the genotype
divergence is the *causal* effect of the live X→G feedback — not confounded with
RNG/construction/fallback differences (which was the flaw in E4's base-engine
"feedback-breaking" control).

| t | genotype divergence (mean) | seed-range |
|---|---|---|
| 1   | 0.498 | [0.400, 0.588] |
| 5   | 0.895 | [0.838, 0.950] |
| 20  | 0.914 | [0.825, 0.963] |
| 60  | 0.921 | [0.825, 0.975] |
| 119 | 0.925 | [0.863, 0.975] |

**Reading.** On the authentic river the phenotype→genotype feedback is not subtle:
within ~5 steps ~90% of genotype cells differ from the matched no-feedback control,
saturating near 0.92. This is a much stronger and cleaner statement than the
earlier `+0.122 bits` (which was measured on the wrong river, `run-river`, against
an unmatched base-engine control). The matched ablation is the control the whole
correction round (E4/E5/E6 reruns) should adopt.
