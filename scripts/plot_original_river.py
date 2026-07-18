"""Render original `quad-4cand / firstMatch prop:rot2` with square cells."""

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt

SEEDS = range(1, 7)


def load(seed):
    raw = open(
        f"data/original_river_s{seed}.txt", encoding="utf-8"
    ).read().splitlines()
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


figure = plt.figure(figsize=(12, 6.5))
grid = figure.add_gridspec(
    2, 6, height_ratios=[1, 1], hspace=0.02, wspace=0.05,
    left=0.01, right=0.995, top=0.94, bottom=0.02
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
    phenotype_axis = figure.add_subplot(grid[1, column])
    genotype_axis.imshow(genotype_image, aspect="equal", interpolation="nearest")
    phenotype_axis.imshow(
        phenotype_image, aspect="equal", cmap="gray", interpolation="nearest"
    )
    for axis in (genotype_axis, phenotype_axis):
        axis.set_xticks([])
        axis.set_yticks([])
    genotype_axis.set_title(f"seed {seed}", fontsize=9, color="#a22")

figure.suptitle(
    "Original paper: quad-4cand / firstMatch prop:rot2", fontsize=13
)
figure.savefig(
    "figures/original_paper_river.png",
    dpi=140,
    bbox_inches="tight",
    facecolor="white",
)
print("wrote figures/original_paper_river.png")
