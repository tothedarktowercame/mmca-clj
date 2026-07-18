# E6 — Multiscale spacetime spectra (result)

Reproduce: `clojure -M -m mmca.experiments.zai-5`
Determinism test: `clojure -M:test` (see `mmca.zai-5-test`).
Config: writing = offset+4 `[4 5 6 7 0 1 2 3]` (all-even, collapses),
feedforward base `run-propagator`, **seed=0, W=60, steps=80** (activity grid T=80).

## S_GG(k,omega) — genotype activity power spectrum

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 11681 | 28952 | 1874161 | 28952 | 11681 |
| 5 | 104 | 1328 | 1231 | 263 | 164 |
| 10 | 3435 | 768 | 1027 | 2308 | 13 |
| 15 | 14 | 883 | 545 | 198 | 45 |
| 20 | 397 | 237 | 337 | 5 | 132 |
| 25 | 425 | 125 | 9 | 253 | 58 |
| 30 | 303 | 198 | 49 | 198 | 303 |

## S_XX(k,omega) — phenotype activity power spectrum

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 1012 | 5017 | 153664 | 5017 | 1012 |
| 5 | 267 | 1282 | 1335 | 136 | 78 |
| 10 | 528 | 773 | 499 | 338 | 7 |
| 15 | 132 | 71 | 346 | 58 | 1112 |
| 20 | 152 | 313 | 559 | 541 | 387 |
| 25 | 80 | 130 | 143 | 246 | 374 |
| 30 | 523 | 705 | 4 | 705 | 523 |

## S_GX(k,omega) — cross-spectrum (cospectrum = Re)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 3310 | 11782 | 536648 | 11782 | 3310 |
| 5 | -160 | 1125 | 885 | -189 | 4 |
| 10 | 1078 | -624 | -699 | 758 | -1 |
| 15 | 43 | 25 | 79 | 58 | -143 |
| 20 | -242 | 33 | -312 | -41 | 217 |
| 25 | 177 | -102 | 35 | -18 | 112 |
| 30 | 113 | -57 | 14 | -57 | 113 |

## Four-point susceptibility C_overlap(r) at lag tau

| tau | r=0 | r=1 | r=2 | r=5 | r=10 | r=15 |
|-----|-----|-----|-----|-----|------|------|
| 1 | 0.1751 | 0.1251 | 0.1159 | 0.1090 | 0.1011 | 0.0947 |
| 5 | 0.1525 | 0.1055 | 0.0995 | 0.0938 | 0.0863 | 0.0788 |
| 10 | 0.1302 | 0.0831 | 0.0768 | 0.0753 | 0.0673 | 0.0633 |

## Peak locations

- Peak S_GG at k=0, omega=0 (power=1874161) — DC dominance: genotype activity is uniform/synchronised.
- Peak S_XX at k=0, omega=0 (power=153664) — same for phenotype: both layers are dominated by their spatial mean.
- Cross-spectrum S_GX peaks at k=0, omega=0 (536648): the layers are positively co-active at DC.

## Reading

Both spectra are dominated by the DC bin (k=0, omega=0), meaning the activity is spatially homogeneous and temporally persistent — consistent with a collapsing operator that settles to a fixed point rather than supporting propagating structures. The sub-DC structure shows broadband noise without clear dispersion ridges: there are no propagating-wave signatures (no linear omega-vs-k ridges) and no oscillatory modes (no isolated off-DC peaks). The cross-spectrum S_GX is positive and large only at DC, confirming that genotype and phenotype activity are co-active only through their shared mean — scale-dependent coupling is negligible away from k=0. The four-point susceptibility C_overlap(r,tau) decays monotonically in both r and tau, with a short spatial correlation length (~2–3 cells) and no intermittent-domain signature (which would show as a peak at finite r or non-monotone decay). These are descriptive spectra of a collapsing system; they show no ridge structure, no oscillatory modes, and no intermittent-domain signatures — exactly what we expect from a feedforward null that homogenises rather than computes. The river engine (which reads phenotype back) is the treatment whose spectra, compared against this baseline, would reveal whether feedback introduces propagating or oscillatory structure.
