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

## 8. Edge-drawing ensemble — mask-dependence is real, and it is ordered

*(Added 2026-07-27 in response to the objection that any choice of edge must
influence transport, so mask-dependence cannot by itself convict the tint.)*

The objection is correct and the §3b sweep did not answer it: that sweep varied
one *threshold* inside one *algorithm*. So: four genuinely different ways of
drawing the edge, on the same fields, with the same TE on `act(0.35)`, all masks
size-matched to the tint wall.

Six-seed W=64 fields, filament-specific transport:

| drawing | shares with the measure | mean | min | max |
|---|---|---|---|---|
| A tint — gradient of the *thresholded* field | the exact binary field | **+0.0885** | +0.0586 | +0.1250 |
| C gradient of the *continuous* activity field | the score, not the threshold | +0.0200 | −0.0191 | +0.0451 |
| D local causal states | nothing | +0.0045 | −0.0228 | +0.0574 |
| E rule-identity change | nothing | +0.0007 | −0.0056 | +0.0076 |

The paper's own L=256/T=600 fields:

| field | A tint | C continuous | E rule-change |
|---|---|---|---|
| offset1 | +0.1335 | +0.0260 | +0.0031 |
| two4cyc | +0.2689 | +0.0916 | +0.0154 |
| sigma16250374 | +0.1628 | +0.0629 | +0.0098 |

**This is not benign scatter.** The result is ordered by how much construction
each drawing shares with the measure: sharing the exact thresholded field gives
the largest value, sharing the activity score but not the threshold gives
roughly a quarter to a third of it, and sharing nothing gives near zero. Free
algorithm-dependence would scatter; this ranks.

**But C is not zero, and that matters.** On `two4cyc` — the field with the most
visible structure — a drawing that never thresholds still recovers +0.0916,
about a third of the tint's value. So the honest reading is not "there is
nothing here" but "the thresholded construction inflates whatever is here by
roughly three-fold." A residual effect may survive.

**Caveat on E and D.** Both score near zero, but the *measure* is still TE on
the thresholded activity field, so any mask not aligned to `act` structure is
penalised by construction. E and D are therefore weak evidence about whether
edges carry transport, and strong evidence only about circularity. Separating
these requires changing the measure, not the mask — the phenotype test.

**Consequence for reporting.** An absolute figure such as "+0.097 bits of
filament-specific transport" is not a property of the system; it is a property
of the system *and* the drawing rule, and it ranges roughly 40-fold across
reasonable drawings. Either report the envelope across a family of drawings — a
methods contribution in its own right, since this literature reports such
numbers without one — or restrict to comparative claims under a rule held fixed
across the comparison.

## 9. q = 0.25 coexisting domains — the domains are real

*(2026-07-27. Testing the objection that if filaments align with visible edges
AND capture a genotype difference across them, concrete conclusions follow.)*

Field: `data/eoc_phase_q250.txt`, the Figure 12 coexisting-domains panel
(offset+1, interrupter q=0.25, L=240, T=300, seed 1). Figure:
`figures/q025_edges.png`.

**(a) Alignment — confirmed.** The tint wall traces the visible plume boundaries
in the activity tint. This is a visual check, but the structure is unambiguous.

**(b) Genotypic difference at matched activity — confirmed, and this is the
real finding.** The worry about (a) is that the partition is by activity score,
so the two sides trivially differ in activity. So: restrict to cells whose
*individual* activity score lies in a narrow band, and ask whether the two
domains hold different *rules*. Total-variation distance between rule
distributions, against a noise floor from randomly halving the larger group:

| activity bin | n active | n quiet | TV(active, quiet) | noise floor | ratio |
|---|---|---|---|---|---|
| [0.00, 0.001) | 377 | 36839 | **0.489** | 0.023 | 21× |
| [0.20, 0.35) | 3651 | 11426 | **0.147** | 0.011 | 13× |
| [0.45, 0.55) | 5774 | 1781 | **0.295** | 0.020 | 15× |
| [0.55, 1.01) | 9391 | 1860 | **0.522** | 0.021 | 25× |

