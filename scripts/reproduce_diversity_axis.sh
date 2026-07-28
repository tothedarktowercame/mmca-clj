#!/usr/bin/env bash
# Rebuild the exact seven-mechanism dataset used by the committed published panel.
set -euo pipefail
cd "$(dirname "$0")/.."

PYTHON="${PYTHON:-python3}"
mkdir -p data figures

clojure -M -i scripts/full_population_sweep.clj > data/full_population_sweep.tsv
clojure -M -i scripts/mutation_axis.clj > data/mutation_axis.tsv
clojure -M -i scripts/diversity_dial2.clj > data/diversity_dial2.tsv
clojure -M -i scripts/diversity_dial3.clj > data/diversity_dial3.tsv
"$PYTHON" scripts/summarize_reproduction_data.py diversity
"$PYTHON" scripts/assemble_diversity_axis.py
"$PYTHON" scripts/plot_published_diversity_axis.py
