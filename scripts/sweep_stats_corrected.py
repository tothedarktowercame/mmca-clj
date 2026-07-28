"""Every number quoted in the paper's edge-of-chaos section, from one run.

Reads data/sweep_corrected.npz (see sweep_corrected.py). Run from the repo root.
"""
import numpy as np
from scipy import stats

d = np.load("data/sweep_corrected.npz", allow_pickle=True)
n = [str(x) for x in d["names"]]
te, sy, raw, surr, bnd = d["te_c"], d["syn_c"], d["te_raw"], d["te_surr"], d["bnd"]
i = n.index
PK = "q0.4"

def ms(a):
    return a.mean(), a.std(ddof=1) / np.sqrt(len(a))

print("== peak and bare operator (filament-specific, bits) ==")
for k in (PK, "q1.0"):
    m1, e1 = ms(te[i(k)]); m2, e2 = ms(sy[i(k)])
    print(f"  {k}: transport {m1:+.4f} +/- {e1:.4f}   combining {m2:+.4f} +/- {e2:.4f}")

print("\n== peak vs others (Welch) ==")
for o in ("q0.3", "q0.5", "q0.7", "q1.0"):
    t1, p1 = stats.ttest_ind(te[i(PK)], te[i(o)], equal_var=False)
    t2, p2 = stats.ttest_ind(sy[i(PK)], sy[i(o)], equal_var=False)
    print(f"  {PK} vs {o}: transport t={t1:+6.2f} p={p1:.1e} | combining t={t2:+6.2f} p={p2:.1e}")

print("\n== chaotic flank: sign of filament-specific transport (one-sample t vs 0) ==")
for o in ("p0.2", "p0.4", "p0.7", "p1"):
    t1, p1 = stats.ttest_1samp(te[i(o)], 0)
    t2, p2 = stats.ttest_1samp(sy[i(o)], 0)
    print(f"  {o}: transport {te[i(o)].mean():+.4f} t={t1:+6.2f} p={p1:.1e} | "
          f"combining {sy[i(o)].mean():+.4f} t={t2:+6.2f} p={p2:.1e}")

print("\n== chaotic-flank monotone decay (Spearman, per-seed) ==")
ch = [x for x in n if x.startswith("p")]
for lab, arr in (("transport", te), ("combining", sy)):
    xs = np.repeat(np.arange(len(ch)), arr.shape[1])
    ys = np.concatenate([arr[i(c)] for c in ch])
    r, p = stats.spearmanr(xs, ys)
    print(f"  {lab}: rho={r:+.3f} p={p:.1e} n={len(xs)}")

print("\n== bare operator against its nulls (q1.0, 8 seeds) ==")
t, p = stats.ttest_ind(raw[i("q1.0")], surr[i("q1.0")], equal_var=False)
m, e = ms(raw[i("q1.0")])
print(f"  raw on-filament {m:+.4f} +/- {e:.4f}  surrogate {surr[i('q1.0')].mean():+.4f} "
      f"(t={t:.1f}, p={p:.1e})  mask-matched {(raw-te)[i('q1.0')].mean():+.4f}")

print("\n== excluded ordered extreme q0.0: surrogate outscores the real field ==")
t, p = stats.ttest_ind(raw[i("q0.0")], surr[i("q0.0")], equal_var=False)
print(f"  raw {raw[i('q0.0')].mean():+.4f}  surrogate {surr[i('q0.0')].mean():+.4f}  t={t:+.2f} p={p:.2f}")

print("\n== rise band q0.1-q0.4: filament fraction flat while the score climbs ==")
band = ["q0.1", "q0.2", "q0.3", "q0.4"]
print("  bndfrac:", np.round([bnd[i(k)].mean() for k in band], 3),
      " transport:", np.round([te[i(k)].mean() for k in band], 4),
      f" ratio {te[i('q0.4')].mean()/te[i('q0.1')].mean():.2f}x")
print(f"  filament fraction overall: q1.0 {bnd[i('q1.0')].mean():.3f} -> p0.7 {bnd[i('p0.7')].mean():.3f}")