Rule diversity differs too: at activity [0.45, 0.55) the active domain holds 24
distinct rules per 1000 cells against the quiet domain's 38.

Two cells with the *same* activity score belong to systematically different rule
populations depending on which domain they sit in, at 13–25× the noise floor.
The partition therefore captures genuine genotypic structure that the activity
score does not determine. The domains are not an artifact of the tint.

**What this does and does not settle.** It settles that the walls are real
boundaries between genuinely different rule populations — a claim the paper can
make and should make more loudly. It does not rescue the transport measurement,
which is a claim about an estimator rather than about whether the structure
exists (§3, §8), and which the phenotype test (§7) does not support. Real
domains and an uninformative transport statistic are consistent: the circularity
argument never asserted the edges were fake.

## 10. What "transport" means — three distinct claims, and Figure 4 separates them

*(2026-07-27. Prompted by the observation that genotypes churn on the edges in
the general case but do not in Figure 4.)*

**Hypothesis tested and REFUTED:** that "transport" is "churn" measured twice.
It is not. Correlation between local TE and genotype churn is ~0:

| field | corr(te, churn) | te given churn | te given no churn | FS-transport | FS-churn |
|---|---|---|---|---|---|
| offset1 | +0.008 | +0.0637 | +0.0546 | +0.1335 | +0.0202 |
| two4cyc | +0.023 | +0.0639 | +0.0396 | +0.2689 | +0.1611 |
| sigma16250374 | +0.019 | +0.0629 | +0.0450 | +0.1628 | +0.0858 |

Conditioning on churn does not remove the wall effect either — among cells that
are *all* churning, on-wall TE exceeds off-wall by +0.18 to +0.31. So the
churn finding and the transport finding are **genuinely independent**, which is
good news for the paper: they are two results, not one.

The reason they decouple: the genotype churns nearly everywhere in a live field,
whereas the *coarse thresholded activity* field changes only at domain
boundaries. TE tracks the coarse field (corr +0.474; +0.8934 on act-change cells
vs +0.0080 elsewhere), not the raw rewriting. The circularity of §3 is therefore
untouched — it was never a claim about churn.

**Figure 4 forces the distinction.** Measured on `data/figshell.txt`: after row
40 the genotype is frozen (per-row change 0.000625; 3 distinct rows in 81) while
the phenotype stays alive (per-row change 0.2906).

A frozen genotype means a frozen activity field, which means **TE on the
activity field is zero everywhere** — the paper's measure is vacuous exactly
where the boundary is most exactly known.

So three different claims travel under one word:

1. **Genotype-layer transport** — rewriting propagating along the wall. Requires
   churn. Figure 4 has none, so this claim is simply inapplicable there.
2. **Activity-domain boundary motion** — what the paper actually measures.
   Circular (§3), and identically zero when the genotype freezes.
3. **Phenotype-layer transport** — signals channelled or blocked by the
   boundary. Survives a frozen genotype, shares no construction with the mask,
   and is the honest version. Negative so far in the general case (§7).

**Figure 4 is a better instrument, not merely a cleaner one.** In the general
case the wall moves in response to the same dynamics being measured, so the
boundary and the signal are entangled. With the genotype frozen the wall is a
fixed boundary condition, and anything the phenotype does across it is
attributable to the boundary rather than to the boundary chasing the phenotype.
Claim (3) tested on Figure 4 is the strongest form of the question the paper
was trying to ask.

## 11. Phenotype-only transport at a frozen boundary — no consistent effect

*(2026-07-27. Claim (3) of §10 tested in its cleanest setting: the Figure 4
construction, where the genotype freezes into stripes so the boundary cannot
move, and the phenotype stays alive.)*

Eight seeds of `run-propagator [2 3 0 1 5 4 7 6] seed 80 120`, analysed below
row 60 where the genotype is frozen. Boundary = columns where the frozen rule
changes; interior = columns ≥3 from any boundary. Measure: the paper's TE
estimator applied to the **phenotype**.

