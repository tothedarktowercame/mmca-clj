"""Render Figure 6 from the standalone Clojure engine's six river runs."""

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt

SEEDS = [1, 2, 3, 4, 5, 6]


def load(seed):
    raw = open(f"data/fig6_s{seed}.txt", encoding="utf-8").read().splitlines()
    i = raw.index("GEN") + 1
    genotype = []
    while raw[i] != "PHE" and raw[i] != "":
        genotype.append(raw[i].split())
        i += 1
    phenotype = [row for row in raw[raw.index("PHE") + 1 :] if row.strip()]
    return genotype, phenotype


def rgb(hex_colour):
    value = hex_colour.lstrip("#")
    return [int(value[i : i + 2], 16) for i in (0, 2, 4)]


figure = plt.figure(figsize=(15, 7.4))
grid = figure.add_gridspec(
    2,
    6,
    height_ratios=[1, 1],
    hspace=0.03,
    wspace=0.04,
    top=0.96,
    bottom=0.01,
    left=0.005,
    right=0.995,
)

for column, seed in enumerate(SEEDS):
    genotype, phenotype = load(seed)
    genotype_image = np.array(
        [[rgb(cell) for cell in row] for row in genotype], dtype=np.uint8
    )
    phenotype_image = np.array(
        [[0 if cell == "1" else 255 for cell in row] for row in phenotype],
        dtype=np.uint8,
    )

    genotype_axis = figure.add_subplot(grid[0, column])
    genotype_axis.imshow(genotype_image, aspect="auto", interpolation="nearest")
    genotype_axis.set_xticks([])
    genotype_axis.set_yticks([])

    phenotype_axis = figure.add_subplot(grid[1, column])
    phenotype_axis.imshow(
        phenotype_image, aspect="auto", cmap="gray", interpolation="nearest"
    )
    phenotype_axis.set_xticks([])
    phenotype_axis.set_yticks([])

    genotype_axis.text(
        0.02,
        0.97,
        f"seed {seed}",
        transform=genotype_axis.transAxes,
        fontsize=11,
        color="#c0392b",
        family="monospace",
        va="top",
        fontweight="bold",
    )

figure.savefig(
    "figures/river.png",
    dpi=100,
    bbox_inches="tight",
    facecolor="white",
)
print("wrote figures/river.png")
