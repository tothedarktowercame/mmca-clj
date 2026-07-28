"""Geometry-preserving nulls for the filament transport measures.

The mask-matched random null (sweep_surrogate.py) controls for the filament's
SIZE but not its shape: it scatters k cells uniformly, whereas a real filament is
a connected, near-vertical structure with a specific distribution over rows. Two
stronger nulls, both of which preserve that geometry exactly or in distribution:

  SHIFT   -- circularly translate the filament mask along x by d, and average
             over all d in [D_MIN, L-D_MIN]. The mask keeps its exact shape,
             connectivity, anisotropy, cell count, and per-row cell counts; only
             its registration against the field is destroyed. Fully
             deterministic -- no RNG at all. Computed for every d at once by FFT
             cross-correlation, which is exact (verified against the d=0
             identity, where the profile must equal the on-filament mean).

  XSEED   -- score the field of seed s under the filament mask extracted from a
             DIFFERENT seed s' at the same sweep point, averaged over the other
             seeds. Not the same mask, but an independent realisation of the same
             kind of object, so it preserves filament geometry in distribution.

D_MIN is set past the point where a shifted mask stops overlapping itself: the
smoothing kernel is 5 wide and measured overlap reaches chance by d~5, so d>=12
is conservative. The script reports the residual overlap excess so this is
auditable rather than asserted.

Run from the mmca-clj repo root. Writes data/geom_null.npz.
"""
import numpy as np, glob, sys, pathlib

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sweep_surrogate import loc, scores, _rng_for, N_NULL, rows
from scipy.ndimage import uniform_filter

D_MIN = 12


def prep(path):
    field = np.loadtxt(path, dtype=int)
    smooth = uniform_filter(scores[field], size=5)
    act = (smooth > 0.35).astype(np.int8)
    gt, gx = np.gradient((smooth > 0.35).astype(float))
    bnd = (np.hypot(gt, gx) > 0)[1:]
    dest, self_ = act[1:], act[:-1]
    L, R = np.roll(act[:-1], +1, axis=1), np.roll(act[:-1], -1, axis=1)
    tl, tr = loc(dest, [self_, L], [self_]), loc(dest, [self_, R], [self_])
    tj = loc(dest, [self_, L, R], [self_])
    return tl + tr, tj - (tl + tr), bnd


def shift_profile(vals, mask):
    """Mean of `vals` over `mask` circularly x-shifted by d, for every d at once."""
    Fm = np.fft.rfft(mask.astype(float), axis=1)
    Fv = np.fft.rfft(vals, axis=1)
    return np.fft.irfft((np.conj(Fm) * Fv).sum(0), n=mask.shape[1]) / mask.sum()


def rand_null(vals, mask, rng):
    k, out = int(mask.sum()), []
    for _ in range(N_NULL):
        f = rng.permutation(mask.size)[:k]
        m = np.zeros(mask.size, bool); m[f] = True
        out.append(vals[m.reshape(mask.shape)].mean())
    return float(np.mean(out))


def main():
    names, out, audit = [], [], []
    for name, pat in rows:
        paths = sorted(glob.glob(pat))
        P = [prep(p) for p in paths]
        per_seed = []
        for i, (te, syn, bnd) in enumerate(P):
            rng = _rng_for(paths[i])
            sl = slice(D_MIN, bnd.shape[1] - D_MIN + 1)
            te_shift = shift_profile(te, bnd)[sl].mean()
            syn_shift = shift_profile(syn, bnd)[sl].mean()
            ov = shift_profile(bnd.astype(float), bnd)
            audit.append(ov[sl].mean() - bnd.mean())      # residual overlap excess

            others = [j for j in range(len(P)) if j != i]
            te_x = float(np.mean([te[P[j][2]].mean() for j in others]))
            syn_x = float(np.mean([syn[P[j][2]].mean() for j in others]))

            per_seed.append([te[bnd].mean(), syn[bnd].mean(),
                             te_shift, syn_shift, te_x, syn_x,
                             rand_null(te, bnd, rng), rand_null(syn, bnd, rng),
                             bnd.mean()])
        v = np.array(per_seed)
        names.append(name); out.append(v)
        print(f"{name:>6}  transport: shift {v[:,0].mean()-v[:,2].mean():+.4f}  "
              f"xseed {v[:,0].mean()-v[:,4].mean():+.4f}  rand {v[:,0].mean()-v[:,6].mean():+.4f}   "
              f"combining: shift {v[:,1].mean()-v[:,3].mean():+.4f}  "
              f"xseed {v[:,1].mean()-v[:,5].mean():+.4f}  rand {v[:,1].mean()-v[:,7].mean():+.4f}")
        sys.stdout.flush()

    np.savez("data/geom_null.npz", names=np.array(names), data=np.array(out),
             cols=np.array(["te_on", "syn_on", "te_shift", "syn_shift", "te_xseed",
                            "syn_xseed", "te_rand", "syn_rand", "bnd"]))
    print(f"\nresidual overlap excess over chance, max across all fields: {max(audit):.4f} "
          f"(D_MIN={D_MIN}; 0 would mean shifted masks are exactly at chance overlap)")
    print("wrote data/geom_null.npz")


if __name__ == "__main__":
    main()
