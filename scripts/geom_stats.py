"""Do the claims survive a geometry-preserving null?

Re-runs every test the paper reports, under all three nulls side by side:
random (size only), shift (exact geometry), xseed (geometry in distribution).

Reads data/geom_null.npz (see geom_null.py). Run from the repo root.
"""
import numpy as np
from scipy import stats

d = np.load("data/geom_null.npz", allow_pickle=True)
n = [str(x) for x in d["names"]]
A = d["data"]                       # (point, seed, 9)
i = n.index
BAND = ["q0.3", "q0.4", "q0.5", "q0.6", "q0.7"]
CHAOS = [x for x in n if x.startswith("p")]

# filament-specific value per (point, seed) under each null
SPEC = {"random": (A[:, :, 0] - A[:, :, 6], A[:, :, 1] - A[:, :, 7]),
        "shift":  (A[:, :, 0] - A[:, :, 2], A[:, :, 1] - A[:, :, 3]),
        "xseed":  (A[:, :, 0] - A[:, :, 4], A[:, :, 1] - A[:, :, 5])}

for null, (te, sy) in SPEC.items():
    print(f"\n{'='*74}\nNULL: {null}\n{'='*74}")

    print("  band q0.3-0.7 vs bare operator q1.0:")
    for lab, arr in (("transport", te), ("combining", sy)):
        b = np.concatenate([arr[i(k)] for k in BAND])
        t, p = stats.ttest_ind(b, arr[i("q1.0")], equal_var=False)
        print(f"    {lab:10s} band {b.mean():+.4f}  bare {arr[i('q1.0')].mean():+.4f}  "
              f"t={t:+6.2f} p={p:.1e}")

    print("  band vs ordered flank q<=0.2:")
    for lab, arr in (("transport", te), ("combining", sy)):
        b = np.concatenate([arr[i(k)] for k in BAND])
        o = np.concatenate([arr[i(k)] for k in ("q0.1", "q0.2")])
        t, p = stats.ttest_ind(b, o, equal_var=False)
        print(f"    {lab:10s} band {b.mean():+.4f}  ordered {o.mean():+.4f}  t={t:+6.2f} p={p:.1e}")

    print("  monotone decay over the chaotic flank (Spearman, per-seed):")
    for lab, arr in (("transport", te), ("combining", sy)):
        xs = np.repeat(np.arange(len(CHAOS)), arr.shape[1])
        ys = np.concatenate([arr[i(c)] for c in CHAOS])
        r, p = stats.spearmanr(xs, ys)
        print(f"    {lab:10s} rho={r:+.3f} p={p:.1e} n={len(xs)}")

    print("  sign on the chaotic flank (one-sample t vs 0):")
    for o in ("p0.2", "p0.4", "p0.7", "p1"):
        t1, p1 = stats.ttest_1samp(te[i(o)], 0)
        t2, p2 = stats.ttest_1samp(sy[i(o)], 0)
        print(f"    {o:>5}: transport {te[i(o)].mean():+.4f} p={p1:.1e}   "
              f"combining {sy[i(o)].mean():+.4f} p={p2:.1e}")

    print("  peak point and band interior:")
    m = [te[i(k)].mean() for k in n]
    pk = n[int(np.argmax([m[i(k)] if k in BAND else -9 for k in n]))]
    print(f"    largest transport in band at {pk} ({te[i(pk)].mean():+.4f} "
          f"+/- {te[i(pk)].std(ddof=1)/np.sqrt(te.shape[1]):.4f})")
    ns = [o for o in BAND if stats.ttest_ind(te[i(pk)], te[i(o)], equal_var=False)[1] >= 0.05]
    print(f"    not separable from it within the band (transport, p>=0.05): {ns}")

print(f"\n{'='*74}\nagreement between nulls (per-point filament-specific transport)")
r, p = stats.pearsonr(SPEC["shift"][0].mean(1), SPEC["random"][0].mean(1))
print(f"  shift vs random: r={r:+.4f}  mean abs difference "
      f"{np.abs(SPEC['shift'][0].mean(1)-SPEC['random'][0].mean(1)).mean():.4f} bits")
r, p = stats.pearsonr(SPEC["shift"][0].mean(1), SPEC["xseed"][0].mean(1))
print(f"  shift vs xseed:  r={r:+.4f}  mean abs difference "
      f"{np.abs(SPEC['shift'][0].mean(1)-SPEC['xseed'][0].mean(1)).mean():.4f} bits")
