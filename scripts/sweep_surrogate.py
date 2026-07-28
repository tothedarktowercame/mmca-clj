"""Surrogate-controlled transport across the codex-3 synthetic sweep.

The draft5 caveat says the co-located transport score is "confounded on the
chaotic flank, where noise manufactures transitions". Two candidate mechanisms:

  (M1) mask degeneracy -- the boundary mask stops being a thin wall as noise
       rises (filament fraction 0.195 at the edge -> 0.39 at p=1), diluting the
       on-filament average toward the field mean.
  (M2) estimator bias -- plug-in local TE is positively biased, so a noisier
       field can score positive TE with no directed coupling at all.

This adds the controls that separate them, per sweep point, over all 8 seeds:
  - source time-shuffle surrogate (kills M2): same marginals, coupling broken.
  - mask-matched random null (kills M1): a random mask of the SAME cell count.
Run from the mmca-clj repo root.
"""
import numpy as np, glob, sys, hashlib
from scipy.ndimage import uniform_filter

# Both nulls are stochastic, so the RNG is re-seeded deterministically from the
# field's own path: results must not depend on the order fields are visited in,
# or the reported numbers drift between runs.
def _rng_for(path):
    seed = int.from_bytes(hashlib.blake2b(path.encode(), digest_size=8).digest(), "big")
    return np.random.default_rng(seed % (2 ** 32))

N_NULL = 20        # redraws of the mask-matched null, averaged
_r = np.loadtxt("data/rule_activity_scores.txt")
scores = np.zeros(256); scores[_r[:, 0].astype(int)] = _r[:, 1]


def cond(dest, cs):
    key = np.zeros(dest.shape, int)
    for i, c in enumerate(cs):
        key += (c.astype(int) << i)
    p = np.zeros(dest.shape)
    for k in range(1 << len(cs)):
        m = key == k
        if m.any():
            p[m] = dest[m].mean()
    return p


def loc(dest, nc, dc):
    pn, pd = cond(dest, nc), cond(dest, dc)
    o = np.zeros(dest.shape)
    a = (dest == 1) & (pn > 0) & (pd > 0)
    o[a] = np.log2(pn[a] / pd[a])
    z = (dest == 0) & ((1 - pn) > 0) & ((1 - pd) > 0)
    o[z] = np.log2((1 - pn[z]) / (1 - pd[z]))
    return o


def measures(path):
    rng = _rng_for(path)
    field = np.loadtxt(path, dtype=int)
    smooth = uniform_filter(scores[field], size=5)
    act = (smooth > 0.35).astype(np.int8)
    gt, gx = np.gradient((smooth > 0.35).astype(float))
    bnd = (np.hypot(gt, gx) > 0)[1:]
    dest, self_ = act[1:], act[:-1]
    L = np.roll(act[:-1], +1, axis=1)
    R = np.roll(act[:-1], -1, axis=1)

    te_l, te_r = loc(dest, [self_, L], [self_]), loc(dest, [self_, R], [self_])
    te_j = loc(dest, [self_, L, R], [self_])
    te, syn = te_l + te_r, te_j - (te_l + te_r)

    # surrogate: time-shuffle the SOURCE column only; (self,dest) left intact
    sh = act.copy()
    for x in range(sh.shape[1]):
        sh[:, x] = rng.permutation(sh[:, x])
    sL = np.roll(sh[:-1], +1, axis=1)
    sR = np.roll(sh[:-1], -1, axis=1)
    s_l, s_r = loc(dest, [self_, sL], [self_]), loc(dest, [self_, sR], [self_])
    s_j = loc(dest, [self_, sL, sR], [self_])
    te_s, syn_s = s_l + s_r, s_j - (s_l + s_r)

    if bnd.sum() == 0:
        return [0.0] * 7
    # mask-matched random null: same number of cells, drawn anywhere, averaged
    # over N_NULL redraws so a single unlucky draw cannot move the reported mean
    k = int(bnd.sum())
    te_r, syn_r = [], []
    for _ in range(N_NULL):
        flat = rng.permutation(bnd.size)[:k]
        rnd = np.zeros(bnd.size, bool); rnd[flat] = True; rnd = rnd.reshape(bnd.shape)
        te_r.append(te[rnd].mean()); syn_r.append(syn[rnd].mean())

    return [te[bnd].mean(), te_s[bnd].mean(), float(np.mean(te_r)),
            syn[bnd].mean(), syn_s[bnd].mean(), float(np.mean(syn_r)),
            bnd.mean()]


def point(pat):
    fs = sorted(glob.glob(pat))
    v = np.array([measures(f) for f in fs])
    return v.mean(0), v.std(0, ddof=1) / np.sqrt(len(v)), len(fs)


rows = ([(f"q{q:.1f}", f"data/eoc_sweep_order_q{round(q*1000):03d}_s*.txt")
         for q in [i / 10 for i in range(11)]] +
        [(f"p{p:g}", f"data/eoc_sweep_chaos_p{round(p*1000):03d}_s*.txt")
         for p in [0.01, 0.02, 0.05, 0.1, 0.2, 0.4, 0.7, 1.0]])

if __name__ == "__main__":
    hdr = (f"{'pt':>6} {'TE_on':>17} {'TE_surr':>9} {'TE_rndmask':>10} {'TE-surr':>9} "
           f"{'syn_on':>17} {'syn_surr':>9} {'syn_rnd':>9} {'syn-surr':>9} {'bndfrac':>8} {'n':>3}")
    print(hdr); print("-" * len(hdr)); sys.stdout.flush()
    for name, pat in rows:
        m, e, n = point(pat)
        print(f"{name:>6} {m[0]:+.4f}±{e[0]:.4f} {m[1]:+.4f} {m[2]:+.4f}    {m[0]-m[1]:+.4f}  "
              f"{m[3]:+.4f}±{e[3]:.4f} {m[4]:+.4f} {m[5]:+.4f} {m[3]-m[4]:+.4f}  "
              f"{m[6]:.3f} {n:3d}")
        sys.stdout.flush()
