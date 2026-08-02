# Amended six-arm masking smoke

Status: **passed; launch-authorizing**.

The smoke used only pilot environment seeds `901–903`.  Reserved confirmation
environment seeds `101–108` remain unspent.  Both repetitions evaluated the
complete six-arm schedule: 16 registered loci × 3 environment seeds × 3 rewrite
tapes × 8 sites = 1,152 raw units per arm.

The two repetitions are byte-identical:

- `raw.edn`: `c4235ddd86aa2af3d4b17aa7055f9a5c8eb0fd623c07922eff765cd07148d378`
- `result.edn`: `b6416dc1f79a9016daf6f890ea1d1a89618c1e88eb2ee5a2401aa132b4c6c537`
- `manifest.edn`: `f9738a02f6c5d824c2e1c8cfc72b54f822b85438c179843897e1d651e3fce5de`

All receipt observations pass: panel rederivation, source hashes, six arms,
intervention separation, paired environment/tape/site schedule, within-locus
aggregation, recorded (non-gating) context-coordinate failure, deterministic
rerun, positive control, artifact completeness, checksums, and deadline.

Pilot outcome: `:mixed-evidence`.

- held-good vs plastic-good: 16–0–0
- held-current vs plastic-current: 16–0–0 (descriptive control)
- held-good vs held-current: 9–4–3
- held-good vs held-bad: 12–3–1
- plastic-good vs plastic-current: 11–4–1
- discovery-tape held-good vs novel-tape held-good: 7–7–2

The balanced novel-tape comparison does not indicate tape degradation.  The
pilot is not the registered confirmation and does not settle the Baldwin-effect
claim; it admits the paid confirmation under the 240-minute Chicago teardown
deadline.

Bindings:

- Clojure implementation: `fc8bfcaa3fd26ed78cec90b7183592af156925f2`
- Lean amendment: `1445d9bd4de70b532cccbd06927285588f96fc1d`
- Registration: `holes/BALDWIN-MASKING-SIX-ARM-AMENDED-PREREGISTRATION.edn`
- Receipt: `smoke-receipt.edn`
