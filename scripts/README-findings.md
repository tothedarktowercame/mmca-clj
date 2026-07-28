# Provenance for the Supplement 1 findings

One row per box in Supplement 1 of *Rule-Rewriting Cellular Automata and the
Edge of Chaos*, giving the command that regenerates its numbers.

`scripts/reproduce_all.sh` is the "single command" the paper refers to. It
covers boxes 2–7 and nothing else. Boxes 1 and 8–12 need the commands below,
run separately. This file records that gap rather than papering over it.

All runs are seeded, so repeated runs of the same command give identical
numbers. Figure rendering requires the pinned plotting stack (`.venv`,
Matplotlib 3.11.1) — see the figure-pipeline note in `README.md`.

| Box | Finding | Command | In `reproduce_all.sh` |
|----|---------|---------|----------------------|
| 1  | The full permutation census | `clojure -M -m mmca.experiments.aliveness-census` | **no** |
| 2  | The rotation survey | `scripts/reproduce_all.sh` (`plot_fig4`, `plot_fig5`) | yes |
| 3  | A braid of two collapsing operators can sustain the field | `scripts/reproduce_all.sh` (`plot_braid`) | yes |
| 4  | The mixing fraction is a reproducible diversity control | `scripts/reproduce_all.sh` (`plot_knob`) | yes |
| 5  | The rotation split occurs with and without neighbour blending | `scripts/reproduce_all.sh` (`plot_fig3`, `plot_fig5`) | yes |
| 6  | No finite-size evidence of a critical point | `scripts/reproduce_all.sh` (`plot_eoc_phase`) | partial — see below |
| 7  | The river sustains structured phenotype | `scripts/reproduce_all.sh` (`plot_fig6`) | yes |
| 8  | Phenotype-to-genotype feedback roughly doubles causal reach | see `README-causal.md` | **no** |
| 9  | Causal reach on a scale calibrated against elementary rules | `clojure -M -i scripts/regime_placement.clj` (the `eca` rows) | **no** |
| 10 | The order parameter is the strength of the coupling | `clojure -M -i scripts/regime_placement.clj` then `.venv/bin/python scripts/plot_coupling_dial.py` | **no** |
| 11 | Coupling is a dial, not a switch | `clojure -M -i scripts/river_gain.clj` then `.venv/bin/python scripts/plot_gain_curves.py` | **no** |
| 12 | Sustained diversity does not determine causal reach | `scripts/diversity_dial*.clj` + `full_population_sweep.clj`, then `assemble_diversity_axis.py` and `plot_diversity_axis.py` | **no** |

## Known gaps

These are recorded because a reader who trusts the "single documented command"
sentence in the paper would otherwise hit them without warning.

- **Box 6 is not regenerated from scratch.** `plot_eoc_phase.py` reads the
  committed offset+1 finite-size scan in
  `holes/E2b-offset1-finite-size-results.md`. Re-running the scan itself is
  `clojure -M -m mmca.experiments.offset1-finite-size`, which is slow and is not
  part of `reproduce_all.sh`.

- **The Part II chain passes intermediate data through `/tmp`.** Six scripts
  read or write `/tmp/pert_summary.tsv`, `/tmp/pert_rows.tsv`,
  `/tmp/pert_seeds.tsv`, `/tmp/fp256.tsv`, `/tmp/mut.tsv` and
  `/tmp/diversity_dial{2,3}`. These are ephemeral and the ordering between
  producer and consumer is not enforced anywhere. `assemble_diversity_axis.py`
  in particular reads `/tmp/fp256.tsv` without any documented step that creates
  it; it comes from `full_population_sweep.clj`.

- **Box 12 has no single entry point.** The diversity axis is assembled from
  several `diversity_dial*.clj` variants plus `full_population_sweep.clj` and
  `mutation_axis.clj`. Which combination produced the published panel is not
  recorded.

Closing these would mean routing the Part II intermediates through `data/` and
extending `reproduce_all.sh` to cover boxes 1 and 8–12, at which point the
paper's claim would hold as written.
