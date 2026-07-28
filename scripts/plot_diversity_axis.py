"""Figure: causal amplification against sustained genotype diversity.

The original seven-mechanism survey is retained as context, but its apparent
descending limb confounded diversity with mutation or genotype holding.  Dial 4
adds balanced genotype measures that are transported only by local bijective
swaps.  Its two-site exchange perturbation is normalized by its initial damage,
placing both protocols on a causal-amplification scale.
"""

import csv

import matplotlib
import numpy as np

matplotlib.use("Agg")
import matplotlib.pyplot as plt


old_rows = [
    line.rstrip("\n").split("\t")
    for line in open("data/diversity_axis.tsv", encoding="utf-8")
][1:]
old_diversity = np.array([float(row[0]) for row in old_rows])
old_reach = np.array([float(row[1]) for row in old_rows])
old_mechanism = np.array([row[2] for row in old_rows])

with open("data/diversity_dial4_summary.tsv", newline="", encoding="utf-8") as source:
    dial4 = list(csv.DictReader(source, delimiter="\t"))

old_groups = [
    (
        "structured original mechanisms",
        ~np.isin(old_mechanism, ["mutation", "async refuge", "preserving limit"]),
        "#777777",
        "o",
    ),
    ("replacement mutation", old_mechanism == "mutation", "#c44e52", "v"),
    (
        "holding / preserving",
        np.isin(old_mechanism, ["async refuge", "preserving limit"]),
        "#8172b3",
        "D",
    ),
]

figure, axis = plt.subplots(figsize=(9.2, 5.2))
for label, selection, colour, marker in old_groups:
    axis.scatter(
        old_diversity[selection],
        old_reach[selection],
        c=colour,
        marker=marker,
        s=36,
        alpha=0.72,
        edgecolor="white",
        linewidth=0.5,
        label=label,
        zorder=2,
    )

rates = [0.0, 0.25, 0.5, 0.75, 1.0]
colours = plt.cm.viridis([0.05, 0.27, 0.50, 0.73, 0.95])
for rate, colour in zip(rates, colours):
    rows = [
        row for row in dial4 if float(row["transport-rate"]) == rate
    ]
    rows.sort(key=lambda row: int(row["support"]))
    x = [int(row["support"]) for row in rows]
    # Dial 4 begins from a transposition: two sites differ at dt=0.  Division
    # by two gives final damaged sites per initially damaged site; the original
    # survey begins from one damaged site, so its plotted values are unchanged.
    y = [float(row["mean-dG-final"]) / 2.0 for row in rows]
    error = [float(row["sem-dG-final"]) / 2.0 for row in rows]
    axis.errorbar(
        x,
        y,
        yerr=error,
        color=colour,
        marker="o",
        markersize=4,
        linewidth=1.6,
        capsize=2,
        label=f"conservative transport {rate:.2f}",
        zorder=4,
    )

axis.text(
    16,
    5.6,
    "original survey",
    color="#555555",
    fontsize=8,
    ha="left",
)
axis.text(
    150,
    18.0,
    "measure-preserving genotype flow",
    color="#333333",
    fontsize=8,
    ha="center",
)
axis.set_xlim(0, 264)
axis.set_ylim(-0.4, 23.5)
axis.set_xlabel("sustained genotype diversity (distinct rules)")
axis.set_ylabel("causal amplification\n(final / initial genotype damage)")
axis.set_title(
    "Causal amplification depends on conservative transport, not diversity alone",
    fontsize=11,
)
axis.grid(color="0.88", linewidth=0.6, zorder=0)
axis.legend(
    loc="upper center",
    bbox_to_anchor=(0.5, -0.17),
    frameon=False,
    fontsize=7.6,
    ncol=4,
)
figure.tight_layout(rect=(0, 0.12, 1, 1))

for extension in ("png", "pdf"):
    figure.savefig(
        f"figures/diversity_axis.{extension}",
        dpi=600,
        bbox_inches="tight",
        facecolor="white",
    )
print("wrote figures/diversity_axis.{png,pdf}")
