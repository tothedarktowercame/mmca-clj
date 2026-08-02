# M-metaca-eoc — Edge-of-chaos measurement program for the MetaCA operator family

**Type:** Mission with Excursions (not a full Campaign — simpler).
**Owner:** claude-2 (design/review). **Basis:** the reproducible Tier-1 suites
`~/code/mmca` (elisp ground truth) and `~/code/mmca-clj` (grid-identical pure
Clojure). **Paper:** *A New Family of Operators on Cellular Automata*
(`futon5/holes/tech-notes/paper/draft2.tex`).

## Why this exists

The paper currently infers "edge of chaos" from rule *diversity* and visually
rich diagrams. That is not evidence of critical dynamics (Codex review,
2026-07-18). This mission replaces the hedge with measurement: each Excursion is
one of Codex's methodology headlines, run on the reproducible suite. Two goals
at once: (1) get real EoC/computation results; (2) **stress-test the suite** —
can *fresh* agents actually use and extend it? So Excursions are belled out to
individual Codex agents (NOT codex-9, who authored the plan) and to Zai agents.

## Structural facts that shape every Excursion

- **The base `run-propagator` is feedforward.** `phenotype-step` reads the
  genotype (G→X live); `genotype-step` ignores the phenotype (**X→G ≡ 0**).
  Only `run-river` reads the phenotype back (X→G live). So the base engine is
  the **null** and the river is the **treatment**; their difference isolates the
  feedback.
