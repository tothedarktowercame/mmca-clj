# Tint threshold vs local causal states — findings

*(2026-07-27. Exploration requested to test the paper's standing caveat that its
domains come from thresholding a rule-space statistic rather than from
predictive equivalence.)*

## What ran

| | |
|---|---|
| operator | offset$+1$ |
| field | W=64, T=120, burn-in 20, 6 seeds |
| LCS selection | depth **1**, tolerance **0.1**, held-out loss 1.5585 over n=37200, 3 folds |
| analysis seed | 20260727 |
| comparison region | rows 20–119, cols 1–62 (100 × 62), identical for both methods |

Reconstruction by `mmca.experiments.tint-vs-lcs` (codex-7), retargeting the E5
machinery in `mmca.experiments.local-causal-states`; its held-out selection path
was reused unmodified (84 insertions, 0 deletions). Comparison and figure by
`scripts/tint_vs_lcs_compare.py` and `scripts/plot_tint_vs_lcs.py`.
Deterministic: two runs give byte-identical output (md5 `208ddca4…`).

**Scale caveat.** This is W=64/T=120, not the paper's L=256/T=600. The tint
transport effect is still strongly present at this size, so the scale does not
suppress it; whether it suppresses the LCS side is untested.

## 1. The two segmentations agree at chance level

| seed | tint density | LCS density | Jaccard | Cohen's κ | d≤0 | d≤1 | d≤2 | d≤3 |
|---|---|---|---|---|---|---|---|---|
| mean | 0.19 | 0.18 | **0.103** | **0.008** | 0.201 | 0.372 | 0.516 | 0.629 |

κ ≈ 0 is chance agreement; three of six seeds are slightly negative. Exact
overlap (d≤0 = 0.201) is indistinguishable from the tint density (0.19), i.e.
what a randomly placed mask of the same size would achieve. Only by dilating the
tint wall to ±3 cells does coverage reach 0.63.

They are not finding the same object, and the figure shows they are not even
finding the same *kind* of object: the tint wall is thin, connected and
curvilinear; the LCS output is diffuse and regional.

## 2. The transport result does NOT survive substitution

Filament-specific score = on-mask mean minus null, averaged over 6 seeds:

| mask | on-filament | − size-matched | − shift | − cross-seed |
|---|---|---|---|---|
| tint | +0.1483 | **+0.0885** | **+0.0876** | **+0.0892** |
| LCS  | +0.0640 | **+0.0036** | **+0.0069** | **+0.0009** |

Combining (synergy) behaves the same way: tint +0.0277, LCS +0.0028.

On tint filaments the effect reproduces the paper. On LCS structures it is
**zero against all three nulls**, and negative for 4 of 6 seeds against the
size-matched null. Only seed 4 shows anything.

## 3. The reason, and it is the important finding

Local transfer entropy here is computed on the **thresholded binary activity
field** `act = (smooth(activity) > 0.35)`. The tint wall is defined as the
**gradient** of that same field. So the mask marks the cells where `act`
changes, and TE on a binary field is near zero except where it changes:

| | |
|---|---|
| te where `act` changed | **+0.8934** (n=461) |
| te where `act` unchanged | **+0.0080** (n=7219) |
| P(changed \| on tint wall) | 0.326 |
| P(changed \| off tint wall) | 0.002 |
| corr(te, changed) | +0.474 |

The tint wall captures the changing cells at ~163× enrichment, and essentially
all the transfer entropy lives on those cells. "Filament-specific transport" is
therefore close to a restatement of "the filament is where the field changes."

**The three nulls do not defend against this.** Size-matched, shift, and
cross-seed all control for properties of the *mask* — its cell count, its shape,
its provenance. Every one of them moves the mask off the change-cells, so all
three report a large effect *because* of the circularity rather than despite it.
That is a gap in the null design, not a failure of the nulls as implemented.

### 3a. Decoupling the measure from the mask threshold

Hold the mask at the paper's 0.35 wall, recompute TE on `act` thresholded
elsewhere (6 seeds, filament-specific score vs size-matched null):

| TE threshold | filament-specific | P(changed \| wall) |
|---|---|---|
| **0.35** (shared with mask) | **+0.0885** | 0.308 |
| 0.30 | +0.0007 | 0.194 |
| 0.40 | −0.0044 | 0.168 |
| 0.50 | −0.0377 | 0.007 |

Moving the measure's threshold by 0.05 — which barely changes the field, and
leaves the wall still overlapping ~19% of change-cells — collapses the effect to
zero.

### 3b. Matched thresholds: the effect tracks the construction

The fair objection to 3a is that at another threshold the true wall has moved,
so a 0.35 mask is merely mismatched. So: derive mask **and** measure from the
same threshold, and sweep it.

| threshold | wall density | on-wall | filament-specific |
|---|---|---|---|
| 0.25 | 0.225 | +0.1227 | +0.0647 |
| 0.30 | 0.211 | +0.1463 | +0.0815 |
| 0.35 | 0.188 | +0.1483 | +0.0885 |
| 0.40 | 0.170 | +0.1790 | +0.1175 |
| 0.50 | 0.119 | +0.2177 | +0.1729 |

