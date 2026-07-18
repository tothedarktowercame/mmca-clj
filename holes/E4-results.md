# E4 — Directed predictive information (corrected: authentic river + matched ablation)

Reproduce: `clojure -M -m mmca.experiments.directed-predictive-info`.
Determinism: `clojure -M:test` (`mmca.directed-predictive-info-test`).
Config: seeds 0–7, W=48, T=64, burn-in=16, 4 seed-held-out folds, Jeffreys α=0.5,
radius-1 past lightcones. Values are held-out baseline log loss minus joint log
loss, **bits per site-step** — a held-out predictive-log-loss gain, *not*
unrestricted conditional mutual information and not by itself causality.

**Correction (Codex review):** this now uses the authentic river `c/run-river`
(was the reconstruction) and a MATCHED feedback-off control
`c/run-river-ablated` (was the base engine, which differed in RNG + construction
+ fallback, so it could not isolate feedback).

| quantity | value | note |
|---|---:|---|
| base I(G⁻;X⁺\|X⁻) | 0.1508 | expression, feedforward |
| base I(X⁻;G⁺\|G⁻) | **−0.0265** | null CHECK, PASS (\|·\|<0.05) |
| river I(G⁻;X⁺\|X⁻) | 0.7589 | strong expression |
| river I(X⁻;G⁺\|G⁻) | 0.3005 | raw river reverse gain |
| **river-ablated (matched) I(X⁻;G⁺\|G⁻)** | **0.0913** | non-feedback shared structure |
| **ISOLATED X→G feedback (river − matched)** | **0.2092** | the clean causal estimate |
| river rule-label shuffle G→X | −0.0511 | surrogate, destroys signal |
| river spacetime-G shuffle G→X | −0.0656 | surrogate |
| Rule 105/204 pooled λ=1/2 control G→X | 0.5118 | neighbourhood-semantics scalpel |

## Reading

On the authentic river the phenotype improves genotype-future prediction, but
the **matched ablation already accounts for 0.091 bits of that** — so the old
base-engine control (which read ~0) would have over-attributed the whole 0.30 to
feedback. Differencing against the matched control gives an isolated feedback of
**0.209 bits/site-step**, RNG/construction/fallback held identical. The λ=1/2
105/204 pair stays sharply distinguishable (0.512) despite equal λ, confirming
the estimator sees neighbourhood semantics.

Remaining (Codex #5): report per-seed/fold intervals or a permutation
distribution alongside these point estimates.