| seed | rules | b-cols | on-boundary | interior | diff |
|---|---|---|---|---|---|
| 0 | 5 | 13 | +0.0268 | +0.0240 | +0.003 |
| 1 | 2 | 4 | **−0.1634** | +0.0637 | −0.227 |
| 2 | 3 | 9 | +0.0113 | +0.0145 | −0.003 |
| 3 | 3 | 7 | −0.0110 | +0.0172 | −0.028 |
| 4 | 3 | 6 | +0.0898 | +0.0202 | +0.070 |
| 5 | 2 | 8 | +0.0268 | +0.1337 | −0.107 |
| 6 | 2 | 4 | **−0.1663** | +0.0457 | −0.212 |
| 7 | 2 | 4 | **+0.1967** | +0.0601 | +0.137 |

**The paper's own field (seed 1) shows striking blocking** — phenotype TE of
−0.163 on the boundary against +0.064 in the interiors, z = −2.42 against a
random-column null. Taken alone it looks like a clean result: a frozen rule
boundary at which neighbours are actively *misinformative* about the next state,
which is mechanistically what one would expect where two different rules abut.

**It does not replicate.** Across eight seeds the difference is negative in 5/8,
and seed 7 is strongly positive. The large effects all occur at seeds with only
4 boundary columns, i.e. where the estimate is noise-dominated.

**Beware the pooled test.** Pooling cells gives on-boundary +0.0108 (n=3300) vs
interior +0.0487 (n=23760), Welch t = **−5.35**, which looks decisive. It is
pseudoreplication: cells within a seed are not independent. At the honest unit
of replication — the seed — the paired difference is −0.0460 (sd 0.1283),
**t(7) = −1.01**, p ≈ 0.35. Nothing.

**Conclusion.** In the cleanest available setting — exact boundary, independent
measure, no confound from a wall that moves in response to what is being
measured — there is no consistent boundary effect on phenotype information flow,
in either direction. This is the strongest form of the question the paper was
asking, and the answer is null.

Caveats: 8 seeds; 4–13 boundary columns each; history length 1; and the "Figure
4 construction" is not a stable object across seeds — the number of surviving
rules varies from 2 to 5, so the seeds are not strict replicates of one another.

## 12. River perturbation — the first POSITIVE causal result

*(2026-07-27. Testing the proposal that the river is not about the edge but the
contents, which propagate, and could therefore carry information across space.)*

**Why this measurement is different from everything above.** §§1–11 are
observational: draw a region, compute a statistic on it, argue about whether the
region was drawn fairly. This one is **causal and mask-free** — flip one bit,
see where the effect arrives. There is no threshold to share, no boundary to
draw, and nothing for a null to fail to control.

**Method.** Run the river to t*=60, fork, flip a single phenotype bit at site
x, continue both branches to T=120, and record which cells differ. Forking is by
re-seeding: `java.util.Random` is deterministic from its seed and the per-step
draw count is fixed (one `nextInt` per cell), so both branches consume the exact
same tape and any divergence is the causal effect of the flip. The matched
control is `run-river-ablated-from` — identical seed, tape, construction and
initial state, with only the live X→G edge cut. Figure:
`figures/river_perturbation.{png,pdf}`.

**Single-seed sweep (seed 1, all 80 sites):**

| dt | river mass | ablated mass | river spread | ablated spread |
|---|---|---|---|---|
| 1 | 1.35 | 1.49 | 0.66 | 0.68 |
| 10 | 3.31 | 3.23 | 4.19 | 3.46 |
| 20 | 5.33 | 3.89 | 7.12 | 4.51 |
| 40 | 11.74 | 4.44 | 13.00 | 6.04 |
| 59 | **12.97** | **5.51** | **16.95** | **7.46** |

The two are identical at dt ≤ 10 — as they must be, since the feedback has not
yet acted — and diverge thereafter.

**Replication (6 seeds, 20 sites each, dt=59):** river exceeds ablated in
**6/6 seeds**; ratios 1.11–2.56; mean difference **+5.00** (sd 2.90),
paired **t(5) = +4.23**, p ≈ 0.008.

**This one replicates**, unlike §11. The live phenotype→genotype edge causally
increases how far a single-bit perturbation travels.

**Two things it does not yet show.**

