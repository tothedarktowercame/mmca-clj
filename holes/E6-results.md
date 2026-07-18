# E6 — Multiscale spacetime spectra (result)

Reproduce: `clojure -M -m mmca.experiments.zai-5`
Determinism test: `clojure -M:test` (see `mmca.zai-5-test`).
Config: **seed=0, W=60, steps=80**. Three configs: offset+4/base (collapsing
null), offset+2/base (sustained), river/feedback (treatment).

## Config 1: offset+4 / base (collapsing null)

### S_GG(k,omega) — genotype activity power spectrum

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 11681 | 28952 | 1874161 | 28952 | 11681 |
| 5 | 104 | 1328 | 1231 | 263 | 164 |
| 10 | 3435 | 768 | 1027 | 2308 | 13 |
| 30 | 303 | 198 | 49 | 198 | 303 |

Peak S_GG at k=0 omega=0 (power=1874161).

### S_XX(k,omega) — phenotype activity power spectrum

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 1012 | 5017 | 153664 | 5017 | 1012 |
| 5 | 267 | 1282 | 1335 | 136 | 78 |
| 30 | 523 | 705 | 4 | 705 | 523 |

Peak S_XX at k=0 omega=0 (power=153664).

### Four-point susceptibility C_overlap(r) at selected lags

| tau | r=0 | r=1 | r=5 | r=10 | r=15 |
|-----|-----|-----|-----|------|------|
| 1 | 0.1751 | 0.1251 | 0.1090 | 0.1011 | 0.0947 |
| 5 | 0.1525 | 0.1055 | 0.0938 | 0.0863 | 0.0788 |
| 10 | 0.1302 | 0.0831 | 0.0753 | 0.0673 | 0.0633 |

## Config 2: offset+2 / base (sustained)

### S_GG(k,omega)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 3846 | 2345 | 17205904 | 2345 | 3846 |
| 5 | 1069 | 2307 | 2606 | 5525 | 449 |
| 10 | 825 | 308 | 1764 | 510 | 801 |
| 30 | 22 | 27 | 36 | 27 | 22 |

Peak S_GG at k=0 omega=0 (power=17205904).

### S_XX(k,omega)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 772 | 5660 | 2247001 | 5660 | 772 |
| 5 | 564 | 577 | 21959 | 924 | 289 |
| 10 | 2692 | 180 | 793 | 88 | 441 |
| 30 | 2135 | 3 | 3025 | 3 | 2135 |

Peak S_XX at k=0 omega=0 (power=2247001).

### Four-point susceptibility

| tau | r=0 | r=1 | r=5 | r=10 | r=15 |
|-----|-----|-----|-----|------|------|
| 1 | 0.1682 | 0.0523 | 0.0134 | -0.0164 | -0.0095 |
| 5 | 0.1858 | 0.0519 | 0.0168 | -0.0198 | -0.0066 |
| 10 | 0.1882 | 0.0510 | 0.0129 | -0.0004 | -0.0121 |

## Config 3: river / feedback (treatment)

### S_GG(k,omega)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 1274 | 2861 | 17388900 | 2861 | 1274 |
| 5 | 1999 | 1152 | 2611 | 1524 | 176 |
| 10 | 1842 | 599 | 2163 | 374 | 72 |
| 15 | 103 | 598 | 4964 | 26 | 139 |
| 20 | 307 | 101 | 7779 | 369 | 73 |
| 25 | 38 | 78 | 4911 | 1500 | 271 |
| 30 | 114 | 117 | 8100 | 117 | 114 |

Peak S_GG at k=0 omega=0 (power=17388900). Notable: off-DC structure at k=15-30,
omega=0 (4964-8100) — sustained spatial structure in genotype activity.

### S_XX(k,omega)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | 569 | 7049 | 1401856 | 7049 | 569 |
| 5 | 633 | 834 | 4704 | 901 | 371 |
| 10 | 177 | 292 | 457 | 2190 | 39 |
| 30 | 70 | 340 | 256 | 340 | 70 |

Peak S_XX at k=0 omega=0 (power=1401856).

### S_GX(k,omega) — cross-spectrum (selected bins)

| k \ om | -10 | -5 | 0 | 5 | 10 |
|--------|-----|----|----|----|-----|
| 0 | -823 | 2922 | 4937280 | 2922 | -823 |
| 5 | 559 | 337 | 3339 | 140 | -175 |
| 10 | -432 | -306 | -917 | -862 | 2 |
| 25 | 74 | -54 | 667 | 66 | 365 |
| 30 | 1 | -199 | 1440 | -199 | 1 |

### Four-point susceptibility

| tau | r=0 | r=1 | r=5 | r=10 | r=15 |
|-----|-----|-----|-----|------|------|
| 1 | 0.1565 | 0.0646 | 0.0179 | -0.0129 | -0.0283 |
| 5 | 0.1741 | 0.0638 | 0.0221 | -0.0183 | -0.0335 |
| 10 | 0.1762 | 0.0544 | 0.0175 | -0.0152 | -0.0304 |

## Reading

**Across all three configs, spectra are DC-dominated (k=0, omega=0)** —
no propagating-wave ridges or oscillatory modes in any engine/writing
combination. The off-DC power is broadband noise without dispersion
structure.

The key diagnostic differences between configs:

1. **Collapsing null (offset+4/base)**: smoothest spectra, slowest
   susceptibility decay (~2-3 cell correlation, monotone positive). The field
   is dying gently.

2. **Sustained (offset+2/base)**: susceptibility crosses zero at r~6-7 cells
   — **anticorrelation** at intermediate range, the signature of domain
   boundaries. S_XX at k=5, omega=0 is elevated (21959 vs 1335 in the
   collapsing case): the phenotype has more spatial structure at wavelength
   12. Still no propagating ridges.

3. **River (feedback treatment)**: S_GG shows off-DC structure at k=15-30,
   omega=0 (4964-8100): the feedback creates spatially periodic genotype
   activity that the feedforward base lacks. The cross-spectrum S_GX at
   k=30, omega=0 = 1440 (vs 14 in collapsing, 330 in sustained): the river
   introduces significant small-wavelength coupling between genotype and
   phenotype. Susceptibility anticorrelation is deeper (-0.03 vs -0.01),
   extending to larger r (-0.0304 at r=15 for tau=10).

**These spectra are descriptive, not evidence of criticality.** The
river's off-DC cross-spectrum and deeper susceptibility anticorrelation
are the feedback signature; the absence of propagating ridges or
oscillatory peaks means the dynamics lack coherent wave modes even with
feedback. The sustained operator's domain-boundary anticorrelation is the
most physically interesting feature: it suggests intermittent domain
structure worth probing with the full four-point susceptibility at
higher resolution.
