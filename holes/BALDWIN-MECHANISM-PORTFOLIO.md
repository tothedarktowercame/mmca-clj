# Baldwin mechanism experiment portfolio

**Preregistered 2026-08-01, before reading the target-stationarity grid.**

This portfolio follows the review in
`futon5/holes/tech-notes/TN-baldwin-experiments-status.md` R1--R10.  It does
not spend either earlier experiment's confirmation seeds and does not revise
their classifiers.

## Stage A — free/local: target stationarity

For the best raw-band genome from the guidance pilot, measure its rule field at
`t* = 60` across environment seeds 1--8. Cross:

- variable versus the already preregistered fixed `p0`;
- variable rewrite tapes versus shared rewrite seed `20260802`.

The fully fixed cell must have exact agreement 1.0. Exact-rule chance is
`1/256`. Pairwise agreement is classified by the Lean decision rule in
`DarkTower/BaldwinMechanismPreregistration.lean`: 4% is the above-chance
screen and 25% is a materially encodable target. The result selects which
source of lifetime variation must be controlled; thresholds do not move.

## Stage B — cheap diagnostics, batched before evolution

These belong on one many-core box only after local smoke, because they share the
same evolved populations, seed/site panels, and field evaluator.

1. **Inherited-field sensitivity.** For every locus, test rules
   `[0, 30, 54, 90, 110, 154, 170, 204]` while the locus remains plastic.
   Record paired reach differences for every `(seed, site)`, not only arm means.
   The question is whether inherited alleles have a selection coefficient while
   rewriting is active.
2. **Context-indexed stationarity.** Re-index live/frozen rule agreement by the
   16 phenotype-context quadruples rather than by cell. Report each context's
   count, agreement rate, selected-rule entropy, and cross-seed stability.
3. **Full assimilation map.** Complete the existing `80 x 256` joint rule/hold
   sweep. The earlier paid battery covered only four least-damaging loci. This
   maps local assimilability but does not itself establish a path.

No evolutionary result is interpreted if Stage A's apparatus control fails or
if paired evaluation tapes diverge.

## Stage C — contingent paid evolutionary arms

The box should carry a portfolio, not a single arm. Which portfolio is launched
is fixed by Stages A and B:

### If a cell-indexed target is materially stationary

Run paired populations with the existing capacity-cost control and a realised
**usage cost** proportional to rewrite events. Cross each with ordinary mutation
and a prepare-then-fix operator carrying a latent inherited rule. Include a
mutation-only lifecycle null. The primary Phase-1 observable is increasing
agreement between inherited and learned fields while dependence remains high;
Phase 2 is held fraction exceeding the empirical mutation-only null with function
retained.

### If only a context-indexed target is materially stationary

Do not evolve an 80-cell hold mask. Evolve a 16-entry context table, with paired
capacity-cost and usage-cost arms plus a mutation-only null. The table is the
heritable unit; cell index is not.

### If neither representation is above chance

Do not run an assimilation search. Run the explanatory 2x2 stationarity sweep
across evolution seeds: fixed/variable `p0` by shared/variable rewrite tape. Its
outcome is the boundary at which a guidance witness becomes unavailable, not a
failed hunt for a witness on a targetless landscape.

## Guidance measurement repair

No arm reuses the absolute-timestep learning budgets `[0,4,16,64,120]` as a
learning curve. Preparation ends at `t*`; measurement-window rewriting is held
identical across treatments. The new continuous primary readout is paired raw
reach or time-to-threshold during preparation. Complex-band membership remains
a separate functional endpoint gate.

## Replication and infrastructure

- Pilot evolution seeds remain distinct from the unspent registered confirmation
  seeds.
- Pair on `(evolution seed, evaluation seed, site, task)`.
- One box may run all admitted arms sequentially; each arm gets the whole CPU set.
- Every terminal state, including validation failure, is banked before deletion.
- A run-specific expiring Linode token replaces broad remote credentials.
- Chicago dead-man and the local watcher are mandatory before the first arm.
