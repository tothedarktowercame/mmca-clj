"""Tint-threshold domains vs local-causal-state coherent structures.

Two ways of segmenting the SAME offset+1 genotype spacetime field:

  TINT  -- map each genotype byte to its isolated-rule activity score, smooth
           with a 5-cell uniform filter, threshold at 0.35, take the gradient of
           the thresholded field as the wall. This is what the paper does, and
           the recipe here is copied verbatim from controls_corrected.py /
           geom_null.py so the comparison is against the real thing.

  LCS   -- held-out local causal-state reconstruction (E5 machinery, retargeted
           by mmca.experiments.tint-vs-lcs). Coherent candidates are the states
           outside the fixed 80% background mass, after the small-component
           filter. Depth and quantisation tolerance were selected by seed-held-out
           likelihood, NOT by the resulting picture.

The paper's standing caveat is that the tint is a statistic of RULE SPACE, so
equal-tint neighbourhoods need not behave alike. This script asks two questions:

  1. Do the two segmentations agree geometrically? (Jaccard, kappa, distance-k)
  2. Does the paper's transport result survive substituting one for the other?

(2) is the one that matters. If filament-specific transport survives on
LCS-derived filaments, the tint caveat is cosmetic; if it does not, the caveat
is load-bearing and the paper's claim depends on the proxy.

Fields and masks come from data/tint_vs_lcs_offset1_s*_{field,lcs_mask}.txt
(W=64, T=120, burn-in 20, 6 seeds; see data/tint_vs_lcs_lcs_config.tsv).

Alignment: the LCS mask indexes field row t directly and is supported on
t in [20, 120), i in [1, 63). The tint wall drops the first row, so tint index
i corresponds to field row i+1. Both are therefore cropped to the common region
before anything is compared.

Run from the repo root. Writes data/tint_vs_lcs_compare.npz.
"""
import numpy as np, sys, pathlib
from scipy.ndimage import uniform_filter, binary_dilation

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sweep_surrogate import loc, scores, N_NULL

SEEDS = range(6)
BURN_IN, STEPS, MARGIN = 20, 120, 1
D_MIN = 12                      # matches geom_null.py
RNG_SEED = 20260727             # matches the analysis seed codex-7 recorded


def tint_masks(field):
    """The paper's tint pipeline, verbatim from geom_null.prep."""
    smooth = uniform_filter(scores[field], size=5)
    act = (smooth > 0.35).astype(np.int8)
    gt, gx = np.gradient((smooth > 0.35).astype(float))
    bnd = (np.hypot(gt, gx) > 0)[1:]
    dest, self_ = act[1:], act[:-1]
    L, R = np.roll(act[:-1], +1, axis=1), np.roll(act[:-1], -1, axis=1)
    tl, tr = loc(dest, [self_, L], [self_]), loc(dest, [self_, R], [self_])
    tj = loc(dest, [self_, L, R], [self_])
    return tl + tr, tj - (tl + tr), bnd


def crop_tint(a):
    return a[BURN_IN - 1:STEPS - 1, MARGIN:64 - MARGIN]


def crop_lcs(a):
    return a[BURN_IN:STEPS, MARGIN:64 - MARGIN]


def rand_null(vals, mask, rng):
    k, out = int(mask.sum()), []
    for _ in range(N_NULL):
        f = rng.permutation(mask.size)[:k]
        m = np.zeros(mask.size, bool); m[f] = True
        out.append(vals[m.reshape(mask.shape)].mean())
    return float(np.mean(out))


def shift_null(vals, mask):
    """Geometry-preserving: circular x-shift, averaged over |d| >= D_MIN. No RNG."""
    Fm = np.fft.rfft(mask.astype(float), axis=1)
    Fv = np.fft.rfft(vals, axis=1)
    prof = np.fft.irfft((np.conj(Fm) * Fv).sum(0), n=mask.shape[1]) / mask.sum()
    W = mask.shape[1]
    ds = [d for d in range(W) if D_MIN <= d <= W - D_MIN]
    return float(prof[ds].mean()), prof


def kappa(a, b):
    n = a.size
    po = (a == b).mean()
    pe = (a.mean() * b.mean()) + ((1 - a.mean()) * (1 - b.mean()))
    return (po - pe) / (1 - pe) if pe < 1 else float("nan")