The effect is present at **every** threshold and grows **monotonically** with it,
nearly tripling from 0.25 to 0.50. Combined with 3a this is conclusive: what is
being measured is a property of the *construction* — the boundary of a
thresholded field scores high on transfer entropy computed from that same
thresholded field, wherever the threshold is put — and not a threshold-independent
physical structure. A measured physical quantity should not track an arbitrary
analysis parameter monotonically.

## 4. Two readings, and the evidence does not yet separate them

**(A) The tint transport result is circular.** Established by §3/3a/3b, which
are independent of anything about LCS quality. This is the serious one, and it
no longer rests on the substitution test at all.

**(B) The LCS reconstruction is under-resolved, so the substitution is not by
itself a fair test.** Selection landed on depth=1 and tolerance=0.1 — both at
the **low edge** of their candidate grids (`[1 2]`, `[0.1 0.2 0.4]`), the
signature of a grid that is too narrow. A depth-1 lightcone has little chance of
resolving thin propagating boundaries, and the LCS output is correspondingly
blobby rather than wall-like.

Both are true, and they are independent. (B) means §2 alone would not have
convicted the tint. (A) convicts it anyway, on evidence that never involves LCS.
Fixing (B) is still worth doing, but it can no longer rescue the transport
claim — it can only change what replaces it.

## 5. Consequences for the paper

- The tint caveat in §*Domains and Particles* must be **strengthened, not
  softened**. As written it says equal-tint neighbourhoods need not behave
  alike. The sharper problem is that the wall and the transport measure are
  computed from the same thresholded field.
- The claim most at risk is filament-specific transport as evidence that the
  walls are *computationally* special. The claims about where walls *are*
  (fractal dimension, churn) are not touched by this.
- The band structure and the sign inversion on the chaotic flank are comparative
  claims across parameter values and are **not obviously** explained by a
  constant circularity — a tautological measure should not invert. That is the
  strongest available defence and it should be checked rather than asserted.

## 6. What is left to do

The decoupling test (§3a/3b) has already been run and is what settles the main
question, so what remains is repair rather than diagnosis.

1. **A transport measure that does not share a threshold with the mask.** The
   honest candidate is TE on the *phenotype* field, which is not derived from
   the activity threshold at all, with the mask still taken from the tint. This
   is the measurement the paper needs in order to say anything about the walls
   carrying information.
2. **Re-run LCS with a widened grid** (depths 1–4, tolerances 0.02–0.4) and
   confirm the selected point is interior. Needed before the LCS side can be
   cited either way.
3. **Check the band and the inversion under (1).** The elevated-in-a-band and
   sign-inversion results are comparisons *across* the order–chaos sweep at
   fixed threshold. A construction artifact of constant form could still vary
   with $q$ if the boundary structure varies, so these are not automatically
   void — but they inherit the measure, and must be re-derived under (1) rather
   than assumed to survive.

Note that (3) is the paper's headline empirical claim, so the order matters:
(1) first, then (3), and only then decide what §*A Sampled Edge of Chaos* can
say.

## 7. Repair: transport on the phenotype field

The directed measure was recomputed on the raw binary phenotype, while the
filament mask remained the gradient of the smoothed genotype-tint threshold.
Thus the measured field shares neither the rule-activity statistic nor its
threshold. Raw integer genotype/phenotype pairs are generated by
`clojure -M -m mmca.figures phenotype-transport-fields`; analysis is
`python3 scripts/phenotype_transport.py`. The analysis seed is **20260727**.

For offset+1 at W=64, T=120, burn-in 20, seeds 0–5:

| phenotype measure | on mask | − size-matched | − shift | − cross-seed |
|---|---:|---:|---:|---:|
| directed transport | +0.0140 | **−0.0109** | **−0.0114** | **−0.0107** |
| combining | +0.0600 | **−0.0118** | **−0.0124** | **−0.0114** |

This is a null result against all three preregistered nulls. The phenotype does
remain active, but the tint filaments do not carry more phenotype transport or
combining than matched non-filament locations. No parameter was tuned.

The paper-scale seed-1 EoC fields agree with that conclusion. Offset+1 transport
is −0.0043 against size-matched and −0.0045 against the shift null; two4cycle is
−0.1603/−0.1554; sigma16250374 is −0.0728/−0.0690. (A cross-seed null is not
defined for these single-seed display fields; the six-seed offset ensemble
above supplies it.)

### 7a. Threshold-decoupling control on the repaired measure

The phenotype measure is held fixed while the independently constructed tint
mask threshold varies:

| tint mask threshold | phenotype transport − size null | combining − size null |
|---|---:|---:|
| 0.25 | −0.0048 | −0.0633 |
| 0.30 | −0.0048 | −0.0408 |
| 0.35 | −0.0120 | −0.0099 |
| 0.40 | −0.0027 | +0.0336 |
| 0.50 | −0.0090 | +0.0956 |

Transport does **not** show the sharp shared-threshold collapse from §3a: it is
already null and remains near zero at every mask threshold. Combining changes
with which cells the mask selects, but its paper-threshold value is null under
all three nulls and the sweep supplies no threshold-independent positive
effect. The repair therefore succeeds as a decoupled measurement, and its
scientific answer is plainly negative: the existing evidence does not support
the claim that tint walls carry phenotype information.
