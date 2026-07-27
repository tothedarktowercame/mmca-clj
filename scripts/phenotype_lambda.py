"""Langton's lambda computed on the realized phenotype dynamics.

The classification fixes lambda = 1/2 for every fixed rule of every operator,
by counting the eight truth-table entries with UNIFORM weight. But a running
field does not visit the eight neighbourhoods uniformly. Three quantities that
coincide for a homogeneous ECA on a uniform field come apart here:

  lambda_nominal  mean over cells of the nominal lambda of that cell's rule
  lambda_uniform  P(next = 1 | neighbourhood), averaged over the 8 neighbourhoods
                  with uniform weight -- lambda as Langton defines it, but read
                  off the realized dynamics rather than the rule table
  lambda_visited  the same probabilities weighted by how often each neighbourhood
                  is actually visited; this equals the phenotype activity

The gap between lambda_uniform and lambda_visited measures how far the
phenotype's neighbourhood distribution is from uniform -- i.e. how much of the
rule table the field actually exercises.
"""
from pathlib import Path
import numpy as np

DATA = Path(__file__).resolve().parents[1] / "data"

def load(tag):
    raw = (DATA / f"diversity_sampler_{tag}.txt").read_text().splitlines()
    g, p = raw.index("GEN") + 1, raw.index("PHE")
    gen = np.array([[int(r) for r in row.split()] for row in raw[g:p] if row.strip()])
    phe = np.array([[int(c) for c in row] for row in raw[p+1:] if row.strip()])
    return gen, phe

def lambdas(gen, phe):
    T, W = phe.shape
    # neighbourhood index in Wolfram order: p = 7 - (4L + 2C + R) maps 111->0 .. 000->7
    L, C, R = np.roll(phe, 1, axis=1), phe, np.roll(phe, -1, axis=1)
    nb = 7 - (4*L + 2*C + R)                      # position index 0..7
    nxt = phe[1:, :]                               # what the field actually produced
    nb = nb[:-1, :]
    counts = np.zeros(8); ones = np.zeros(8)
    for p in range(8):
        m = nb == p
        counts[p] = m.sum(); ones[p] = nxt[m].sum()
    visited = counts > 0
    prob = np.divide(ones, counts, out=np.full(8, np.nan), where=visited)
    lam_uniform = np.nanmean(prob)                        # uniform over visited neighbourhoods
    lam_visited = ones.sum() / counts.sum()               # visitation-weighted == activity
    freq = counts / counts.sum()
    ent = -(freq[freq > 0] * np.log2(freq[freq > 0])).sum() / 3.0   # normalised to [0,1]
    lam_nominal = np.mean([bin(r).count("1") for r in gen.ravel()]) / 8.0
    return lam_nominal, lam_uniform, lam_visited, ent, int(visited.sum())

REACH = {"dial1-noise040": 0.00, "dial1-braid-pa-t4": 3.23, "dial1-braid-r2-r4": 3.81,
         "dial2-blend070": 3.60, "dial2-async025": 1.22, "dial2-niches64": 2.72,
         "dial3-braid-blend070": 3.88, "dial3-braid-async075": 0.75,
         "dial3-phase-niches64": 1.56}

rows = []
print(f"{'configuration':>24} {'lam_nom':>8} {'lam_unif':>9} {'lam_vis':>8} {'nb-ent':>7} {'seen':>5} {'reach':>6}")
for tag, reach in REACH.items():
    gen, phe = load(tag)
    ln, lu, lv, e, seen = lambdas(gen, phe)
    rows.append((ln, lu, lv, e, reach))
    print(f"{tag:>24} {ln:>8.3f} {lu:>9.3f} {lv:>8.3f} {e:>7.3f} {seen:>5} {reach:>6.2f}")

a = np.array(rows)
def spear(x, y):
    rx, ry = (np.argsort(np.argsort(v)).astype(float) for v in (x, y))
    return np.corrcoef(rx, ry)[0, 1]
print()
for i, name in enumerate(("lambda_nominal", "lambda_uniform", "lambda_visited", "nbhd entropy")):
    print(f"  rho(reach, {name:<15}) = {spear(a[:, i], a[:, 4]):+.3f}")
print(f"\n  mean |lambda_uniform - lambda_visited| = {np.abs(a[:,1]-a[:,2]).mean():.3f}")
