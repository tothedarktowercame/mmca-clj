> Integration note (claude-2): this committed artifact uses a **reduced config**
> (widths [30 60 120], 16 seeds, 9 q values, 200 steps) so `-main` completes under
> the 30-min job cap that killed codex-5's original run. The full L=240 / 32-seed /
> 13-q / 300-step sweep is preserved as `full-config` in the source and is a
> documented follow-up. Reproduce this artifact: `clojure -M -m mmca.experiments.control-param-scan`.

# E2 — Continuous interrupter finite-size scan (result)

Reproduce bit-for-bit: `clojure -M -m mmca.experiments.control-param-scan`.
Config: writing=[4 5 6 7 0 1 2 3], seeds 0–15 (16), widths=[30 60 120], steps=200, late window=60, collapse window=8.
Feedforward base only: `run-propagator`, so X→G remains zero by construction. q=Pr(propagator write); 1-q holds the neighbour blend.

Innovation density is the late changed-cell fraction. Collapse is 8 consecutive steps with zero G and X innovation.

| L | q | a_G | a_X | exp(H(G)) | L Var(a_G) | Binder | P(collapse) | median T_c |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 30 | 0.0000 | 0.0687 | 0.0123 | 5.2316 | 0.4465 | -0.9506 | 0.5625 | 15.0 |
| 30 | 0.0500 | 0.0863 | 0.0306 | 6.5525 | 0.1545 | -0.3610 | 0.1250 | 138.0 |
| 30 | 0.1000 | 0.0882 | 0.0251 | 5.9395 | 0.0795 | 0.3722 | 0.1250 | 147.5 |
| 30 | 0.1500 | 0.0870 | 0.0127 | 3.9393 | 0.1887 | -0.3152 | 0.2500 | 151.5 |
| 30 | 0.2000 | 0.0764 | 0.0086 | 3.1360 | 0.2893 | -1.1022 | 0.3750 | 149.0 |
| 30 | 0.3000 | 0.1020 | 0.0209 | 2.2077 | 0.9449 | -1.1285 | 0.5000 | 102.0 |
| 30 | 0.5000 | 0.0955 | 0.0159 | 2.1137 | 2.0309 | -1.6684 | 0.8750 | 87.5 |
| 30 | 0.7500 | 0.0009 | 0.0000 | 1.1520 | 0.0004 | -4.3333 | 1.0000 | 58.0 |
| 30 | 1.0000 | 0.0000 | 0.0000 | 1.1954 | 0.0000 | 0.0000 | 1.0000 | 39.0 |
| 60 | 0.0000 | 0.0563 | 0.0181 | 8.1268 | 0.3086 | -0.1773 | 0.2500 | 16.5 |
| 60 | 0.0500 | 0.0821 | 0.0251 | 10.3520 | 0.0958 | 0.4031 | 0.0000 | >T |
| 60 | 0.1000 | 0.0926 | 0.0135 | 8.2399 | 0.1372 | 0.3710 | 0.0000 | >T |
| 60 | 0.1500 | 0.1017 | 0.0120 | 5.5241 | 0.2947 | 0.1870 | 0.1250 | 187.0 |
| 60 | 0.2000 | 0.1012 | 0.0118 | 4.9475 | 0.4264 | 0.1439 | 0.1250 | 121.5 |
| 60 | 0.3000 | 0.0651 | 0.0096 | 2.6225 | 0.4903 | -0.3926 | 0.6875 | 160.0 |
| 60 | 0.5000 | 0.0064 | 0.0005 | 1.2938 | 0.0223 | -3.6297 | 0.9375 | 100.0 |
| 60 | 0.7500 | 0.0000 | 0.0000 | 1.1220 | 0.0000 | 0.0000 | 1.0000 | 69.5 |
| 60 | 1.0000 | 0.0000 | 0.0000 | 1.2124 | 0.0000 | 0.0000 | 1.0000 | 61.5 |
| 120 | 0.0000 | 0.0667 | 0.0437 | 12.3676 | 0.3156 | 0.1818 | 0.0625 | 21.0 |
| 120 | 0.0500 | 0.0776 | 0.0385 | 15.9814 | 0.1166 | 0.4004 | 0.0000 | >T |
| 120 | 0.1000 | 0.0860 | 0.0200 | 11.9377 | 0.0866 | 0.5622 | 0.0000 | >T |
| 120 | 0.1500 | 0.0962 | 0.0163 | 7.9669 | 0.2521 | 0.4078 | 0.0000 | >T |
| 120 | 0.2000 | 0.0845 | 0.0199 | 6.0721 | 0.2254 | 0.2065 | 0.0000 | >T |
| 120 | 0.3000 | 0.0568 | 0.0103 | 3.2423 | 0.4913 | -0.9166 | 0.3750 | 178.0 |
| 120 | 0.5000 | 0.0087 | 0.0009 | 1.4171 | 0.0901 | -4.1276 | 0.9375 | 135.0 |
| 120 | 0.7500 | 0.0000 | 0.0000 | 1.1242 | 0.0000 | 0.0000 | 1.0000 | 85.5 |
| 120 | 1.0000 | 0.0000 | 0.0000 | 1.1309 | 0.0000 | 0.0000 | 1.0000 | 61.5 |

## Finite-size diagnostics

Susceptibility peaks: L30:q=0.5000,chi=2.0309, L60:q=0.3000,chi=0.4903, L120:q=0.3000,chi=0.4913.  Peak-q span=0.2000; chi(Lmax)/chi(Lmin)=0.2419.

Exploratory collapse scores (lower RMSE is better; x=(q-qc)L^(1/nu)): nu=0.5 activity=0.2234[1 bins], survival=0.1671[1 bins]; nu=1.0 activity=0.2058[5 bins], survival=0.1657[5 bins]; nu=2.0 activity=0.1484[5 bins], survival=0.1593[5 bins].

## Reading — metastability

At least one L,q cell mixes collapsed and surviving seeds, while the susceptibility peaks do not meet the joint convergence-and-growth bar. The honest reading is metastability: seed-dependent residence times dominate this horizon, so E3 transient scaling should test whether the mixed region sharpens or remains broad.
