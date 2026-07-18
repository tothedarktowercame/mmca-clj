"""Render seeds 1--36 as a 6x6 genotype/phenotype river contact sheet."""

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt


def load(seed):
    raw = open(
        f"data/river_grid_s{seed}.txt", encoding="utf-8"
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


figure = plt.figure(figsize=(15, 18))
outer = figure.add_gridspec(
    6, 6, hspace=0.10, wspace=0.055, left=0.015, right=0.995,
    bottom=0.015, top=0.975
)

for index, seed in enumerate(range(1, 37)):
    genotype, phenotype = load(seed)
    genotype_image = np.array(
        [[rgb(cell) for cell in row] for row in genotype], dtype=np.uint8
    )
    phenotype_image = np.array(
        [[0 if cell == "1" else 255 for cell in row] for row in phenotype],
        dtype=np.uint8,
    )

    cell = outer[index // 6, index % 6].subgridspec(
        2, 1, height_ratios=[1, 1], hspace=0.015
    )
    genotype_axis = figure.add_subplot(cell[0, 0])
    phenotype_axis = figure.add_subplot(cell[1, 0])

    genotype_axis.imshow(genotype_image, aspect="auto", interpolation="nearest")
    phenotype_axis.imshow(
        phenotype_image, aspect="auto", cmap="gray", interpolation="nearest"
    )
    for axis in (genotype_axis, phenotype_axis):
        axis.set_xticks([])
        axis.set_yticks([])

    genotype_axis.text(
        0.02,
        0.96,
        f"{seed}",
        transform=genotype_axis.transAxes,
        fontsize=8,
        color="#c0392b",
        family="monospace",
        va="top",
        fontweight="bold",
        bbox={"facecolor": "white", "alpha": 0.72, "edgecolor": "none", "pad": 1},
    )

figure.suptitle(
    "River composition: seeds 1–36 (genotype above phenotype)",
    fontsize=14,
    y=0.995,
)
figure.savefig(
    "figures/river_grid_36.png",
    dpi=140,
    bbox_inches="tight",
    facecolor="white",
)
print("wrote figures/river_grid_36.png")