def main():
    per_seed, store = [], {}
    for s in SEEDS:
        field = np.loadtxt(f"data/tint_vs_lcs_offset1_s{s}_field.txt", dtype=int)
        lcs_full = np.loadtxt(f"data/tint_vs_lcs_offset1_s{s}_lcs_mask.txt", dtype=int)
        te_f, syn_f, bnd_f = tint_masks(field)

        te, syn = crop_tint(te_f), crop_tint(syn_f)
        tint = crop_tint(bnd_f).astype(bool)
        lcs = crop_lcs(lcs_full).astype(bool)
        assert te.shape == tint.shape == lcs.shape, (te.shape, tint.shape, lcs.shape)

        inter = (tint & lcs).sum()
        union = (tint | lcs).sum()
        jac = inter / union if union else float("nan")
        kap = kappa(tint, lcs)
        # NB scipy dilates to convergence when iterations<1, so k=0 must be the
        # undilated mask itself, not binary_dilation(..., iterations=0).
        cover = {k: float((lcs & (tint if k == 0 else
                                  binary_dilation(tint, iterations=k))).sum() / lcs.sum())
                 if lcs.sum() else float("nan") for k in (0, 1, 2, 3)}

        rng = np.random.default_rng(RNG_SEED + s)
        row = {"seed": s, "jaccard": jac, "kappa": kap, "cover": cover,
               "tint_density": float(tint.mean()), "lcs_density": float(lcs.mean())}
        for nm, mask in (("tint", tint), ("lcs", lcs)):
            on_t, on_s = te[mask].mean(), syn[mask].mean()
            rnd_t, rnd_s = rand_null(te, mask, rng), rand_null(syn, mask, rng)
            sh_t, _ = shift_null(te, mask)
            sh_s, _ = shift_null(syn, mask)
            xs_t, xs_s = [], []
            for o in SEEDS:
                if o == s:
                    continue
                om = crop_lcs(np.loadtxt(
                    f"data/tint_vs_lcs_offset1_s{o}_lcs_mask.txt", dtype=int)).astype(bool) \
                    if nm == "lcs" else crop_tint(tint_masks(np.loadtxt(
                        f"data/tint_vs_lcs_offset1_s{o}_field.txt", dtype=int))[2]).astype(bool)
                if om.sum():
                    xs_t.append(te[om].mean()); xs_s.append(syn[om].mean())
            row[nm] = {"on_transport": float(on_t), "on_combining": float(on_s),
                       "rand": float(rnd_t), "shift": float(sh_t),
                       "xseed": float(np.mean(xs_t)),
                       "spec_rand": float(on_t - rnd_t),
                       "spec_shift": float(on_t - sh_t),
                       "spec_xseed": float(on_t - np.mean(xs_t)),
                       "comb_rand": float(on_s - rnd_s),
                       "comb_shift": float(on_s - sh_s),
                       "comb_xseed": float(on_s - np.mean(xs_s))}
        per_seed.append(row)
        store[f"tint_s{s}"] = tint; store[f"lcs_s{s}"] = lcs
        store[f"te_s{s}"] = te; store[f"syn_s{s}"] = syn

    print(f"offset+1, W=64 T=120 burn-in={BURN_IN}, comparison region "
          f"{crop_lcs(np.zeros((STEPS, 64))).shape}, {len(per_seed)} seeds, "
          f"rng seed {RNG_SEED}\n")
    print("AGREEMENT (tint wall vs LCS coherent structures)")
    print(f"{'seed':>4} {'tint':>7} {'lcs':>7} {'Jaccard':>9} {'kappa':>8} "
          f"{'d<=0':>7} {'d<=1':>7} {'d<=2':>7} {'d<=3':>7}")
    for r in per_seed:
        c = r["cover"]
        print(f"{r['seed']:>4} {r['tint_density']:>7.3f} {r['lcs_density']:>7.3f} "
              f"{r['jaccard']:>9.3f} {r['kappa']:>8.3f} "
              f"{c[0]:>7.3f} {c[1]:>7.3f} {c[2]:>7.3f} {c[3]:>7.3f}")
    mj = np.mean([r["jaccard"] for r in per_seed])
    mk = np.mean([r["kappa"] for r in per_seed])
    print(f"{'mean':>4} {'':>7} {'':>7} {mj:>9.3f} {mk:>8.3f} " +
          " ".join(f"{np.mean([r['cover'][k] for r in per_seed]):>7.3f}" for k in (0, 1, 2, 3)))

    print("\nTRANSPORT SUBSTITUTION -- filament-specific score against three nulls")
    print(f"{'mask':>6} {'seed':>4} {'on-filament':>12} {'-rand':>9} {'-shift':>9} {'-xseed':>9}")
    for nm in ("tint", "lcs"):
        for r in per_seed:
            d = r[nm]
            print(f"{nm:>6} {r['seed']:>4} {d['on_transport']:>+12.4f} "
                  f"{d['spec_rand']:>+9.4f} {d['spec_shift']:>+9.4f} {d['spec_xseed']:>+9.4f}")
        agg = {k: np.mean([r[nm][k] for r in per_seed])
               for k in ("on_transport", "spec_rand", "spec_shift", "spec_xseed")}
        print(f"{nm:>6} {'MEAN':>4} {agg['on_transport']:>+12.4f} "
              f"{agg['spec_rand']:>+9.4f} {agg['spec_shift']:>+9.4f} {agg['spec_xseed']:>+9.4f}\n")

    print("COMBINING (synergy) -- same three nulls")
    for nm in ("tint", "lcs"):
        agg = {k: np.mean([r[nm][k] for r in per_seed])
               for k in ("comb_rand", "comb_shift", "comb_xseed")}
        print(f"{nm:>6} {'MEAN':>4} {agg['comb_rand']:>+9.4f} "
              f"{agg['comb_shift']:>+9.4f} {agg['comb_xseed']:>+9.4f}")

    np.savez("data/tint_vs_lcs_compare.npz", **store)
    print("\nwrote data/tint_vs_lcs_compare.npz")


if __name__ == "__main__":
    main()
