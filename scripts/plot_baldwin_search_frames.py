#!/usr/bin/env python3
"""Compare matched spacetime samples from the preregistered Baldwin search.

The input directory contains `frames_<arm>.tsv` derived from each arm's record
and the original per-generation `<arm>.tsv`.  Labels report both the one-fork
reach visible in the damage panel and the final population mean; the former is
not presented as an estimate of arm quality.
"""

import collections
import csv
import os
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np


ARMS = [
    ("neutral", "neutral"),
    ("independent-variable", "independent / variable p0"),
    ("coupled-variable", "coupled / variable p0"),
    ("independent-fixed", "independent / fixed p0"),
    ("coupled-fixed", "coupled / fixed p0"),
]
SURFACE = "#fcfcfb"
INK = "#1a1a1a"


def load_frames(path):
    grids = collections.defaultdict(dict)
    with open(path, encoding="utf-8") as stream:
        next(stream)
        for line in stream:
            if not line.strip():
                continue
            kind, time, cell, value = line.rstrip().split("\t")
            grids[kind].setdefault(int(time), {})[int(cell)] = int(value)
    return grids


def array(grids, kind):
    rows = grids[kind]
    times = sorted(rows)
    width = max(max(row) for row in rows.values()) + 1
    return np.array([[rows[t].get(i, 0) for i in range(width)] for t in times]), times


def terminal_stats(path):
    with open(path, encoding="utf-8") as stream:
        rows = list(csv.DictReader(stream, delimiter="\t"))
    return rows[-1]


def main():
    if len(sys.argv) != 3:
        raise SystemExit("usage: plot_baldwin_search_frames.py DERIVED_DIR OUTPUT.png")
    source, output = sys.argv[1:]
    fig, axes = plt.subplots(len(ARMS), 3, figsize=(12.5, 15.5), facecolor=SURFACE)

    for row, (arm, label) in enumerate(ARMS):
        grids = load_frames(os.path.join(source, f"frames_{arm}.tsv"))
        stats = terminal_stats(os.path.join(source, "..", f"{arm}.tsv"))
        shown_held = grids["meta"][0][0]
        sample_reach = grids["meta"][0][1]
        caption = (
            f"{label}\n"
            f"sample reach {sample_reach}  |  population reach {float(stats['mean-reach']):.2f}\n"
            f"shown genome held {shown_held}/80  |  population held {float(stats['mean-held']):.3f}"
        )
        for col, (kind, cmap) in enumerate(
            [("geno", "viridis"), ("pheno", "gray"), ("damage", "inferno")]
        ):
            values, times = array(grids, kind)
            ax = axes[row, col]
            ax.imshow(
                values,
                aspect="auto",
                interpolation="nearest",
                cmap=cmap,
                extent=[0, values.shape[1], times[-1], times[0]],
            )
            ax.axhline(60, color="cyan", lw=0.7, ls="--")
            ax.set_xticks([])
            ax.set_yticks([])
            if row == 0:
                ax.set_title(
                    {"geno": "genotype", "pheno": "phenotype", "damage": "damage A vs B"}[kind],
                    fontsize=10,
                    family="monospace",
                    color=INK,
                )
            if col == 0:
                ax.set_ylabel(
                    caption,
                    fontsize=8.5,
                    family="monospace",
                    rotation=0,
                    ha="right",
                    va="center",
                    labelpad=38,
                    color=INK,
                )

    fig.suptitle(
        "Best-function genome from each arm — matched seed 1, site 40\n"
        "cyan line: damage fork at t*=60; single-sample reach is descriptive only",
        fontsize=11,
        family="monospace",
        color=INK,
    )
    fig.tight_layout(rect=(0, 0, 1, 0.965))
    fig.savefig(output, dpi=190, bbox_inches="tight", facecolor=SURFACE)
    print(f"wrote {output}")


if __name__ == "__main__":
    main()