- **Same-randomness replay is free.** A propagator write draws one
  genotype-independent position per cell, so two fork branches consume the
  identical RNG tape (`mmca.rng` reproduces Emacs's `(random "prop-N")` stream).
  Any divergence after a fork is the *causal* effect of the intervention.
- **The live/dead λ=1/2 control pair exists** (paper §"A dead genotype can carry
  a live phenotype"): Rule 105 (live, additive) vs Rule 204 (dead, identity),
  both balanced fixed points of the same operator. Isomorphic propagator
  dynamics, opposite neighbourhood semantics — Codex's built-in scalpel.

## Reproducibility discipline (the bar every Excursion clears)

1. One runnable, **seeded, deterministic** entry point in `mmca-clj` (a
   `mmca.experiments.*` namespace with `-main`), no hidden state.
2. A committed **result artifact** (data file + one-paragraph summary) that
   re-running reproduces bit-for-bit.
3. Gates: `clojure -M:test` stays green; the new namespace has at least one
   determinism test (same seed ⇒ same result); state the seed/width/steps.
4. Report back with commit shas + the headline number.

## Excursions

- **E1 — Paired-perturbation response.** *(DONE directly, as the exemplar —
  `mmca.experiments.paired-perturbation`.)* Fork a replay at t*, intervene
  (flip X-bit / flip rule-bit / swap 105↔204), measure the 4-channel response
  matrix R_{B←A}(Δt), A,B∈{G,X}. Assert X→G ≡ 0 on base (correctness), then run
  the river as treatment. Upgrades "high diversity" → "sustained *causal*
  propagation."
- **E2 — Real control parameter / L×q scan.** Expose a continuous interrupter
  strength `q = Pr(propagate vs blend/hold)`; scan q × widths L∈{30,60,120,240}
  × seeds; look for a sharp transition vs crossover vs metastability (Binder
  cumulants, susceptibility peak, finite-size collapse). Honest null =
  "long-lived crossover." *Needs a clean q seam in the engine.*
- **E3 — Transient scaling.** Treat the transient as the object: survival curves
  S(t;L,q)=Pr(T_collapse>t), collapse-time quantiles, hazard, scaling of median
  collapse time with L (log L vs polynomial vs e^{cL}). Many seeds (3 is far too
  few — collapse time is broad/multimodal).
- **E4 — Conditional predictive information.** Replace raw entropy/C_μ with
  directed, cross-validated predictive info: I(G⁻;X⁺|X⁻) and I(X⁻;G⁺|G⁻) via
  held-out lightcone predictors; plus surrogate tests (rule-label shuffle,
  spatial/temporal genotype shuffle, feedback-breaking).
- **E5 — Joint local causal states.** Reconstruct local causal states from joint
  (G,X) past lightcones vs each layer alone (Rupe–Crutchfield); count coherent
  structures, lifetimes, velocities; test whether joint predicts better than
  either layer.
- **E6 — Multiscale correlation.** Full spacetime spectra S_AB(k,ω) incl. the
  G–X cross-spectrum; four-point dynamical susceptibility for intermittent
  domains. Descriptive support, not proof of criticality.
- **E7 — Direct computation tests.** Measure Langton's three primitives
  directly — storage (decode an injected bit after delay τ), transmission
  (after distance d), modification (decode XOR of two inputs) — across q, on
  phenotype and genotype separately, held-out decoder only.

## Paper division (Codex, endorsed)

- **Current paper (draft2):** precise dynamics + death definitions, width/seed/
  horizon robustness, collapse survival curves, **E1**. Justifies "sustained
  causal propagation" without a criticality claim.
- **Supplement:** E2 (full L×q), E3, E4, nulls/ablations, estimator robustness.
- **Second paper** ("Criticality and computation in a coupled rule–state
  cellular system"): finite-size scaling + E5 and/or E7.

Recommended order: E1 → (E2,E3) decide *whether* there's a transition →
(E4,E5,E7) explain *what it computes*.

## Scoreboard (updated 2026-08-02)

All experiments are topic-named and deterministic.  The current repository gate
is green: `clojure -M:test` passes 91 tests / 231 assertions.

| Exc | file | headline result | commit |
|-----|------|-----------------|--------|
| E1 | `paired_perturbation.clj` | X→G≡0 null (ff correctness); live/dead scalpel | `7d331b9` |
| E2 | `control_param_scan.clj` | **metastability** — susceptibility does not sharpen with L; exact offset +1/+2 checks do not rescue a critical-point claim | `155569e`,`d5d3991`,`0d8fd9b` |
| E3 | `transient_scaling.clj` | collapse width-dependent, **right-censored** at large L | `155569e` |
| E4 | `directed_predictive_info.clj` | authentic river/matched ablation isolates **X→G = +0.2092 bits/site-step** | `756791b`,`16f9d77` |
| E5 | `local_causal_states.clj` | feedback-on has fewer inferred structures (90 vs 125) and worse joint held-out loss (1.016 vs 0.867); **no added causal-state richness** | `bd6f10b`,`270bc62` |
| E6 | `multiscale_spectra.clj` | authentic river/matched ablation retains an **off-DC S_GX feedback signature** | `fedb90b`,`b8ff2d3`,`6b8f2fb` |
| E7 | `direct_computation.clj` | authentic river/matched ablation finds weak storage/transmission but **no modification capacity under the registered probes** | `0b374e8`,`4300434` |
| A–C | phenotype transport + Figure 4 tint/LCS audit | phenotype transport null; exact stripe walls fail boundary-transport scoring; LCS structure is regional, not a wall detector | `9f35ab4`,`7a905f4`,`a555ceb` |
| — | (rename) | drop self-named files + shims | `8fa6b6c` |

**Convergent finding:** E4 (predictive information) and E6 (cross-spectrum)
independently detect the river's X→G feedback against matched ablations.  E5
does not find extra local-causal-state richness, and the corrective A–C audit
rejects the stronger interpretation that phenotype transport or LCS boundaries
explain the Figure 4 genotype stripes.  **Honest bounds:** E2 supports
metastability rather than a transition, E3 remains right-censored, and E7's
registered probes find only limited storage/transmission and no modification.
The evidence supports coupled feedback dynamics, but not the stronger
criticality, computation, or boundary-transport claims.

**Scope note:** this scoreboard covers the MetaCA dynamics/criticality mission.
The Baldwin-effect programme has separate registrations and run receipts under
`holes/BALDWIN-*` and `data/baldwin-runs/`; its paid Chicago runs are not MetaCA
mission excursions.

**Process findings:** fresh agents can use + extend the suite (E6 iterated on
review). All failures were infra (network / zai-persistence / 30-min job cap);
the git commit is the only durable artifact, so agents must commit incrementally.
