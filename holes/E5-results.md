# E5 — Joint local causal states (result, correction round)

Reproduce: `clojure -M -m mmca.experiments.local-causal-states`

Determinism gate: `clojure -M:test` (see
`mmca.local-causal-states-test`). Run time ~23 min.

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
| `:river-ablated` | matched control (`c/run-river-ablated`; identical seed/tape/construction) | **X→G cut** (frozen phenotype) |

The river-minus-ablated contrast isolates the causal effect of phenotype→genotype
feedback. Everything else (Java seed, RNG tape, initial state, constant-zero
quad-4cand construction, fallback) is held identical.

## Selected models

| engine | past layer | depth | tolerance | held-out loss | states | candidate structures | lifetime mean / max | mean abs velocity | signed velocity |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| feedforward base | X | 2 | 0.10 | 1.756009 | 61 | 17 | 28.65 / 44 | 0.066 | +0.036 |
| feedforward base | G | 1 | 0.20 | 1.681537 | 508 | 19 | 16.68 / 44 | 0.107 | +0.031 |
| feedforward base | joint G,X | 1 | 0.20 | **1.616961** | 597 | 22 | 15.77 / 44 | 0.132 | +0.005 |
| river | X | 2 | 0.10 | 1.585984 | 198 | 127 | 4.61 / 24 | 0.579 | −0.041 |
| river | G | 2 | 0.10 | **0.931319** | 8593 | 101 | 5.24 / 30 | 0.465 | +0.059 |
| river | joint G,X | 2 | 0.10 | 1.015996 | 7743 | 90 | 5.81 / 30 | 0.484 | +0.008 |
| river-ablated | X | 2 | 0.10 | 1.384761 | 115 | 159 | 3.58 / 15 | 0.646 | +0.007 |
| river-ablated | G | 2 | 0.10 | **0.953672** | 6416 | 151 | 4.33 / 16 | 0.559 | −0.010 |
| river-ablated | joint G,X | 2 | 0.10 | **0.930432** | 6734 | 144 | 4.15 / 17 | 0.566 | +0.036 |

## Joint gain vs best marginal

| engine | joint gain (bits/predicted bit) |
|---|---:|
| feedforward base | **+0.064576** |
| river | **−0.084676** |
| river-ablated | **+0.023240** |

In the feedforward base, the joint layer predicts the nine-bit future better than
either marginal alone. On the authentic river, the genotype layer alone is a
stronger predictor than the joint layer (joint gain is negative), indicating that
adding the phenotype bit does not help once the full genotype context is
available. The ablated control shows a small positive joint gain.

## ISOLATED feedback contrast (river − matched ablation), joint layer

This is the key correction-round result: comparing the authentic river against
its matched feedback-off control (same seed/tape/construction, only X→G cut).

| metric | river | ablated | delta mean | seed interval |
|---|---:|---:|---:|---|
| structure count | 90 | 144 | **−5.00** | [−9, +5] |
| state count | 7743 | 6734 | **−93.50** | [−210, −43] |
| joint held-out loss | 1.015996 | 0.930432 | **−0.085563** bits/bit | — |

### Per-seed structure deltas (joint layer)

| seed | river structures | ablated structures | delta | states delta |
|---:|---:|---:|---:|---:|
| 0 | 30 | 25 | +5 | −48 |
| 1 | 23 | 30 | −7 | −43 |
| 2 | 26 | 31 | −5 | −128 |
| 3 | 16 | 25 | −9 | −46 |
| 4 | 24 | 29 | −5 | −86 |
| 5 | 11 | 20 | −9 | −210 |

## Reading

The matched ablation contrast reveals a surprising result: cutting the X→G
feedback edge **increases** the number of candidate structures (ablated 144 vs
river 90) and slightly **improves** the joint held-out loss (ablated 0.930 vs
river 1.016). This means the live phenotype→genotype feedback does not create
extra coherent causal-state structures — if anything, it reduces them and makes
the future slightly harder to predict from the joint layer. The per-seed
intervals confirm this is not an artifact of a single seed: structure deltas
range from −9 to +5, with 5 of 6 seeds negative.

This stands in contrast to the old E5 reading (which found 141 vs 22 structures
on the wrong river). On the authentic paper river with the correct matched
control, the feedback does not drive the joint>marginal predictive gain or extra
coherent structures. The feedforward base result stands unchanged: the joint
reconstruction predicts the nine-bit local future 0.0646 bits/predicted-bit
better than either marginal alone, confirming a coupled predictive state
description even without feedback.
