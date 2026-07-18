# E4 — Directed conditional predictive information (result)

Reproduce: `clojure -M -m mmca.experiments.directed-predictive-info`

Determinism gate: `clojure -M:test` (see `mmca.directed-predictive-info-test`).

Config: simulation seeds 0–7, surrogate seed `e4-surrogate-1729`, W=48,
T=64, burn-in=16, four seed-held-out folds, Jeffreys smoothing α=0.5.

Predictors use one-step radius-1 past lightcones. Values are held-out baseline
log loss minus joint log loss, in bits per site-step. For G⁺, the eight rule
bits are predicted separately and their losses summed. Negative surrogate/null
values are retained rather than clipped: they expose finite-sample estimation
cost instead of manufacturing nonnegative information.

| condition | directed quantity | baseline loss | joint loss | improvement |
|---|---:|---:|---:|---:|
| feedforward base | I(G⁻;X⁺\|X⁻) | 0.221621 | 0.070785 | **0.150836** |
| feedforward base (correctness check) | I(X⁻;G⁺\|G⁻) | 0.339171 | 0.365636 | **−0.026465** |
| river treatment | I(G⁻;X⁺\|X⁻) | 0.899665 | 0.113493 | **0.786172** |
| river treatment | I(X⁻;G⁺\|G⁻) | 6.842218 | 6.720639 | **0.121578** |
| river, rule-label shuffle | G→X surrogate | 0.899665 | 0.956901 | −0.057235 |
| river, spatial/temporal G shuffle | G→X surrogate | 0.899665 | 0.980577 | −0.080912 |
| feedback-breaking (feedforward) | X→G surrogate | 0.339171 | 0.365636 | −0.026465 |
| pooled Rule 105/204 λ=1/2 control | I(G⁻;X⁺\|X⁻) | 0.512204 | 0.000443 | **0.511761** |

**CHECK: feedforward I(X⁻;G⁺|G⁻) has |value| < 0.05 → PASS.**

## Reading

The directed held-out result distinguishes ordinary expression from feedback:
G⁻ improves X⁺ prediction in both engines, strongly so in the river, but X⁻
improves G⁺ only in the phenotype-reading river (`+0.121578` bits/site-step).
The feedforward reverse estimate is a small negative generalization penalty
(`−0.026465`), correctly reading as zero within the predeclared ±0.05 harness
check rather than as reverse influence. Destroying the river’s local G–X
alignment by rule-label or spacetime shuffle removes its positive G→X gain,
and replacing the river with the feedback-breaking base removes X→G. Finally,
the balanced Rule 105/204 pair remains sharply distinguishable predictively
despite equal λ=1/2, confirming that the estimator sees neighbourhood semantics
rather than merely rule-table balance.
