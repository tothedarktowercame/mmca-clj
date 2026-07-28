"""Control fields for the edge-of-chaos section, against both nulls.

offset+1  -- the operator whose filaments are in question
offset+4  -- genotypically DEAD: transports nothing, and is the demonstration
             that local transfer entropy is unusable in the sparse limit
sigma=16250374 -- Rule-110-dominated positive control

Uses the same deterministic per-field seeding as the sweep. Run from the repo
root; reads data/eoc_interface_top_*.txt.
"""
import numpy as np, sys, pathlib
from scipy.ndimage import uniform_filter

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from sweep_surrogate import loc, _rng_for, N_NULL, scores


def analyse(name):
    path = f"data/eoc_interface_top_{name}.txt"
    rng = _rng_for(path)
    field = np.loadtxt(path, dtype=int)
    smooth = uniform_filter(scores[field], size=5)
    act = (smooth > 0.35).astype(np.int8)
    gt, gx = np.gradient((smooth > 0.35).astype(float))
    bnd = (np.hypot(gt, gx) > 0)[1:]

    dest, self_ = act[1:], act[:-1]
    L, R = np.roll(act[:-1], +1, axis=1), np.roll(act[:-1], -1, axis=1)
    te = loc(dest, [self_, L], [self_]) + loc(dest, [self_, R], [self_])
    syn = loc(dest, [self_, L, R], [self_]) - te

    sh = act.copy()
    for x in range(sh.shape[1]):
        sh[:, x] = rng.permutation(sh[:, x])
    sL, sR = np.roll(sh[:-1], +1, axis=1), np.roll(sh[:-1], -1, axis=1)
    te_s = loc(dest, [self_, sL], [self_]) + loc(dest, [self_, sR], [self_])

    k = int(bnd.sum())
    nulls = []
    for _ in range(N_NULL):
        f = rng.permutation(bnd.size)[:k]
        m = np.zeros(bnd.size, bool); m[f] = True
        nulls.append((te[m.reshape(bnd.shape)].mean(), syn[m.reshape(bnd.shape)].mean()))
    nt, ns = np.mean([a for a, _ in nulls]), np.mean([b for _, b in nulls])

    on, off, s = te[bnd].mean(), te[~bnd].mean(), te_s[bnd].mean()
    print(f"{name:>14}  active {act.mean():.2f}  filament {bnd.mean():.3f}")
    print(f"{'':14}  on-filament {on:+.4f}  interior {off:+.4f}  ratio {on/off:6.1f}x")
    print(f"{'':14}  shuffled surrogate {s:+.4f}  mask-matched {nt:+.4f}")
    print(f"{'':14}  FILAMENT-SPECIFIC transport {on-nt:+.4f}  combining {syn[bnd].mean()-ns:+.4f}")


for nm in ("offset1", "offset4", "sigma16250374"):
    analyse(nm)
