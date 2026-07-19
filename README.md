# mmca-clj

A standalone, full-Clojure port of the Tier-1 MetaCA dynamics used by *A New
Family of Operators on Cellular Automata*. It is the Clojure counterpart of
`../mmca`, but does not load or shell out to Emacs and does not depend on
`futon5`.

## What is ported

- `run-propagator`: neighbour-agreement blending followed by one propagator
  write per genotype cell;
- `run-propagator-alone`: one propagator write per cell, without blending;
- `run-river`: the later centre-rule/Emacs-seed Figure 6 reconstruction;
- `run-original-paper-river`: the original `quad-4cand / firstMatch prop:rot2`
  coordinate with constant-zero fallback and literal Java seeds;
- standard Wolfram ECA phenotype evaluation with fixed-zero boundaries;
- the 2014 rule-table entry order, colour highlights, and the legacy
  head/tail/interior evaluation order;
- GNU Emacs 30/Linux's seeded `random` stream, implemented in pure Clojure.

The RNG matters: the seeded stream calls `(random "prop-N")`; `mmca.rng` folds
that string into a 32-bit seed, uses glibc's degree-31 additive generator, and
combines two draws for each bounded value, so a run is fully deterministic in the
seed. **Convention (standard Wolfram order throughout).** Operators act directly
on truth-table positions in Wolfram's standard neighbourhood order
(`111,110,...,000`): the `writing` vector *is* the position permutation, applied
with no re-ordering, and random genotypes are drawn as plain rule bytes. The
legacy 2014 truth-table order and the Emacs-exact reproduction shim have been
dropped, so trajectories are no longer grid-identical to the 2014 Elisp tree;
they are the standard-order dynamics the paper reports.

## Dynamics and boundaries

The ordinary Tier-1 system is feedforward:

```text
X(t+1) = per-cell ECA expression of G(t) over X(t)
G(t+1) = P_writing-once(neighbour-agreement-blend(G(t)))
```

There is no phenotype-to-genotype feedback in `run-propagator`. `run-river` is
named separately because its template construction does read the phenotype.
Both genotype and phenotype use fixed Rule-0/state-0 boundaries.

The public `writing` vector is positional in the original 2014 neighbourhood
order: source position `k` writes its complement to `writing[k]`. Before a run,
`positional-writing->neighbourhood-writing` conjugates it into the current
Wolfram order. This is the ordering-independent shim formerly called
`positional-sigma->neighbourhood-sigma`; it preserves the semantic operator
when the truth-table representation changes. A writing need not be bijective:
Figure 8 deliberately uses `[0 0 1 2 3 4 5 6]`.

## Use

Run the tests:

```sh
clojure -M:test
```

Generate every paper data file and the conclusion statistics:

```sh
clojure -M:figures
```

Generate one group directly:

```sh
clojure -M -m mmca.figures fig6
python3 scripts/plot_fig6.py
```

Reproduce the three edge-of-chaos Discussion figures from an empty `data/`
directory:

```sh
mkdir -p data figures
clojure -M -m mmca.figures eoc
python3 scripts/plot_eoc_phase.py
python3 scripts/plot_eoc_tint.py
python3 scripts/plot_eoc_interface.py
python3 scripts/plot_eoc_churn.py
```

This writes `figures/eoc_phase.png`, `figures/eoc_tint.png`, and
`figures/eoc_interface.png`, plus the activity-domain comparison
`figures/eoc_churn.png`. The plotters print the manuscript verification
statistics (three-way regime entropy, box-counting dimensions, and per-region
genotype churn) as part of the reproducible run. `clojure -M:figures` also
includes these datasets.

The isolated-rule tint is generated independently with periodic boundaries at
width 300 for 300 steps from `java.util.Random(1)`, averaging cell-change rate
over the final 150 steps. Generate just its 256-rule table with:

```sh
clojure -M -m mmca.figures activity-scores
```

The phase plot reads the committed, reproducible finite-size scan in
`holes/E2b-offset2-finite-size-results.md`; regenerate that scan with
`clojure -M -m mmca.experiments.offset2-finite-size`.

Generate the 36-seed river contact sheet:

```sh
clojure -M -m mmca.figures river-grid
python3 scripts/plot_river_grid.py
```

Reproduce the original paper's stripey river using its full typed coordinate
and literal Clojure/Java integer seeds (rather than the later Elisp `prop-N`
seed convention):

```sh
clojure -M -m mmca.figures original-river
python3 scripts/plot_original_river.py
```

This writes `figures/original_paper_river.png`. The original Figure 6 raster
was checked cell-for-cell: all 58,080 genotype cells and all 58,080 phenotype
cells across seeds 1--6 agree with this run.

Run one simulation from an EDN specification:

```sh
clojure -M -m mmca.cli \
  '{:mode :blending :writing [2 3 4 5 6 7 0 1] :seed 0 :width 80 :steps 120}'
```

The public functions return:

```clojure
{:death 120
 :rules 31
 :activity 2816
 :gen [[245 78 ...] ...] ; integer rule bytes, initial row included
 :phe ["1010..." ...]}   ; binary rows, initial row included
```

## Convention

Everything is in standard Wolfram neighbourhood order (`111,110,...,000`). A
`writing` such as offset $+2$ = `[2 3 4 5 6 7 0 1]` is the permutation of
truth-table positions in that order, applied directly. The golden-master tests in
`test/mmca/core_test.clj` are Wolfram-order regression masters (deterministic in
the seed), not ties to the 2014 Elisp engine. Note that which operators sustain a
high-diversity field is order-dependent: in this standard order the coprime
rotations (offset $\pm1,\pm3$; single 8-cycles) sustain, while offset $\pm2,\pm4$
settle onto fixed rules and collapse.

## Validation

Tests cover:

- exact Emacs random draws;
- Wolfram Rule-110 semantics;
- the all-even fixed-point count for rotate+2;
- exact short blending and river trajectories generated by Elisp;
- a golden master for the original paper's constant-zero river coordinate;
- a SHA-256 golden master over a complete width-80, 120-step Figure-1 run.
