# Pre-go-live check

Nothing that costs money starts until this passes. The point is to move every
discoverable failure to *before* the billing clock, because today's two most expensive
errors were both discoverable in under a second and were instead found hours later.

The check covers the apparatus **and** the experimental design. Splitting them is what
let a well-formed experiment run with a flag that did nothing, and a well-configured
box run an experiment that could not have shown the effect.

## A. Toolchain — resolve *and* exercise

Every external tool the run will eventually need, resolved and **executed** now, in the
environment the run will actually use.

- Resolving is not enough: a present-but-unauthorised CLI fails exactly like a missing
  one, at the moment it matters.
- The environment matters: a `systemd --user` unit gets a minimal `PATH` without
  `~/.local/bin`, so a tool that works in an interactive shell can be absent inside the
  unit. Check from inside the unit, not from the shell that launches it.
- This includes the teardown path. A watchdog that cannot destroy the box is worse than
  no watchdog, because it looks like teardown is handled.

## B. Code identity

- The remote is at the **expected commit** — assert the sha, do not assume `git pull`
  succeeded. A pull can fail on a dirty tree and leave the box running stale code while
  reporting nothing useful.
- There is one executable library source. Probes import it rather than loading a
  generated copy that can drift.
- Working tree is clean, or the diff is explicitly acknowledged.

## C. Experimental design

The existing gates, run against the actual parameters of *this* run:

- Every axis under selection is **non-constant** over its reachable range.
- Every axis carries **gradient across adjacent levels**, not merely two distinct
  values. A profile that is zero at every level but one has two values and a single
  cliff, and a hill-climber cannot cross it. Count adjacent transitions, not distinct
  values.
- A **no-selection null arm** is present whenever the claim has the form "rarer than
  chance", and any analytical baseline matches the lifecycle actually implemented.
- Arms that differ only in an inert parameter are flagged: if two arms cannot differ,
  their agreement is a design defect and not a robustness result.

## D. Flag efficacy — assert the effect, not the exit code

**This is the class that caused today's silent failure and is the main thing missing
from every check above.**

For each flag or option the run passes, there must be a stated, observable consequence
and a check that it occurred:

| flag | observable consequence |
|---|---|
| `--mode hold-only` | `gamma = 1`, `update-prob = 1`, and every mask bit stays live |
| `--mode static-search` | the hold-only invariants hold and every hold bit stays fixed |
| `--neutral 1` | selection is inert; ranking column is randomised |
| `--pin <g>` | `gamma` never leaves `<g>` |
| `--hgt 1` | donor ids appear in the record |

Two forms, both cheap:

1. **Unit** — call the function directly with the option set and assert the invariant
   (mutate with the pin on, assert `update-prob` unchanged). One assertion.
2. **Smoke** — run 2 generations, read the output, assert the consequence holds at
   generation 1. This catches wiring failures a unit test cannot, because it exercises
   the actual argument path from the command line to the function.

A flag with no efficacy assertion is not shipped. The failure mode being guarded is
specific and real: a patch that targets a code form which no longer exists no-ops
silently, the linter still passes because the result is valid code that ignores the new
parameter, and the run produces a plausible trajectory that answers a different
question than the one asked.

## E. Test surface

- The full suite passes.
- **Any flag or behaviour added since the last green run has a test.** Modifying a file
  that already has a test namespace without touching it is the specific gap to close.
- Lint is not a substitute. `clj-kondo` clean means the code parses, not that it acts.

## F. Budget

- Estimated wall-clock and cost are stated **before** launch, derived from a measured
  per-unit rate rather than a guess, and including sampling multipliers (an arm with
  4x the seeds and sites is 4x per generation, not the same).
- A hard teardown deadline exists and is independent of the run succeeding.

## Output

The check emits a single line per item and a final verdict. A failure names the item
and refuses to launch — it does not warn and proceed, because a warning at launch time
is read as noise and a refusal is not.
