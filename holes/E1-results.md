# E1 — Paired-perturbation response (result)

Reproduce: `clojure -M -m mmca.experiments.paired-perturbation`
Determinism/null gates: `clojure -M:test` (see `mmca.paired-perturbation-test`).
Config: writing = offset+4 `[4 5 6 7 0 1 2 3]` (all-even, collapses), feedforward
base `run-propagator`, seeds 0–39, W=80, T=120, fork t*=40, site = centre.

## Four-channel response matrix R_{B←A}(Δt), mean affected mass

Flip one **phenotype** bit:

| Δt | X→X | X→G |
|----|-----|-----|
| 1  | 0.45 | 0.00 |
| 5  | 0.38 | 0.00 |
| 20 | 0.33 | 0.00 |
| 60 | 0.33 | 0.00 |

Flip one **rule** bit:

| Δt | G→G | G→X |
|----|-----|-----|
| 1  | 0.10 | 0.48 |
| 5  | 0.05 | 0.15 |
| 20 | 0.00 | 0.05 |
| 60 | 0.00 | 0.05 |

**CHECK: total X→G across Δt = 0.0000 → PASS** (feedforward null holds exactly —
perturbing the phenotype never touches the genotype on the base engine, by
construction; this is a correctness check on the harness, not a measurement).

## Live/dead λ=1/2 scalpel — inject Rule 105 (live) vs Rule 204 (dead), G→X mass

| rule | Δt=5 | Δt=20 | Δt=60 |
|------|------|-------|-------|
| 105 (live)  | 0.20 | 0.08 | 0.08 |
| 204 (dead)  | 0.00 | 0.03 | 0.03 |

Same propagator class (both balanced fixed points), opposite neighbourhood
semantics: the live rule injects sustained phenotype response, the dead one does
not. This is the built-in control Codex asked for.

## Reading

On the collapsing operator offset+4: the phenotype sustains propagation (X→X
holds at ~0.33), the genotype absorbs its own perturbations (G→G → 0), and the
genotype expresses causally into the phenotype (G→X). The feedforward base has
X→G ≡ 0 exactly; the **river** engine (X→G live) is the treatment whose response
matrix, minus this baseline, isolates the phenotype→genotype feedback — the next
step, and the reason the base result is worth pinning first.
