#!/usr/bin/env bash
# Reproduce every paper (draft4) figure from mmca-clj alone -- no elisp engine,
# no sibling repo. Data generators live in src/mmca/figures.clj; plot scripts in
# scripts/. Run from anywhere; writes data/ and figures/.
set -euo pipefail
cd "$(dirname "$0")/.."

# 1. all figure data (fig1,3,4,5,6,8,figshell,stats + eoc tint/phase-examples/interface)
clojure -M -m mmca.figures all

# 2. render every draft4 figure. plot_eoc_phase reads the committed offset+1
#    finite-size scan (holes/E2b-offset1-finite-size-results.md).
for s in plot_fig1 plot_fig3 plot_fig4 plot_fig5 plot_fig6 plot_fig8 plot_figshell \
         plot_two4cycle plot_braid plot_knob plot_eoc_tint plot_eoc_interface plot_eoc_phase; do
  python3 "scripts/$s.py"
done

echo "reproduced 14 draft4 figures into figures/:"
echo "  rule110_spread fig8 parity-dichotomy survey_a survey_b lambda_analogue"
echo "  river critical_shell two4cycle braid knob eoc_tint eoc_interface eoc_phase"
