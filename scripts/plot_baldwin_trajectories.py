#!/usr/bin/env python3
"""Selection-run trajectories against the empirical null.

Small multiples, one panel per quantity -- never a dual axis, since reach and the
held fraction share no scale.  The null arm is a dashed gray reference rather than
a peer series: it is the baseline every claim must beat, not another treatment.

Palette validated with the dataviz validator (light mode, categorical, 5 slots):
all checks pass, no warnings.
"""
import sys, glob, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

SURFACE = "#fcfcfb"
INK, MUTED = "#1a1a1a", "#6b6b6b"
HUES = ["#c2334d", "#3b6fd4", "#1b7f5f", "#7b5ea7", "#c77a12"]

srcdir = sys.argv[1] if len(sys.argv) > 1 else "/tmp/box3"
out = sys.argv[2] if len(sys.argv) > 2 else "figures/baldwin_trajectories.png"

def load(path):
    lines = open(path).read().splitlines()
    hdr = lines[0].split("\t")
    rows = [l.split("\t") for l in lines[1:] if l.strip()]
    return hdr, [[float(x) for x in r] for r in rows]

arms = {}
for p in sorted(glob.glob(os.path.join(srcdir, "data_*.tsv"))):
    tag = os.path.basename(p)[5:-4]
    hdr, rows = load(p)
    if rows:
        arms[tag] = (hdr, rows)

# col index by header name, tolerant of the older short header
def col(hdr, name, fallback):
    return hdr.index(name) if name in hdr else fallback

PANELS = [("mean-gamma", 1, "gain (gamma)"),
          ("mean-reach", 3, "reach  — function"),
          ("mean-plastic", 8, "plastic dependence"),
          ("mean-held", 9, "held fraction  — assimilation")]

fig, axes = plt.subplots(1, 4, figsize=(15, 3.6), facecolor=SURFACE)
treat = [t for t in arms if t != "null"]
for ax, (name, fb, title) in zip(axes, PANELS):
    ax.set_facecolor(SURFACE)
    if "null" in arms:
        hdr, rows = arms["null"]
        c = col(hdr, name, fb)
        if c < len(rows[0]):
            ax.plot([r[0] for r in rows], [r[c] for r in rows],
                    color=MUTED, lw=2, ls="--", label="null (no selection)", zorder=2)
    for i, tag in enumerate(treat):
        hdr, rows = arms[tag]
        c = col(hdr, name, fb)
        if c < len(rows[0]):
            ax.plot([r[0] for r in rows], [r[c] for r in rows],
                    color=HUES[i % len(HUES)], lw=2, label=tag, zorder=3)
    ax.set_title(title, fontsize=9.5, color=INK, family="monospace", loc="left")
    ax.set_xlabel("generation", fontsize=8, color=MUTED)
    ax.tick_params(labelsize=7.5, colors=MUTED)
    for s in ("top", "right"):
        ax.spines[s].set_visible(False)
    for s in ("left", "bottom"):
        ax.spines[s].set_color("#d8d8d4")
    ax.grid(True, lw=0.6, color="#ececea", zorder=1)
    ax.set_axisbelow(True)

axes[0].legend(fontsize=7.5, frameon=False, labelcolor=INK, loc="lower right")
fig.tight_layout()
fig.savefig(out, dpi=200, bbox_inches="tight", facecolor=SURFACE)
print(f"  wrote {out}  ({len(arms)} arms: {', '.join(sorted(arms))})")
