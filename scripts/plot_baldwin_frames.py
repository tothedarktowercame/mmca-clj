#!/usr/bin/env python3
"""Render MetaCA spacetime frames for eyeball inspection.

The formalism decides whether a trajectory certifies a claim; it cannot tell you
the dynamics look wrong.  Three panels, matching the paper's figure conventions:

  genotype  -- rule index per cell, the heritable layer
  phenotype -- the binary layer the genotype reads
  damage    -- where the perturbed and unperturbed branches differ

Damage is the one to check by eye.  Reach is the count of its final row, so if it
does not look like a light cone spreading from the flipped site, the number means
nothing however exactly it reproduces.
"""
import sys, collections
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

src = sys.argv[1] if len(sys.argv) > 1 else "/tmp/frames.tsv"
out = sys.argv[2] if len(sys.argv) > 2 else "figures/baldwin_frames.png"

grids = collections.defaultdict(dict)
for line in open(src).read().splitlines()[1:]:
    if not line.strip():
        continue
    k, t, i, v = line.split("\t")
    grids[k].setdefault(int(t), {})[int(i)] = int(v)

def to_array(kind):
    g = grids[kind]
    ts = sorted(g)
    w = max(max(r) for r in g.values()) + 1
    return np.array([[g[t].get(i, 0) for i in range(w)] for t in ts]), ts

fig = plt.figure(figsize=(12, 5.2))
panels = [("geno", "genotype (rule index)", None),
          ("pheno", "phenotype", "gray"),
          ("damage", "damage: A vs B (reach = final row count)", "inferno")]
for col, (kind, title, cmap) in enumerate(panels):
    if kind not in grids:
        continue
    A, ts = to_array(kind)
    ax = fig.add_subplot(1, 3, col + 1)
    ax.imshow(A, aspect="auto", interpolation="nearest", cmap=cmap,
              extent=[0, A.shape[1], ts[-1], ts[0]])
    ax.set_title(title, fontsize=9, family="monospace")
    ax.set_xlabel("cell", fontsize=8)
    if col == 0:
        ax.set_ylabel("step", fontsize=8)
    ax.tick_params(labelsize=7)
    # mark the fork so the light cone can be read against it
    if kind == "damage":
        ax.axhline(ts[0], color="cyan", lw=0.8, ls="--")
        ax.text(1, ts[0], " fork", color="cyan", fontsize=7, va="bottom")

fig.tight_layout()
fig.savefig(out, dpi=200, bbox_inches="tight", facecolor="white")
print(f"  wrote {out}")
d = grids.get("damage")
if d:
    last = max(d)
    print(f"  reach at final step = {sum(d[last].values())}")
