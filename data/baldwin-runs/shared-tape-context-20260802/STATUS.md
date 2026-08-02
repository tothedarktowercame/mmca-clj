# Shared-tape context diagnostic — 2026-08-02

The preregistered prerequisite classified the result as
`:no-shared-tape-context-gain`.

| Cell | Stable contexts | Captured observations | Total observations | Capture |
|---|---:|---:|---:|---:|
| Variable rewrite tape | 4 / 16 | 7,500 | 37,440 | 2,003 bp |
| Shared rewrite tape | 3 / 16 | 5,637 | 37,440 | 1,505 bp |

The material gate required at least 4 stable contexts and 2,500 basis points
of capture under the shared tape.  The weaker improvement outcome required
both stable-context count and capture to increase.  Neither condition held.
The fixed-p0/shared-tape apparatus control was exactly reproducible.

This is a preregistered stop for the six-arm paid study, not evidence against
the Baldwin effect in general.  It says that the proposed four-bit context
coordinate did not become more invariant when rewrite-tape variation was
removed in this diagnostic.

Provenance:

- Source revision: `f893a4005842ca0ceeb472020990ed131bb2de67`
- Input SHA-256: `5fa7c5b5e26066cbd58f450bef7de270e34b4ef0d1a7490a2bfe1dc924816f24`
- Environment seeds: `1..8`
- Shared rewrite seed: `20260802`
- Two independent extractions were byte-identical:
  `661b380d648adb2234d0cc848edf9ce5ad3d272f71b1cbc6c3c896a092cac39b`

The older 14% figure was the unweighted mean modal share across all contexts.
This preregistration instead uses the explicitly defined captured-observation
rate for stable modes, retaining numerator and denominator to avoid a hidden
change of estimand.
