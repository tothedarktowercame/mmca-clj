#!/usr/bin/env bash
# Regenerate the three Part IV figures from an empty directory.
#   scripts/reproduce_exotype_figures.sh [sheet-dir] [fig-dir]
# Runtime is roughly 25 minutes; every sheet is deterministic in its arguments.
set -euo pipefail
# The plotting stack is PINNED, exactly as in reproduce_all.sh. Matplotlib 3.6.3
# -- still the system default on some machines -- SHEARS these tall spacetime
# rasters in its PDF backend: vertical structure comes out diagonal, and no
# interpolation, dpi, aspect or rasterized setting avoids it. This script used to
# call bare python3 and so silently escaped the guard the rest of the figures have.
#   python3 -m venv .venv-figures
#   .venv-figures/bin/pip install -r requirements-figures.txt
PYTHON="${PYTHON:-$(if [ -x .venv-figures/bin/python ]; then echo .venv-figures/bin/python; else echo python3; fi)}"
"$PYTHON" scripts/check_plot_environment.py

SHEETS="${1:-data/exotype-sheets}"
FIGS="${2:-figures}"
mkdir -p "$SHEETS" "$FIGS"
gen () {  # beta kappa seed steps [--genotype]
  local tag="$1-$2-$3"
  # -s is not enough: a partially written sheet is non-empty and would be skipped.
  if [ "$(wc -l < "$SHEETS/$tag-phe.txt" 2>/dev/null || echo 0)" -eq "$4" ]; then
    echo "have $tag"; return; fi
  rm -f "$SHEETS/$tag-phe.txt" "$SHEETS/$tag-gen.txt"
  echo "generating $tag ($4 steps)"
  clojure -M scripts/exotype_sheet.clj "$1" "$2" "$3" "$4" "$SHEETS/$tag" ${5:-}
}
gen 2  0.0 2026102000 3000            # ridge from below  -> exo-bifurcation
gen 32 0.1 2026102000 3000            # ridge from above  -> exo-bifurcation
gen 8  0.1 2026102000 3000 --genotype # the valley        -> exo-lavalamp-spacetime + exo-bisection
gen 10 0.1 2026102000 3000            # bisection
gen 12 0.1 2026102000 3000
gen 14 0.1 2026102000 3000
gen 16 0.1 2026102000 3000
"$PYTHON" scripts/exotype_figures.py "$SHEETS" "$FIGS"
# Regression: the embedded raster must reproduce the sheet cell-for-cell. This is
# what catches a sheared or crushed panel, and it is why the figures are trusted.
"$PYTHON" scripts/check_exotype_pdf.py "$FIGS"
echo "done: $FIGS/exo-{bifurcation,bisection,lavalamp-spacetime}.{png,pdf}"
