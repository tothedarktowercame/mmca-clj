"""Per-seed null-corrected transport and combining across the synthetic sweep.

Dumps every seed's corrected statistic so the figure can carry real SEMs and the
caption can carry real tests. Corrected statistic = on-filament score minus a
mask-matched random null (same cell count, drawn anywhere), which removes the
mask-degeneracy artefact: the extracted filament swells from 20% of the field at
the bare operator to 39% at p=1, so the raw on-filament mean drifts toward the
field mean for reasons unrelated to transport.

Also records the source-time-shuffle surrogate per point (plug-in estimator
bias) -- at the sparse ordered extreme q=0 the surrogate outscores the real
field, which is what the old "sparsity effect" caveat was.

Run from the mmca-clj repo root. Writes data/sweep_corrected.npz.
"""
import numpy as np, glob, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sweep_surrogate import measures

POINTS = ([(f"q{q:.1f}", f"data/eoc_sweep_order_q{round(q*1000):03d}_s*.txt")
           for q in [i / 10 for i in range(11)]] +
          [(f"p{p:g}", f"data/eoc_sweep_chaos_p{round(p*1000):03d}_s*.txt")
           for p in [0.01, 0.02, 0.05, 0.1, 0.2, 0.4, 0.7, 1.0]])

names, te_c, syn_c, te_raw, te_surr, syn_surr, bnd = [], [], [], [], [], [], []
for name, pat in POINTS:
    v = np.array([measures(f) for f in sorted(glob.glob(pat))])
    names.append(name)
    te_c.append(v[:, 0] - v[:, 2])      # transport minus mask-matched null
    syn_c.append(v[:, 3] - v[:, 5])     # combining minus mask-matched null
    te_raw.append(v[:, 0]); te_surr.append(v[:, 1])
    syn_surr.append(v[:, 4]); bnd.append(v[:, 6])
    print(f"{name:>6}  TE_corr {te_c[-1].mean():+.4f}  syn_corr {syn_c[-1].mean():+.4f}  "
          f"bndfrac {bnd[-1].mean():.3f}")
    sys.stdout.flush()

np.savez("data/sweep_corrected.npz",
         names=np.array(names), te_c=np.array(te_c), syn_c=np.array(syn_c),
         te_raw=np.array(te_raw), te_surr=np.array(te_surr),
         syn_surr=np.array(syn_surr), bnd=np.array(bnd))
print("wrote sweep_corrected.npz")
