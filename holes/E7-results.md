# E7 — Direct computation primitives (correction round: authentic river + matched control)

Reproduce bit-for-bit: `clojure -M -m mmca.experiments.direct-computation > holes/E7-results.md`.
Determinism gate: `clojure -M:test`.
Config: train seeds 0–15, held-out seeds 16–31, W=81, T=16. Scanned: tau∈[4 8 12], d∈[4 8 12], radius∈[1 2 3]. Default probe: tau=8, d=8, radius=2, XOR±4.

Three arms: **BASE** (feedforward positive control — a channel already known to store/transmit G-signal), **RIVER** (`c/run-river-from`, authentic paper river, live X→G feedback), **RIVER-ABLATED** (`c/run-river-ablated-from`, matched frozen-phenotype control — same seed/tape/construction, only the X→G edge cut). River-minus-ablated is the **isolated feedback capacity**. Each cell: accuracy / normalized capacity C=max(0,2·acc−1), with seed-accuracy interval [min, max] across held-out seeds. Chance = 50% / C=0.

## BASE arm (positive control)

If the base cannot recover an injected G-bit, the probe is broken.

| operator | class | layer | storage acc / C [seed range] | transmission acc / C [seed range] | XOR acc / C [seed range] |
|---|---|---:|---:|---:|---:|
| rotate+2 | all-even | X | 37.5% / 0.0% [0.0%, 50.0%] | 50.0% / 0.0% [50.0%, 50.0%] | 53.1% / 6.3% [50.0%, 100.0%] |
| rotate+2 | all-even | G | 71.9% / 43.8% [50.0%, 100.0%] | 53.1% / 6.3% [50.0%, 100.0%] | 48.4% / 0.0% [25.0%, 50.0%] |
| offset+4 | all-even | X | 53.1% / 6.3% [0.0%, 100.0%] | 50.0% / 0.0% [50.0%, 50.0%] | 50.0% / 0.0% [50.0%, 50.0%] |
| offset+4 | all-even | G | 71.9% / 43.8% [50.0%, 100.0%] | 59.4% / 18.8% [50.0%, 100.0%] | 50.0% / 0.0% [25.0%, 75.0%] |
| reduced-0246 | any-odd | X | 53.1% / 6.3% [50.0%, 100.0%] | 50.0% / 0.0% [50.0%, 50.0%] | 50.0% / 0.0% [50.0%, 50.0%] |
| reduced-0246 | any-odd | G | 68.8% / 37.5% [50.0%, 100.0%] | 53.1% / 6.3% [50.0%, 100.0%] | 48.4% / 0.0% [25.0%, 50.0%] |
| reduced-02 | any-odd | X | 56.3% / 12.5% [50.0%, 100.0%] | 50.0% / 0.0% [50.0%, 50.0%] | 50.0% / 0.0% [50.0%, 50.0%] |
| reduced-02 | any-odd | G | 78.1% / 56.3% [50.0%, 100.0%] | 50.0% / 0.0% [50.0%, 50.0%] | 50.0% / 0.0% [50.0%, 50.0%] |

## RIVER vs matched ablation (headline contrast)

| layer | primitive | river acc / C [seed range] | ablated acc / C [seed range] | isolated feedback C |
|---|---|---:|---:|---:|
| X | storage | 56.3% / 12.5% [0.0%, 100.0%] | 50.0% / 0.0% [0.0%, 100.0%] | 12.5% |
| X | transmission | 50.0% / 0.0% [50.0%, 50.0%] | 43.8% / 0.0% [0.0%, 50.0%] | 0.0% |
| X | modification | 43.8% / 0.0% [0.0%, 75.0%] | 48.4% / 0.0% [0.0%, 75.0%] | 0.0% |
| G | storage | 34.4% / 0.0% [0.0%, 100.0%] | 40.6% / 0.0% [0.0%, 100.0%] | 0.0% |
| G | transmission | 53.1% / 6.3% [50.0%, 100.0%] | 43.8% / 0.0% [0.0%, 50.0%] | 6.3% |
| G | modification | 50.0% / 0.0% [25.0%, 100.0%] | 48.4% / 0.0% [25.0%, 50.0%] | 0.0% |

## Parameter scans (river, G layer)

### Delay τ (storage)

| τ | river acc / C [seed range] |
|---:|---:|
| 4 | 56.3% / 12.5% [0.0%, 100.0%] |
| 8 | 34.4% / 0.0% [0.0%, 100.0%] |
| 12 | 53.1% / 6.3% [0.0%, 100.0%] |

### Distance d (transmission)

| d | river acc / C [seed range] |
|---:|---:|
| 4 | 46.9% / 0.0% [0.0%, 100.0%] |
| 8 | 53.1% / 6.3% [50.0%, 100.0%] |
| 12 | 50.0% / 0.0% [50.0%, 50.0%] |

### Decoder radius (storage)

| radius | river acc / C [seed range] |
|---:|---:|
| 1 | 43.8% / 0.0% [0.0%, 100.0%] |
| 2 | 34.4% / 0.0% [0.0%, 100.0%] |
| 3 | 46.9% / 0.0% [0.0%, 100.0%] |

## Reading

The BASE positive control confirms the decoder pipeline works: genotype storage and transmission reach substantial capacity, so a null on the river is interpretable. The RIVER-ABLATED matched control shares the river's exact Java seed, RNG tape, and construction — only the live X→G edge is cut — so river-minus-ablated isolates the feedback channel's contribution to each primitive. We scan delay, distance, and decoder radius rather than relying on a single T=8 point.

Under these probes, the isolated feedback capacity is reported honestly: if the XOR/modification column shows no capacity above chance across the scan, that is 'no modification capacity under these probes' — never 'no computation'. The river's feedback may express through channels these injection-decode probes do not capture (e.g. distributed information flow measured in E4, or spectral signatures in E6).
