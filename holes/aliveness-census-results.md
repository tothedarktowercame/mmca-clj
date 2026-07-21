# Aliveness census -- 40320 bijective operators (result)

Reproduce: `clojure -M -m mmca.experiments.aliveness-census`.
Config: {:seeds 3, :width 60, :steps 100, :late 40, :alive 0.1, :chunk 200}
Aliveness = late-window mean changed-cell fraction, seed-averaged; a field is ALIVE if its change rate >= 0.10.

## 2x2 quadrants

| quadrant | count | share | cleanest exemplars (perm : g-change p-change g-div) |
|---|---:|---:|---|
| genotype-ALIVE / phenotype-ALIVE | 33357 | 82.7% | [4 5 2 3 0 7 6 1] : 0.88 0.59 26.8; [4 1 2 3 0 5 6 7] : 0.91 0.55 29.1; [1 0 2 3 4 5 6 7] : 0.89 0.56 26.1 |
| genotype-ALIVE / phenotype-DEAD | 3953 | 9.8% | [0 3 1 2 7 5 6 4] : 0.82 0.03 27.4; [6 7 2 1 4 5 0 3] : 0.84 0.05 28.9; [6 1 2 4 7 5 0 3] : 0.85 0.07 31.4 |
| genotype-DEAD / phenotype-ALIVE | 574 | 1.4% | [2 0 5 7 3 1 4 6] : 0.00 0.52 1.0; [2 5 3 7 0 1 4 6] : 0.00 0.52 1.1; [2 3 5 7 0 1 4 6] : 0.00 0.52 1.1 |
| genotype-DEAD / phenotype-DEAD | 2436 | 6.0% | [1 0 4 5 3 2 7 6] : 0.00 0.00 1.0; [1 0 4 5 6 2 7 3] : 0.00 0.00 1.0; [1 0 4 7 3 2 5 6] : 0.00 0.00 1.0 |

## Ranked by genotype aliveness (top 30 of 40320)

| # | perm | g-change | p-change | g-div | quadrant |
|---:|---|---:|---:|---:|---|
| 1 | [0 1 2 3 4 5 6 7] | 0.964 | 0.289 | 44.8 | genotype-ALIVE / phenotype-ALIVE |
| 2 | [0 7 2 3 4 1 5 6] | 0.956 | 0.275 | 44.4 | genotype-ALIVE / phenotype-ALIVE |
| 3 | [0 5 1 2 4 3 6 7] | 0.954 | 0.317 | 43.8 | genotype-ALIVE / phenotype-ALIVE |
| 4 | [0 7 2 5 4 1 6 3] | 0.954 | 0.264 | 39.9 | genotype-ALIVE / phenotype-ALIVE |
| 5 | [0 1 2 6 4 5 7 3] | 0.954 | 0.368 | 42.8 | genotype-ALIVE / phenotype-ALIVE |
| 6 | [0 1 6 3 2 4 5 7] | 0.951 | 0.281 | 42.0 | genotype-ALIVE / phenotype-ALIVE |
| 7 | [0 1 3 6 4 5 2 7] | 0.950 | 0.286 | 41.1 | genotype-ALIVE / phenotype-ALIVE |
| 8 | [0 1 2 7 4 3 6 5] | 0.950 | 0.313 | 45.8 | genotype-ALIVE / phenotype-ALIVE |
| 9 | [6 5 1 0 4 3 2 7] | 0.950 | 0.295 | 36.8 | genotype-ALIVE / phenotype-ALIVE |
| 10 | [4 1 2 3 7 5 0 6] | 0.949 | 0.284 | 41.6 | genotype-ALIVE / phenotype-ALIVE |
| 11 | [2 1 5 3 4 0 6 7] | 0.949 | 0.288 | 43.9 | genotype-ALIVE / phenotype-ALIVE |
| 12 | [0 2 4 3 1 5 6 7] | 0.949 | 0.284 | 43.6 | genotype-ALIVE / phenotype-ALIVE |
| 13 | [0 1 2 7 4 5 3 6] | 0.949 | 0.350 | 39.4 | genotype-ALIVE / phenotype-ALIVE |
| 14 | [1 5 2 0 4 3 6 7] | 0.949 | 0.319 | 41.8 | genotype-ALIVE / phenotype-ALIVE |
| 15 | [6 1 0 3 4 5 2 7] | 0.949 | 0.353 | 44.4 | genotype-ALIVE / phenotype-ALIVE |
| 16 | [0 1 4 3 5 2 6 7] | 0.949 | 0.315 | 42.8 | genotype-ALIVE / phenotype-ALIVE |
| 17 | [0 1 2 6 4 3 5 7] | 0.948 | 0.262 | 43.0 | genotype-ALIVE / phenotype-ALIVE |
| 18 | [2 1 7 3 4 5 6 0] | 0.948 | 0.290 | 44.5 | genotype-ALIVE / phenotype-ALIVE |
| 19 | [0 1 3 7 4 5 6 2] | 0.948 | 0.327 | 44.4 | genotype-ALIVE / phenotype-ALIVE |
| 20 | [0 7 4 3 1 5 2 6] | 0.948 | 0.251 | 41.0 | genotype-ALIVE / phenotype-ALIVE |
| 21 | [0 1 2 5 4 6 3 7] | 0.948 | 0.291 | 43.1 | genotype-ALIVE / phenotype-ALIVE |
| 22 | [0 1 4 3 5 6 2 7] | 0.948 | 0.302 | 42.2 | genotype-ALIVE / phenotype-ALIVE |
| 23 | [0 1 5 2 4 3 6 7] | 0.948 | 0.321 | 45.9 | genotype-ALIVE / phenotype-ALIVE |
| 24 | [4 1 3 0 2 5 6 7] | 0.947 | 0.304 | 42.0 | genotype-ALIVE / phenotype-ALIVE |
| 25 | [0 1 5 3 4 6 2 7] | 0.947 | 0.312 | 42.1 | genotype-ALIVE / phenotype-ALIVE |
| 26 | [0 2 3 5 1 4 6 7] | 0.946 | 0.286 | 40.7 | genotype-ALIVE / phenotype-ALIVE |
| 27 | [0 1 6 3 2 5 7 4] | 0.946 | 0.315 | 41.9 | genotype-ALIVE / phenotype-ALIVE |
| 28 | [0 1 6 3 4 2 5 7] | 0.946 | 0.284 | 45.3 | genotype-ALIVE / phenotype-ALIVE |
| 29 | [0 1 2 3 4 6 7 5] | 0.946 | 0.359 | 44.6 | genotype-ALIVE / phenotype-ALIVE |
| 30 | [6 0 2 3 4 5 7 1] | 0.946 | 0.393 | 42.0 | genotype-ALIVE / phenotype-ALIVE |

