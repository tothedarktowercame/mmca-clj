# E5 — Joint local causal states (result, correction round)

Reproduce: `clojure -M -m mmca.experiments.local-causal-states`

Determinism gate: `clojure -M:test` (see
`mmca.local-causal-states-test`). Run time ~22 min.

Config: simulation seeds 0–5, W=40, T=56, burn-in=12, three seed-held-out
folds, Jeffreys smoothing α=0.5. The declared model grid is past-cone depth
`{1,2}` × predictive-distribution tolerance `{0.1,0.2,0.4}`. Each row below
reports the pair selected by minimum held-out future log loss. The future cone
is the centre cell's next joint `(G,X)` value, represented by eight rule bits
and one phenotype bit; loss is the mean held-out bits per predicted bit.

## Engine definitions (correction round)

| engine | construction | feedback |
|---|---|---|
| `:base` | feedforward propagator (positional writing) | none |
| `:river` | **authentic paper river** (`c/run-river`; constant-zero/Java seed) | live X→G |
| `:river-ablated` | matched control (`c/run-river-ablated`; identical seed/tape/construction) | **X→G cut** (frozen p0 phenotype) |

The river-minus-ablated contrast isolates the causal effect of phenotype→genotype
feedback. Everything else (Java seed, RNG tape, initial state, constant-zero
quad-4cand construction, fallback) is held identical. The ablated genotype step
reads the **frozen initial phenotype p0** (captured once before the loop) for all
four context bits — not the live evolving phenotype.

## Selected models

| engine | past layer | depth | tolerance | held-out loss | states | candidate structures | lifetime mean / max | mean abs velocity | signed velocity |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| feedforward base | X | 2 | 0.10 | 1.756009 | 61 | 17 | 28.65 / 44 | 0.066 | +0.036 |
| feedforward base | G | 1 | 0.20 | 1.681537 | 508 | 19 | 16.68 / 44 | 0.107 | +0.031 |
| feedforward base | joint G,X | 1 | 0.20 | **1.616961** | 597 | 22 | 15.77 / 44 | 0.132 | +0.005 |
| river | X | 2 | 0.10 | 1.585984 | 198 | 127 | 4.61 / 24 | 0.579 | −0.041 |
| river | G | 2 | 0.10 | **0.931319** | 8593 | 101 | 5.24 / 30 | 0.465 | +0.059 |
| river | joint G,X | 2 | 0.10 | 1.015996 | 7743 | 90 | 5.81 / 30 | 0.484 | +0.008 |
| river-ablated | X | 2 | 0.10 | 1.552845 | 147 | 168 | 3.64 / 15 | 0.560 | +0.175 |
| river-ablated | G | 2 | 0.10 | **0.863656** | 7033 | 135 | 6.36 / 43 | 0.263 | +0.008 |
| river-ablated | joint G,X | 2 | 0.10 | **0.867252** | 6966 | 125 | 6.66 / 32 | 0.250 | −0.028 |

## Joint gain vs best marginal

| engine | joint gain (bits/predicted bit) |
|---|---:|
| feedforward base | **+0.064576** |
| river | **−0.084676** |
| river-ablated | **−0.003596** |

In the feedforward base, the joint layer predicts the nine-bit future better than
either marginal alone. On the authentic river, the genotype layer alone is a
stronger predictor than the joint layer (joint gain is negative), indicating that
adding the phenotype bit does not help once the full genotype context is
available. The ablated control shows near-zero joint gain.

## ISOLATED feedback contrast (river − matched ablation), joint layer

This is the key correction-round result: comparing the authentic river against
its matched feedback-off control (same seed/tape/construction, frozen p0 — the
true X→G cut).

| metric | river | ablated | delta mean | seed interval |
|---|---:|---:|---:|---|
| structure count | 90 | 125 | **+3.00** | [−4, +10] |
| state count | 7743 | 6966 | **+111.67** | [−148, +247] |
| joint held-out loss | 1.015996 | 0.867252 | **−0.148744** bits/bit | — |

### Per-seed structure deltas (joint layer)

| seed | river structures | ablated structures | delta | states delta |
|---:|---:|---:|---:|---:|
| 0 | 30 | 20 | +10 | +146 |
| 1 | 23 | 21 | +2 | +247 |
| 2 | 26 | 21 | +5 | +225 |
| 3 | 16 | 17 | −1 | −6 |
| 4 | 24 | 18 | +6 | +206 |
| 5 | 11 | 15 | −4 | −148 |

## Reading

With the corrected ablation (frozen p0 for all context bits, a true X→G cut), the
feedback contrast shows that the live phenotype→genotype feedback does not create
extra coherent causal-state structures. The ablated control (no feedback) has a
better joint held-out loss (0.867 vs 1.016) and comparable structure counts (125
vs 90). Per-seed structure deltas range [−4, +10] with 4 of 6 seeds showing the
river with *more* structures — but the predictive quality is worse.

The feedforward base result is unchanged and stands: joint reconstruction
predicts the nine-bit local future +0.0646 bits/predicted-bit better than either
marginal alone, confirming a coupled predictive state description even without
feedback. On the authentic river, the genotype layer alone is already a stronger
predictor than the joint layer, suggesting that the river's feedback enriches the
genotype layer's information content enough that the phenotype bit adds no
incremental predictive value.
