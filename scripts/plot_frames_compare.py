#!/usr/bin/env python3
"""Spacetime diagrams for each arm's best evolved genome, one row per arm.

Same three quantities as the single-run view -- genotype, phenotype, damage -- but
now for what selection actually built, so the arms can be compared directly. The
damage column is the one to read: reach is the count of its final row.
"""
import sys, collections, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

SURFACE = "#fcfcfb"; INK = "#1a1a1a"; MUTED = "#6b6b6b"
arms = sys.argv[1:-1]
out = sys.argv[-1]

def grids(path):
    g = collections.defaultdict(dict)
    for line in open(path).read().splitlines()[1:]:
        if not line.strip():
            continue
        k, t, i, v = line.split("\t")
        g[k].setdefault(int(t), {})[int(i)] = int(v)
    return g

def arr(g, kind):
    d = g[kind]; ts = sorted(d)
    w = max(max(r) for r in d.values()) + 1
    return np.array([[d[t].get(i, 0) for i in range(w)] for t in ts]), ts

fig, axes = plt.subplots(len(arms), 3, figsize=(11.5, 3.5 * len(arms)), facecolor=SURFACE)
if len(arms) == 1:
    axes = [axes]
for row, path in enumerate(arms):
    tag = os.path.basename(path)[7:-4]
    g = grids(path)
    for col, (kind, cmap) in enumerate([("geno", None), ("pheno", "gray"), ("damage", "inferno")]):
        ax = axes[row][col]
        A, ts = arr(g, kind)
        ax.imshow(A, aspect="auto", interpolation="nearest", cmap=cmap,
                  extent=[0, A.shape[1], ts[-1], ts[0]])
        ax.set_xticks([]); ax.set_yticks([])
        if row == 0:
            ax.set_title({"geno": "genotype", "pheno": "phenotype",
                          "damage": "damage (reach = final row)"}[kind],
                         fontsize=9.5, color=INK, family="monospace")
        if col == 0:
            reach = sum(g["damage"][max(g["damage"])].values())
            ax.set_ylabel(f"{tag}\nreach {reach}", fontsize=9, color=INK,
                          family="monospace", rotation=0, ha="right", va="center", labelpad=34)
fig.tight_layout()
fig.savefig(out, dpi=170, bbox_inches="tight", facecolor=SURFACE)
print(f"  wrote {out}  ({len(arms)} arms)")