## Genotype-aliveness distribution

min=0.000 p10=0.390 p25=0.570 median=0.727 p75=0.821 p90=0.882 max=0.945

Alive genotypes (>= 0.10): 37310/40320 (92.5%).

## Sustain fraction by cycle type

Does cycle structure determine aliveness? If yes, each cycle type is all-alive
or all-dead; a split column means the type under-determines the outcome.

| cycle type | count | genotype-alive | share | live/live | live/dead | dead/live | dead/dead |
|---|---:|---:|---:|---:|---:|---:|---:|
| [1 7] | 5760 | 5760 | 100.0% | 5240 | 520 | 0 | 0 |
| [8] | 5040 | 4176 | 82.9% | 3831 | 345 | 146 | 718 |
| [1 2 5] | 4032 | 4032 | 100.0% | 3697 | 335 | 0 | 0 |
| [1 3 4] | 3360 | 3360 | 100.0% | 2895 | 465 | 0 | 0 |
| [1 1 6] | 3360 | 3360 | 100.0% | 2743 | 617 | 0 | 0 |
| [2 6] | 3360 | 2352 | 70.0% | 2132 | 220 | 192 | 816 |
| [3 5] | 2688 | 2688 | 100.0% | 2649 | 39 | 0 | 0 |
| [1 1 2 4] | 2520 | 2520 | 100.0% | 1841 | 679 | 0 | 0 |
| [1 2 2 3] | 1680 | 1680 | 100.0% | 1452 | 228 | 0 | 0 |
| [1 1 1 5] | 1344 | 1344 | 100.0% | 1335 | 9 | 0 | 0 |
| [2 2 4] | 1260 | 620 | 49.2% | 532 | 88 | 142 | 498 |
| [4 4] | 1260 | 840 | 66.7% | 695 | 145 | 72 | 348 |
| [2 3 3] | 1120 | 1120 | 100.0% | 1102 | 18 | 0 | 0 |
| [1 1 1 2 3] | 1120 | 1120 | 100.0% | 1109 | 11 | 0 | 0 |
| [1 1 3 3] | 1120 | 1120 | 100.0% | 1120 | 0 | 0 | 0 |
| [1 1 2 2 2] | 420 | 420 | 100.0% | 265 | 155 | 0 | 0 |
| [1 1 1 1 4] | 420 | 420 | 100.0% | 373 | 47 | 0 | 0 |
| [1 1 1 1 2 2] | 210 | 210 | 100.0% | 182 | 28 | 0 | 0 |
| [1 1 1 1 1 3] | 112 | 112 | 100.0% | 112 | 0 | 0 | 0 |
| [2 2 2 2] | 105 | 27 | 25.7% | 23 | 4 | 22 | 56 |
| [1 1 1 1 1 1 2] | 28 | 28 | 100.0% | 28 | 0 | 0 | 0 |
| [1 1 1 1 1 1 1 1] | 1 | 1 | 100.0% | 1 | 0 | 0 | 0 |

Full ranked list of every operator: `holes/aliveness-census-ranked.tsv`.
