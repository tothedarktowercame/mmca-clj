# Causal perturbation measurement (paper: "A Causal Measure")

Reproduces Figures 16 and 17 and Empirical findings `find:causal` / `find:ladder`.

**Figures must be generated with the pinned stack** (`.venv`, Matplotlib 3.11.1)
— the system Matplotlib 3.6.3 pipeline is defective for these panels. See the
figure-pipeline note in `README.md`.

    clojure -M -i scripts/river_perturbation.clj        # single-seed sweep, all 80 sites -> data/pert_summary.tsv
    clojure -M -i scripts/river_perturbation_seeds.clj  # 6 seeds x 20 sites          -> data/pert_seeds.tsv
    clojure -M -i scripts/river_perturbation_rows.clj   # 4 seeds x 10 sites, all dt  -> data/pert_rows.tsv
    .venv/bin/python scripts/plot_river_perturbation.py   # figures/river_perturbation.{png,pdf}
    .venv/bin/python scripts/plot_river_centroid.py       # figures/river_centroid.{png,pdf}

CA space-time panels are composited to a single explicit RGB array and upsampled
before being handed to Matplotlib. Layering a two-valued panel under a masked
overlay makes the PDF backend emit a one-bit indexed stream, which is the
documented corruption; an RGB array avoids that path. Panels use aspect="equal"
(never "auto", which shears the lattice) — the repo convention for CA panels.

All runs: L=80, T=120, t*=60. Damage is measured on the PHENOTYPE layer as the
number of cells differing between branches; distance is the shorter circular
displacement. Forking is by re-seeding: `java.util.Random` is deterministic from
its seed and the per-step draw count is fixed (one `nextInt` per cell), so both
branches consume an identical tape and every divergence is the causal
consequence of the intervention. The control is `run-river-ablated-from`:
identical seed, tape, construction and initial state, with only the live
phenotype-to-genotype edge cut.

ECA calibration ladder (rules 0, 204, 90, 54, 110, 30) is computed inline by
`scripts/regime_placement.clj`, which emits it as the `eca` rows of its TSV
(4 seeds per rule, same protocol). There is no `eca_damage_ladder.py`.

Evidence trail and retracted intermediate conclusions: `holes/tint-vs-lcs-findings.md`.
