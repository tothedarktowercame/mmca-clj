#!/usr/bin/env bash
# Reproduce every paper figure from mmca-clj alone -- no elisp engine,
# no sibling repo. Data generators live in src/mmca/figures.clj; plot scripts in
# scripts/. Run from anywhere; writes data/ and figures/.
set -euo pipefail
cd "$(dirname "$0")/.."

# 0. Refuse a plotting stack known to corrupt the PDF phenotype rasters.
python3 scripts/check_plot_environment.py

# 1. All ordinary figure data, followed by the synthetic order-edge-chaos sweep.
clojure -M -m mmca.figures all
clojure -M -m mmca.experiments.eoc-sweep

# 2. Render every figure as a 600-dpi PNG and a vector-based PDF with embedded
#    raster CA panels.
#    plot_eoc_phase reads the committed offset+1
#    finite-size scan (holes/E2b-offset1-finite-size-results.md).
for s in plot_fig1 plot_fig3 plot_fig4 plot_fig5 plot_fig6 plot_fig8 plot_fig2pair plot_figshell \
         plot_two4cycle plot_braid plot_knob plot_eoc_tint plot_eoc_interface plot_eoc_phase \
         plot_eoc_churn plot_eoc_patch plot_eoc_sweep; do
  python3 "scripts/$s.py"
done

# 3. Decode Figure 3's embedded PDF rasters and compare them cell-for-cell with
#    the generated standard-order phenotype data.
python3 scripts/check_fig2pair_pdf.py

echo "reproduced 17 paper assets as PNG and PDF files in figures/:"
echo "  rule110_spread fig8 fig2pair parity-dichotomy survey_a survey_b lambda_analogue"
echo "  river critical_shell two4cycle braid knob eoc_tint eoc_interface eoc_phase"
echo "  eoc_patch edge_of_chaos_curve"
