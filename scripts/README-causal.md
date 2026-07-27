# Causal perturbation measurement (paper: "A Causal Measure")

Reproduces Figures 16 and 17 and Empirical findings `find:causal` / `find:ladder`.

    clojure -M -i scripts/river_perturbation.clj        # single-seed sweep, all 80 sites -> /tmp/pert_summary.tsv
    clojure -M -i scripts/river_perturbation_seeds.clj  # 6 seeds x 20 sites          -> /tmp/pert_seeds.tsv
    clojure -M -i scripts/river_perturbation_rows.clj   # 4 seeds x 10 sites, all dt  -> /tmp/pert_rows.tsv
    python3 scripts/plot_river_perturbation.py          # figures/river_perturbation.{png,pdf}
    python3 scripts/plot_river_centroid.py              # figures/river_centroid.{png,pdf}

All runs: L=80, T=120, t*=60. Damage is measured on the PHENOTYPE layer as the
number of cells differing between branches; distance is the shorter circular
displacement. Forking is by re-seeding: `java.util.Random` is deterministic from
its seed and the per-step draw count is fixed (one `nextInt` per cell), so both
branches consume an identical tape and every divergence is the causal
consequence of the intervention. The control is `run-river-ablated-from`:
identical seed, tape, construction and initial state, with only the live
phenotype-to-genotype edge cut.

ECA calibration ladder (rules 0, 204, 90, 54, 110, 30) is in
`scripts/eca_damage_ladder.py`, same protocol, 6 seeds x 10 sites.

Evidence trail and retracted intermediate conclusions: `holes/tint-vs-lcs-findings.md`.