1. *Not ballistic.* Spread reaches ~17 cells against a light-cone bound of 59.
   Propagation is enhanced but sub-ballistic — a more conductive medium, not
   demonstrably a coherent particle traversing at fixed velocity. Calling the
   river "a glider" is not yet earned; "a channel with higher conductivity than
   its feedback-cut control" is.
2. *Not yet localised to identifiable bands.* 22/80 sites are dead ends and they
   are spatially **contiguous** (≈64→13 wrapping), so part of the lattice
   conducts and part does not. Propagating sites have higher local phenotype
   alternation (0.256 vs 0.143, corr +0.349) — suggestive but not strong enough
   to say the damage follows the visible bands.

**What would finish it.** Track the damage centroid over dt: a glider gives a
straight line at constant velocity, a conductive medium gives a spreading blob.
That single plot separates the two readings, and the data to make it already
exists.

### 12a. Centroid tracker — the river is a medium, not a particle

*(2026-07-27. The test proposed at the end of §12: a glider translates at
constant velocity with a packet of fixed width; a conductive medium disperses.)*

4 seeds × 10 sites, damage rows recorded at every $dt$ from 1 to 59.
Figure: `figures/river_centroid.{png,pdf}`.

| dt | river \|centroid\| | river RMS | ablated \|centroid\| | ablated RMS |
|---|---|---|---|---|
| 10 | 2.27 | 3.91 | 1.41 | 2.52 |
| 30 | 4.48 | 8.18 | 2.83 | 4.85 |
| 59 | 5.39 | **11.37** | 2.92 | **5.42** |

Scaling fits:

| | RMS ~ dt^a | \|centroid\| ~ dt^b |
|---|---|---|
| river | **a = 0.64** | **b = 0.56** |
| ablated | a = 0.43 | b = 0.43 |
| glider would give | a ≈ 0 | b = 1 |
| diffusion gives | a = 0.5 | b = 0.5 |

**Verdict: not a glider.** A glider keeps a fixed-width packet and translates at
constant velocity (a ≈ 0, b = 1). What we see is the opposite: the packet width
*grows* (a = 0.64) while the centroid merely *wanders* at b = 0.56 — the
random-walk exponent, i.e. no systematic drift at all. At dt = 59 the centroid
has moved 5.4 cells where a constant-velocity structure would have moved ~26.
Panel (c) shows it directly: the damage spreads symmetrically about the flip
site while the centroid jitters in place.

**What survives.** The conductivity result of §12 is unaffected and is the real
finding: the live X→G edge roughly doubles the spread (11.37 vs 5.42) and lifts
the exponent from sub-diffusive (0.43) to super-diffusive (0.64), replicated
6/6 seeds. The river is a **medium whose conductivity the feedback controls**,
not a signal carrier.

