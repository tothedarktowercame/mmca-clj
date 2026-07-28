"""Plot the 3x3 diversity-dial genotype/phenotype sampler."""

from pathlib import Path

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
COLOUR_OVERRIDES = {
    29: "#00ff33",
    30: "#0033ff",
    71: "#00ff33",
    90: "#ffcc00",
    110: "#ff3300",
    118: "#ff3300",
    120: "#0033ff",
    135: "#0033ff",
    137: "#ff3300",
    145: "#ff3300",
    165: "#ffcc00",
    184: "#00ff33",
    225: "#0033ff",
    226: "#00ff33",
}


def rgb(rule):
    colour = COLOUR_OVERRIDES.get(rule, f"#{rule:02x}{rule:02x}{rule:02x}")
    value = colour.lstrip("#")
    return [int(value[index : index + 2], 16) for index in (0, 2, 4)]


def load_run(tag):
    raw = (DATA / f"diversity_sampler_{tag}.txt").read_text(
        encoding="utf-8"
    ).splitlines()
    genotype_start = raw.index("GEN") + 1
    phenotype_start = raw.index("PHE")
    genotype = np.array(
        [
            [rgb(int(rule)) for rule in row.split()]
            for row in raw[genotype_start:phenotype_start]
            if row.strip()
        ],
        dtype=np.uint8,
    )
    phenotype = np.array(
        [
            [0 if cell == "1" else 255 for cell in row]
            for row in raw[phenotype_start + 1 :]
            if row.strip()
        ],
        dtype=np.uint8,
    )
    return genotype, phenotype


manifest = []
for line in (DATA / "diversity_sampler_manifest.tsv").read_text(
    encoding="utf-8"
).splitlines()[1:]:
    group, tag, name, rules, effective = line.split("\t")
    manifest.append(
        {
            "group": group,
            "tag": tag,
            "name": name,
            "rules": int(rules),
            "effective": float(effective),
        }
    )

figure = plt.figure(figsize=(15, 11.5))
grid = figure.add_gridspec(
    3,
    3,
    hspace=0.17,
    wspace=0.025,
    top=0.90,
    bottom=0.025,
    left=0.055,
    right=0.995,
)

for index, item in enumerate(manifest):
    group_row = index // 3
    column = index % 3
    genotype, phenotype = load_run(item["tag"])
    tile = grid[group_row, column].subgridspec(2, 1, hspace=0.035)
    genotype_axis = figure.add_subplot(tile[0, 0])
    phenotype_axis = figure.add_subplot(tile[1, 0])
    genotype_axis.imshow(genotype, aspect="auto", interpolation="nearest")
    phenotype_axis.imshow(
        phenotype,
        aspect="auto",
        cmap="gray",
        vmin=0,
        vmax=255,
        interpolation="nearest",
    )
    genotype_axis.set_title(
        f"{item['name']}\n"
        rf"$R_{{70}}={item['rules']}$, "
        rf"$N_{{\rm eff}}={item['effective']:.1f}$",
        fontsize=10,
        pad=4,
    )
    for axis in (genotype_axis, phenotype_axis):
        axis.set_xticks([])
        axis.set_yticks([])
    if column == 0:
        genotype_axis.set_ylabel(
            f"{item['group']}\ngenotype", fontsize=10, fontweight="bold"
        )
        phenotype_axis.set_ylabel("phenotype", fontsize=9)

figure.suptitle(
    "High-diversity spacetime behaviours across three diversity dials\n"
    r"full-population seed 0, $L=256$, $T=70$",
    fontsize=15,
    y=0.985,
)
FIGURES.mkdir(exist_ok=True)
figure.savefig(
    FIGURES / "diversity_sampler.png",
    dpi=600,
    bbox_inches="tight",
    facecolor="white",
)
figure.savefig(
    FIGURES / "diversity_sampler.pdf",
    dpi=600,
    bbox_inches="tight",
    facecolor="white",
)
print("wrote figures/diversity_sampler.png and figures/diversity_sampler.pdf")
