"""Render the river's pinned 240x360 representative spacetime plate."""

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt


SEED = 1


def load():
    raw = open(
        f"data/fig6_plate_s{SEED}.txt", encoding="utf-8"
    ).read().splitlines()
    genotype_start = raw.index("GEN") + 1
    phenotype_start = raw.index("PHE")
    genotype = [
        row.split() for row in raw[genotype_start:phenotype_start] if row.strip()
    ]
    phenotype = [row for row in raw[phenotype_start + 1 :] if row.strip()]
    return genotype, phenotype


def rgb(hex_colour):
    value = hex_colour.lstrip("#")
    return [int(value[index : index + 2], 16) for index in (0, 2, 4)]


genotype, phenotype = load()
genotype_image = np.array(
    [[rgb(colour) for colour in row] for row in genotype], dtype=np.uint8
)
phenotype_image = np.array(
    [[0 if cell == "1" else 255 for cell in row] for row in phenotype],
    dtype=np.uint8,
)

figure, axes = plt.subplots(
    1,
    2,
    figsize=(10.5, 7.8),
    gridspec_kw={
        "wspace": 0.035,
        "left": 0.01,
        "right": 0.99,
        "top": 0.94,
        "bottom": 0.01,
    },
)
axes[0].imshow(genotype_image, aspect="equal", interpolation="nearest")
axes[1].imshow(
    phenotype_image,
    aspect="equal",
    cmap="gray",
    vmin=0,
    vmax=255,
    interpolation="nearest",
)
axes[0].set_title("genotype", fontsize=13)
axes[1].set_title("phenotype", fontsize=13)
for axis in axes:
    axis.set_xticks([])
    axis.set_yticks([])

figure.suptitle(
    r"River construction: seed 1, $L=240$, $T=360$",
    fontsize=14,
    y=0.995,
)
figure.savefig(
    "figures/river.png", dpi=600, bbox_inches="tight", facecolor="white"
)
figure.savefig(
    "figures/river.pdf", dpi=600, bbox_inches="tight", facecolor="white"
)
print("wrote figures/river.png and figures/river.pdf (seed 1, 240x360)")