**Why the distinction matters for the paper.** Gliders make CA computation work
(Rule 110's universality) precisely because they deliver a signal *to a specific
place at a specific time*. A superdiffusive medium spreads influence without
addressing it. So "if we knew how to form a river we could send information
across space" is half-supported: the river does transmit causal influence
further than its control, but not in the targeted, addressable way that would
make it a computational primitive.

### 12b. CORRECTION to 12a — the glider criterion was uncalibrated

*(2026-07-27. Prompted by the observation that a Rule-110-dominated field should
serve as a positive control for the whole apparatus.)*

§12a asserted that a glider signature is a ≈ 0 (fixed packet width) with b = 1
(constant-velocity centroid), and concluded from the river's a = 0.64, b = 0.56
that it "is not a glider". **That reference was reasoned, not measured, and it
is wrong.**

Same estimator, same W=80, same t*, 6 seeds × 10 sites, applied to known ECAs:

| rule | RMS ~ dt^a | \|centroid\| ~ dt^b | RMS at dt=59 |
|---|---|---|---|
| **110** (gliders, universal) | **0.71** | **0.73** | **15.02** |
| 30 (chaotic) | 0.87 | 0.23 | 24.14 |
| 90 (chaotic, XOR) | 0.61 | — | 8.31 |
| 204 (identity) | — | — | **0.00** |
| 0 (dead) | — | — | **0.00** |
| river (measured) | 0.64 | 0.56 | 11.37 |
| ablated (measured) | 0.43 | 0.43 | 5.42 |

Rule 110 does **not** give a ≈ 0, b = 1. Damage spreading in a glider-bearing
medium is not a translating packet: the perturbation creates and destroys
gliders which then collide, producing a spreading cone. The a≈0/b=1 signature
belongs to tracking a single isolated glider, which is not what a
random-site damage experiment measures.

**Consequences.**

1. The "not a glider" verdict of §12a is **withdrawn**. Against the correct
   reference the river (0.64, 0.56) is comparable to Rule 110 (0.71, 0.73),
   slightly weaker and slightly less directed, but the same character.
2. **The apparatus is validated.** Identity and dead rules give exactly 0.00
   spread — the instrument reports nothing when nothing is there — and the live
   rules order sensibly:
   dead 0.00 < ablated 5.42 < Rule 90 8.31 < river 11.37 < Rule 110 15.02 <
   Rule 30 24.14. The river falls in the live-and-complex band.
3. §12's conductivity result is untouched and remains the positive finding.

**Methodological note.** This is the value of a positive control: every result in
§§1–12 was a negative, and a battery of negatives is only informative if the
instrument registers a positive where one is known to exist. The first time that
control was run it overturned a published-in-notes conclusion of ours. Any
remaining negative in this document should be read as provisional until the same
control has been run for that specific measurement.

### 14. Sustained diversity predicts causal propagation (the λ-analogue test)

*(2026-07-27. Joe's proposed test: does diversity, as a λ-analogue, correlate
with the empirical causal measurement?)*

15 operators (7 rotations, two-4-cycle, σ=16250374, 6 arbitrary permutations),
6 seeds each, one genotype bit flipped at $t^*=20$ **while every operator is
still alive**, damage read at $dt=30$. Loop verified to reproduce
`run-propagator` exactly (full genotype trace, `MATCH true`) — the earlier
ladder was void because it applied a conjugation `run-propagator` does not.

| | Spearman ρ | permutation p | n |
|---|---|---|---|
| sustained diversity vs **genotype** damage | **+0.918** | **<0.0001** | 15 |
| sustained diversity vs **phenotype** damage | −0.025 | 0.93 | 15 |

The relationship is strong, and it is specific to the genotype layer — the
phenotype null is the control that says this is not a generic artefact of the
perturbation protocol.

**But it is monotone, not peaked** *over the range we sampled* — and that range
is far narrower than it looked (Joe, 2026-07-27: "what *percentage* is 48?").

At $W=80$ the top operator sustains 48.2 rules. That is 18.8% of the 256-rule
space but **60% of the 80 lattice cells**, and the lattice is a hard ceiling.
Diversity turns out to be lattice-limited, not intrinsically limited — it does
not saturate:

| operator | W=80 | W=160 | W=320 | W=640 |
|---|---|---|---|---|
| rot+1 | 35.7 | 56.0 | 67.0 | 98.7 |
| two4cyc | 39.3 | 71.7 | 99.3 | 137.0 |
| p-a | 47.0 | 86.0 | 118.3 | 154.7 |

At $W=640$ the top operator reaches 61% of the rule space. **The correlation
therefore sampled only the bottom fifth of the available diversity range.** A
turnover, if there is one, would be expected in the high-diversity regime we
never reached, so "no turnover" is not a finding — it is an artefact of lattice
size.

**Worse: the correlation is largely a two-cluster effect.** Sustained diversity
at $W=80$ is bimodal — seven operators at $\le 4.3$, eight at $\ge 13.7$, with a
gap of 9.4 and nothing in between. Splitting it:

| subset | ρ | permutation p |
|---|---|---|
| all 15 | +0.918 | <0.0001 |
| live only (n=8) | +0.714 | 0.059 |
| collapsed only (n=7) | +0.857 | 0.045 |

Mean genotype damage is 0.07 for the collapsed cluster against 1.50 for the
live one. So ρ=0.918 is substantially "collapsed operators do not propagate,
live ones do" — a real but much weaker statement than a graded law, and the
within-live relationship does not reach significance at n=8.

**Revised status:** diversity separates propagating from non-propagating
operators. Whether it *grades* causal reach among live operators is unresolved,
and the high-diversity regime is unsampled. Any rerun needs a larger lattice and
operators spanning the gap.

That is consistent with — and strengthens — the paper's existing negative:
Langton's λ is silent here (every fixed point sits at 1/2 by the classification),
the finite-size scan finds a broad drifting crossover and no critical point, and
now the live coordinate turns out to be monotone rather than critical. Three
independent routes, same conclusion: there is no critical point in this family.

**Caveat on independence.** Diversity and damage are different measurements on
different objects (one branch's rule variety vs two branches' divergence), so
this is not the circularity of §3. But they are not mechanically independent
either: a field collapsed to one rule has nothing for a perturbation to
propagate *through*, so some of the correlation is likely substrate rather than
regime. Distinguishing those needs a diversity-matched control.

`scripts/diversity_vs_damage.clj`.

## 15. Figure 4 ground truth: neither segmentation finds the known stripes

*(2026-08-02. Deterministic seed 1; analysis seed 20260802.)*

The all-even involution $\sigma=[2,3,0,1,5,4,7,6]$ was regenerated as raw
integer genotype and phenotype fields with
`clojure -M -m mmca.figures figshell`.  The transient endpoint was located
without looking at either segmentation: row 52 is the earliest row from which
the genotype is one unchanged two-rule spatial pattern through the end of the
run.  The surviving rules are 105 and 201.  On the periodic width-80 lattice,
the exact change columns are **4, 17, 30, and 61**, producing cyclic stripe
widths 13, 13, 31, and 23.

Both masks were scored at exact pixel resolution on rows 52--120.  The common
spatial support excludes one column at each edge because held-out selection
chose LCS depth 1.  There are 276 true boundary cells in that support.

| method | predicted cells | precision | recall | F1 |
|---|---:|---:|---:|---:|
| TINT (paper threshold 0.35) | 552 | **0.250** | **0.500** | **0.333** |
| LCS coherent structures | 0 | **0.000** | **0.000** | **0.000** |

TINT does not recover the exact vertical boundaries reliably.  Its five-cell
smoothing shifts the two descending rule-activity transitions inward by two
columns; the stationary mask is at columns 4/5, 15/16, 30/31, and 59/60 rather
than the exact changes at 4, 17, 30, and 61.  LCS has 75 retained points over
the full six-seed reconstruction but none in seed 1's stationary tail.  A
method that misses this certainly-present boundary is not a reliable boundary
detector under this reconstruction.

### 15a. The widened LCS grid still selects both low edges

Selection retained the existing seed-held-out procedure (six seeds, three
folds, burn-in 20), but widened the candidates prospectively to depths 1--4
and tolerances 0.02, 0.05, 0.1, 0.2, and 0.4.  The selected point was
**depth 1, tolerance 0.02**, held-out loss **1.508489 bits/bit** over 46,800
predictions.  It is still at the low edge of both grids.  Thus widening did not
produce an interior optimum; it moved tolerance selection from the old low
edge 0.1 to the new low edge 0.02.  This is evidence that the LCS model class or
search range remains under-resolved, not permission to widen or tune again
toward a better-looking boundary score.

The exact grid initially took 45 minutes without completing because the legacy
selector refit the same Naive Bayes model once per tolerance.  The implementation
was changed to fit once per held-out fold and share the tolerance-independent raw
predictions; quantisation and causal-state fitting remain tolerance-specific.
All legacy candidate losses on the regression fixture match bit-for-bit.  The
full unchanged 4-by-5 grid then completed in about eight minutes.

### 15b. Phenotype transport is lower across the known boundary

For each exact genotype boundary edge, phenotype transfer was measured in both
directions (left cell to right destination and right cell to left destination)
over the stationary tail.  Same-rule edges supply the known-interior comparison:

| edge class | directed phenotype transport |
|---|---:|
| exact genotype boundary (552 directed samples) | **-0.088819** |
| genotype interior (10,488 directed samples) | **+0.027859** |
| boundary minus interior | **-0.116679** |

The certainly-present walls therefore do not carry enhanced phenotype
information under this measure; the contrast has the opposite sign.  Combined
with §7, the publishable result is negative: neither an estimated tint wall nor
an exact known wall supports the claim that genotype boundaries carry phenotype
transport in these examples.

Artifacts: `src/mmca/experiments/figshell_ground_truth.clj`,
`scripts/figshell_ground_truth.py`, `data/figshell_lcs_candidates.tsv`,
`data/figshell_lcs_config.tsv`, `data/figshell_lcs_mask.txt`, and
`data/figshell_ground_truth.tsv`.

## 16. Full-resolution overlays: LCS marks predictive regions, not walls

*(2026-08-02. Part C; analysis seed 20260802.)*

The LCS masks were finally placed directly on the paper's exact display fields.
Hyperparameters and causal states were learned from the existing six-seed
ensembles, then applied without refitting to the seed-1 display runs.  The three
Figure 13 panels use genotype-past to genotype-future LCS, matching the earlier
tint comparison.  The authentic river uses joint $(G,X)$ past and future,
matching E5.  Selection remained three-fold seed-held-out on the declared
depth `{1,2}` by tolerance `{0.1,0.2,0.4}` grid.

| display field | target size | selected $(d,\tau)$ | held-out loss | coherent cells | structures |
|---|---:|---:|---:|---:|---:|
| offset $+1$ | 256×600 | (1, 0.10) | 1.558510 | 45,076 | 550 |
| two 4-cycles | 256×600 | (1, 0.10) | 1.538839 | 25,968 | 254 |
| $\sigma=16250374$ | 256×600 | (1, 0.10) | 1.699014 | 23,341 | 476 |
| river, joint LCS | 240×360 | (1, 0.10) | 1.004980 | 52,000 | 171 |

Bright-green overlays make the relationship to the activity tint inspectable.
For each tint panel, every retained LCS cell was classified as lying on the
paper's threshold-gradient boundary, inside the active domain away from a
boundary, or inside the inactive domain away from a boundary.  Parentheses give
the fraction expected from the area of that class; enrichment is observed over
expected.

| field | tint boundary | active interior | inactive interior |
|---|---:|---:|---:|
| offset $+1$ | 18.7% (16.7%; 1.12×) | 25.4% (36.6%; 0.69×) | **55.9%** (46.7%; 1.20×) |
| two 4-cycles | 16.5% (10.4%; 1.59×) | 18.1% (56.8%; 0.32×) | **65.4%** (32.9%; 1.99×) |
| $\sigma=16250374$ | 21.3% (14.0%; 1.52×) | **54.4%** (73.8%; 0.74×) | 24.3% (12.2%; 2.00×) |

The answer is therefore neither "the same boundaries" nor "spatially
unrelated."  Boundaries are modestly enriched in every panel, but **79--84% of
LCS cells are not on a tint boundary**.  Their domain preference is also
operator-dependent: the first two masks concentrate in inactive interiors,
whereas the third has most of its absolute mass in active interiors.  The
green regions follow extended predictive textures crossing and occupying tint
domains rather than tracing their edges.

The river result is even less particle-like at display scale: joint LCS covers
**62.8%** of its valid post-burn-in support.  The mask tracks broad evolving
regions on both layers, not a sparse set of coherent filaments.  Together with
the exact Figure 4 miss in §15, the overlays support the narrower reading that
this reconstruction finds relatively uncommon predictive-state regions.  It
does not operationalize "wall" or "filament" consistently across these fields.

The exact four-dataset run completed locally in 14m43s at 96% of one core,
peaking at 2.41 GB RAM.  Outputs are deterministic raw masks plus
`data/lcs_overlay_config.tsv`, `data/lcs_overlay_candidates.tsv`,
`data/lcs_overlay_summary.tsv`, and
`figures/lcs_overlay_{tint,river}.{png,pdf}`.  Entry points are
`mmca.experiments.lcs-overlays` and `scripts/plot_lcs_overlays.py`.
