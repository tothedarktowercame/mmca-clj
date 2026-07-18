# E7 — Direct computation primitives (result)

Reproduce bit-for-bit: `clojure -M -m mmca.experiments.codex-8 > holes/E7-results.md`.
Determinism gate: `clojure -M:test`.
Config: feedforward base, train seeds 0–23, held-out seeds 24–47, W=81, T=8, tau_storage=8, tau_transmission=8, d=8, XOR inputs at +/- 4, decoder radius=2.

X injection forces one phenotype bit. G injection uses the balanced control pair Rule 105 (bit 1, live/additive) versus Rule 204 (bit 0, dead/identity). Each cell is a held-out nearest-centroid decoder accuracy with normalized capacity C=max(0,2*accuracy-1); chance accuracy is 50% and C=0.

| operator | parity class | layer | storage acc / C | transmission acc / C | XOR acc / C |
|---|---|---:|---:|---:|---:|
| rotate+2 | all-even | X | 45.8% / 0.0% | 47.9% / 0.0% | 51.0% / 2.1% |
| rotate+2 | all-even | G | 70.8% / 41.7% | 56.3% / 12.5% | 50.0% / 0.0% |
| offset+4 | all-even | X | 54.2% / 8.3% | 50.0% / 0.0% | 51.0% / 2.1% |
| offset+4 | all-even | G | 70.8% / 41.7% | 54.2% / 8.3% | 49.0% / 0.0% |
| reduced-0246 | any-odd | X | 47.9% / 0.0% | 50.0% / 0.0% | 51.0% / 2.1% |
| reduced-0246 | any-odd | G | 64.6% / 29.2% | 52.1% / 4.2% | 53.1% / 6.3% |
| reduced-02 | any-odd | X | 43.8% / 0.0% | 50.0% / 0.0% | 49.0% / 0.0% |
| reduced-02 | any-odd | G | 70.8% / 41.7% | 50.0% / 0.0% | 46.9% / 0.0% |

## Reading

These held-out probes separate retained, transported, and nonlinearly combined signal rather than treating visual diversity as computation. The phenotype and genotype rows are same-layer capacities: because the base engine is feedforward, phenotype injection is never interpreted as phenotype-to-genotype influence; only `run-river` could support that claim. The committed engine had no q API at this excursion's base revision (the E2 seam was present only as unrelated uncommitted work), so this reproducible artifact uses two representative all-even and two any-odd operators instead of silently depending on an unsettled seam. Genotype storage reaches C=41.7% and transmission reaches C=12.5%, while phenotype capacities are near zero; the maximum XOR capacity is only 6.3% (reduced-0246 G), so this sample finds no convincing modification capacity.
