# M-reproduce-eoc-figures — make mmca-clj regenerate the 3 new EoC paper figures

Goal (Joe): `~/code/mmca-clj` should contain everything that reproduces **all**
figures in the paper `futon5/holes/tech-notes/paper/draft3.tex`. The paper's
original figures already have generators (`mmca.figures` + `scripts/plot_*.py`).
The THREE new Discussion figures were built ad-hoc from `/tmp` and need the same
treatment. Match the existing convention exactly: `generate-*!` in
`src/mmca/figures.clj` writes `data/*.txt`; `scripts/plot_*.py` reads `data/` and
writes `figures/*.png`; document in `README.md`; wire into the `-main` dispatch
and `generate-all!`.

## 1. The isolated-rule activity score (the load-bearing new primitive)

Definition (calibrated by claude-2 to reproduce the paper's committed scores to
~0.006): the **asymptotic per-step cell-change rate of a rule run alone as an
elementary CA with PERIODIC boundaries**.

Exact params — pin these so the output matches the paper's numbers:
- width 300, steps 300, measurement (late) window = last 150 steps, seed 1.
- initial condition: random density-1/2 bits from `(java.util.Random. 1)`, i.e.
  `(repeatedly width #(.nextInt r 2))`.
- periodic ECA step using `mmca.core/rule-output` with wrap indices `(mod k w)`
  (NOT `phenotype-step`, which is zero-boundary — that mis-scores rules 30/45).
- score = mean over the last 150 steps of (changed-cell count / width).

Reference values to verify against (rule : expected): 0:0.00, 204:0.00, 8:0.00,
51:1.00, 110:≈0.421, 90:≈0.501, 30:≈0.496, 45:≈0.495, 150:≈0.501.

Add `generate-activity-scores!` → `data/rule_activity_scores.txt` (256 lines:
`<rule> <score>`).

## 2. Field generators (all via `mmca.core`; write `data/eoc_*.txt`, rows=time)

- **tint fields** (fig:tint): `run-propagator` W=256 T=600 seed 1 for
  offset+2 `[2 3 4 5 6 7 0 1]` and σ=16250374 `[1 6 2 5 0 3 7 4]`.
- **interface fields** (fig:interface): W=256 T=600 seed 1 for offset+2,
  offset+4 `[4 5 6 7 0 1 2 3]`, river (`run-river`); PLUS a finite-size series at
  W∈{128,256,512,768}, T=W+200 (crop last W = square), offset+2 seeds 1–3,
  offset+4 & river seeds 1–2.
- **phase examples** (fig:phase, bottom row): `run-propagator [2 3 4 5 6 7 0 1] 1
  240 300 {:interrupter-q q}` for q∈{0.0,0.05,0.25,0.75}.
- The phase SCAN data is already reproducible via
  `mmca.experiments.offset2-finite-size` → `holes/E2b-offset2-finite-size-results.md`.

## 3. Plot scripts (`scripts/plot_eoc_{tint,interface,phase}.py`)

Port claude-2's committed reference generator
`futon5/holes/tech-notes/paper/figures/gen_phase_diagram.py` (it already reads
committed data and does the logistic fit + panels) — that IS the phase plotter;
adapt paths to read `mmca-clj/data/` + `data/rule_activity_scores.txt` and write
`figures/eoc_phase.png`.

- **tint**: 2 panels (offset+2, σ=16250374), `imshow` of the field tinted by
  activity score, `cmap="coolwarm"`, vmin=0 vmax=1.
- **interface**: top row = boundary (black) of `score>0.35` after
  `scipy.ndimage.uniform_filter(size=5)`, for offset+2/offset+4/river at L=256,
  each labelled with box-counting D (box sizes [2,4,8,16,32,64], D = −slope of
  log N(ε) vs log ε); bottom = D vs L over the finite-size series.
- **phase**: the ported `gen_phase_diagram.py`.

## Acceptance / gates (this is a real review gate on return)
- Figures regenerate end-to-end from a clean `data/` via the documented commands.
- **Report back these numbers so claude-2 verifies them against draft3**:
  box-counting D for offset+2 and river at L=256 (paper says ≈1.5 / ≈1.7); the
  three-way regime-entropy for offset+2 and σ=16250374 (paper says 1.45 / 0.92
  bits, bands ordered<0.15 / complex 0.30–0.48 / chaotic>0.48). If any differ
  enough to move a paper number, FLAG it — do not silently diverge.
- clj-kondo clean; `futon4/dev/check-parens.el`; `clojure -M:test` green.
- Update `README.md` with the new figure commands.
- **Bell claude-2 back** with a summary, the verification numbers above, and
  commit shas.
