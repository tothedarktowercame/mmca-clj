#!/usr/bin/env bash
# Reproduce every paper figure from mmca-clj alone -- no elisp engine,
# no sibling repo. Data generators live in src/mmca/figures.clj; plot scripts in
# scripts/. Run from anywhere; writes data/ and figures/.
set -euo pipefail
cd "$(dirname "$0")/.."

full=0
case "${1:-}" in
  "") ;;
  --full) full=1 ;;
  -h|--help)
    echo "usage: scripts/reproduce_all.sh [--full]"
    echo "  --full  also rerun the resumable permutation census and finite-size scan"
    exit 0
    ;;
  *)
    echo "unknown argument: $1" >&2
    exit 2
    ;;
esac

PYTHON="${PYTHON:-python3}"
start_seconds=$SECONDS
mkdir -p data figures

# 0. Refuse a plotting stack known to corrupt the PDF phenotype rasters.
"$PYTHON" scripts/check_plot_environment.py

# 0b. The two longest resumable enumerations are explicit full-mode stages.
# Their checkpoints live under holes/ so interrupted runs can be resumed.
if (( full )); then
  echo "[box 1] running/resuming the complete 8! permutation census"
  clojure -M -m mmca.experiments.aliveness-census
  echo "[box 6] running/resuming the offset+1 finite-size scan"
  clojure -M -m mmca.experiments.offset1-finite-size
else
  echo "[box 1] SKIPPED expensive permutation census (use --full)"
  echo "[box 6] SKIPPED finite-size scan; plot uses the committed result (use --full)"
fi

# 1. All ordinary figure data, followed by the synthetic order-edge-chaos sweep.
clojure -M -m mmca.figures all
clojure -M -m mmca.experiments.eoc-sweep

# 2. Render every figure as a 600-dpi PNG and a vector-based PDF with embedded
#    raster CA panels.
#    plot_eoc_phase reads the committed offset+1
#    finite-size scan (holes/E2b-offset1-finite-size-results.md).
for s in plot_fig1 plot_fig3 plot_fig4 plot_fig5 plot_fig6 plot_fig8 plot_fig2pair plot_figshell \
         plot_two4cycle plot_braid plot_knob plot_eoc_tint plot_eoc_interface plot_eoc_phase \
         plot_eoc_churn plot_eoc_patch; do
  "$PYTHON" "scripts/$s.py"
done

# 2b. Edge-of-chaos information measures. These are the slow step (~15 min): each
#     stage recomputes local transfer entropy over all 160 sweep fields. Both
#     nulls are seeded per field, so every number below is run-invariant.
#     plot_eoc_sweep.py is deliberately NOT run: it plots the raw on-filament
#     statistic, which fails its own negative control (see its docstring).
python3 scripts/sweep_corrected.py         # -> data/sweep_corrected.npz
python3 scripts/plot_eoc_corrected.py      # -> figures/edge_of_chaos_curve.{png,pdf}
python3 scripts/sweep_stats_corrected.py   # reported statistics
python3 scripts/controls_corrected.py      # offset+1, dead offset+4, Rule-110 foil
python3 scripts/geom_null.py               # -> data/geom_null.npz
python3 scripts/geom_stats.py              # every claim under all three nulls

# 3. Decode Figure 3's embedded PDF rasters and compare them cell-for-cell with
#    the generated standard-order phenotype data.
"$PYTHON" scripts/check_fig2pair_pdf.py

# 4. Supplement Part II, in producer-before-consumer order. Every intermediate
#    is durable under data/; no analysis script relies on process-local /tmp.
echo "[boxes 8-9] causal perturbation and elementary-rule calibration"
clojure -M -i scripts/river_perturbation.clj
clojure -M -i scripts/river_perturbation_seeds.clj
clojure -M -i scripts/river_perturbation_rows.clj
"$PYTHON" scripts/plot_river_perturbation.py
"$PYTHON" scripts/plot_river_centroid.py

echo "[boxes 9-10] regime placement and coupling dial"
clojure -M -i scripts/regime_placement.clj > data/regime_placement.tsv
"$PYTHON" scripts/summarize_reproduction_data.py regime
"$PYTHON" scripts/plot_coupling_dial.py

echo "[box 11] river coupling-gain dial"
clojure -M -i scripts/river_gain.clj > data/river_gain.tsv
"$PYTHON" scripts/summarize_reproduction_data.py gain
"$PYTHON" scripts/plot_gain_curves.py

echo "[box 12] published seven-mechanism diversity axis"
PYTHON="$PYTHON" scripts/reproduce_diversity_axis.sh

echo "reproduced all 12 Supplement 1 finding datasets and paper assets."
echo "wall time: $((SECONDS - start_seconds)) seconds"
echo "ordinary assets:"
echo "  rule110_spread fig8 fig2pair parity-dichotomy survey_a survey_b lambda_analogue"
echo "  river critical_shell two4cycle braid knob eoc_tint eoc_interface eoc_phase"
echo "  eoc_patch edge_of_chaos_curve"
echo "Part II assets:"
echo "  river_perturbation river_centroid regime_placement gain_curves diversity_axis"
