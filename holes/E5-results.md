# E5 — Joint local causal states (result)

Reproduce: `clojure -M -m mmca.experiments.local-causal-states`

Determinism gate: `clojure -M:test` (see
`mmca.local-causal-states-test`).

Config: simulation seeds 0–5, W=40, T=56, burn-in=12, three seed-held-out
folds, Jeffreys smoothing α=0.5. The declared model grid is past-cone depth
`{1,2}` × predictive-distribution tolerance `{0.1,0.2,0.4}`. Each row below
reports the pair selected by minimum held-out future log loss. The future cone
is the centre cell's next joint `(G,X)` value, represented by eight rule bits
and one phenotype bit; loss is the mean held-out bits per predicted bit.

| engine | past layer | selected depth | tolerance | held-out loss | states | candidate structures | lifetime mean / max | mean absolute velocity | signed velocity |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| feedforward base | X | 2 | 0.10 | 1.756009 | 61 | 17 | 28.65 / 44 | 0.066 | +0.036 |
| feedforward base | G | 1 | 0.20 | 1.681537 | 508 | 19 | 16.68 / 44 | 0.107 | +0.031 |
| feedforward base | joint G,X | 1 | 0.20 | **1.616961** | 597 | 22 | 15.77 / 44 | 0.132 | +0.005 |
| river | X | 2 | 0.10 | 2.064415 | 167 | 135 | 5.57 / 26 | 0.460 | +0.041 |
| river | G | 1 | 0.10 | 1.358971 | 6591 | 120 | 5.43 / 23 | 0.516 | +0.087 |
| river | joint G,X | 1 | 0.10 | **1.285705** | 6693 | 141 | 4.82 / 23 | 0.527 | +0.025 |

Joint held-out gain over the better marginal predictor is **0.064576
bits/predicted bit** in the feedforward base and **0.073266 bits/predicted
bit** in the river. Candidate structures are connected components of states
outside the fixed 80% background mass, using 8-neighbour spacetime adjacency
and a predeclared minimum size of three points. Velocities are component
centroid displacement per time step. These are reproducible descriptive
candidates, not hand-labelled particles.

## Reading

The joint reconstruction predicts the nine-bit local future better than either
layer alone in both engines, with the advantage larger in the phenotype-reading
river (`0.073266` versus `0.064576` bits per future bit). The result therefore
supports a coupled predictive state description, but it is not a clean claim
that feedback alone creates the gain: even the feedforward system benefits
from observing both the expressed phenotype and its genotype. The structural
diagnostic does distinguish the regimes: the base has a few long-lived,
slow-moving rare-state components, whereas the river has many shorter and much
faster candidates. Since depth and state tolerance were fixed by held-out
likelihood before component counting, those contrasts are not chosen for the
most visually attractive decomposition.
