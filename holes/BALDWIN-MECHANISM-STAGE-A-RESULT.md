# Baldwin mechanism Stage A result

**Observed 2026-08-01 after preregistration; no evolutionary arms launched.**

The four-cell stationarity grid in
`data/baldwin-diagnostics/target-stationarity-306b705/result.edn` gave:

| initial phenotype | rewrite tape | mean pairwise field agreement |
|---|---|---:|
| variable | variable | 0.02143 |
| fixed | variable | 0.01920 |
| variable | shared | 0.12946 |
| fixed | shared | 1.00000 |

The fully fixed apparatus control passed exactly. Under the preregistered Lean
rule the outcome is `bothSourcesDestabilize`: sharing rewrite randomness raises
agreement, but neither one-factor repair reaches the 0.25 materially encodable
threshold. Fixing `p0` alone therefore could not have repaired the target.

The follow-up 16-context diagnostic in
`data/baldwin-diagnostics/context-stationarity-327a3bd/result.edn` also found a
diffuse target. Only 4 of 16 contexts shared a modal rule across all eight seeds,
and mean modal share was 0.1432 with variable `p0` and 0.1413 with fixed `p0`.
This does not admit the context-table evolutionary branch either.

Consequently the next paid box is diagnostic, not evolutionary. It measures the
paired selection coefficient of inherited alleles under active rewriting and
completes the 80 by 256 assimilability map. These distinguish a neutral inherited
coordinate from a representation which contains useful fixed endpoints but no
accessible gradient. Only their result can admit a usage-cost or prepare-then-fix
evolution experiment.
