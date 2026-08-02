# Shared-tape robustness amendment — 2026-08-02

This prospective three-tape follow-up classified the result as
`:no-gain-reproduced`.

| Rewrite tape | Stable contexts | Stable-mode capture | Apparatus exact |
|---:|---:|---:|:---:|
| 20260811 | 4 / 16 | 1,783 bp | yes |
| 20260812 | 4 / 16 | 1,782 bp | yes |
| 20260813 | 3 / 16 | 1,747 bp | yes |

The variable-tape reference was 4 stable contexts and 2,003 basis points.
All three new shared tapes met the registered `noGain` condition; the decision
rule required two of three.  Thus the original 3/16 result was not merely an
idiosyncratic shared-tape draw.  This hardens the conclusion about the four-bit
context coordinate, but does not gate the independent locus/rule masking study.

Two complete extractions were byte-identical:
`cb4b546f4b1e5ed4d9b1ec04aed078a17754459bce610ba28856a745b18572da`.

The frozen input and source bindings were unchanged:

- Source revision: `f893a4005842ca0ceeb472020990ed131bb2de67`
- Input SHA-256: `5fa7c5b5e26066cbd58f450bef7de270e34b4ef0d1a7490a2bfe1dc924816f24`

## Positional audit of the banked endpoint map

The review's quoted endpoint figures were independently re-derived from the
20,480-row assimilation map:

- 1,062 selectable endpoints across 67/80 loci;
- 5/80 current alleles selectable when held;
- median nearest selectable rule distance 2 bits;
- 27/67 endpoint-bearing loci have a selectable rule within one bit;
- locus 1 has 86 selectable rules.

A linear regression of endpoint count on locus index has slope `-0.509892`
endpoints/locus and Pearson `r = -0.646699`; Spearman `rho = -0.767213`.
Endpoint totals by consecutive 20-locus block are `505, 461, 68, 28`.
This is strong positional heterogeneity and supports retaining the balanced
position/density strata in the masking experiment.
