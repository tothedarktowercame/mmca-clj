"""Summarize and plot the measure-preserving diversity dial."""

import csv
import math
from collections import defaultdict
from pathlib import Path
from statistics import mean, stdev

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
RATES = [0.0, 0.25, 0.5, 0.75, 1.0]


with (DATA / "diversity_dial4.tsv").open(newline="", encoding="utf-8") as source:
    rows = list(csv.DictReader(source, delimiter="\t"))

if len(rows) != 1920:
    raise RuntimeError(f"expected 1920 responses, found {len(rows)}")
if not all(
    row["rules-fork"] == row["rules-end"]
    and row["effective-fork"] == row["effective-end"]
    for row in rows
):
    raise RuntimeError("genotype-measure invariant failed")

# Sites share a trajectory and are not independent replicates. Average sites
# within each seed first, then use the six seed means for uncertainty.
by_seed = defaultdict(list)
for row in rows:
    key = (
        int(row["support"]),
        float(row["transport-rate"]),
        int(row["seed"]),
    )
    by_seed[key].append(row)

seed_means = {}
for key, seed_rows in by_seed.items():
    seed_means[key] = {
        field: mean(float(row[field]) for row in seed_rows)
        for field in ("effective-fork", "dG-final", "dP-final", "mean-swaps")
    }

summary = []
for support in sorted({key[0] for key in seed_means}):
    for rate in RATES:
        samples = [
            values
            for (sample_support, sample_rate, _), values in seed_means.items()
            if sample_support == support and sample_rate == rate
        ]
        record = {
            "support": support,
            "transport-rate": rate,
            "effective-rules": mean(
                sample["effective-fork"] for sample in samples
            ),
        }
        for field in ("dG-final", "dP-final", "mean-swaps"):
            values = [sample[field] for sample in samples]
            record[f"mean-{field}"] = mean(values)
            record[f"sem-{field}"] = stdev(values) / math.sqrt(len(values))
        summary.append(record)

summary_fields = [
    "support",
    "transport-rate",
    "effective-rules",
    "mean-dG-final",
    "sem-dG-final",
    "mean-dP-final",
    "sem-dP-final",
    "mean-mean-swaps",
    "sem-mean-swaps",
]
with (DATA / "diversity_dial4_summary.tsv").open(
    "w", newline="", encoding="utf-8"
) as target:
    writer = csv.DictWriter(target, delimiter="\t", fieldnames=summary_fields)
    writer.writeheader()
    writer.writerows(summary)

figure, axes = plt.subplots(1, 2, figsize=(9.2, 3.8), sharex=True)
colours = plt.cm.viridis([0.05, 0.27, 0.50, 0.73, 0.95])
for rate, colour in zip(RATES, colours):
    rate_rows = [row for row in summary if row["transport-rate"] == rate]
    x = [row["effective-rules"] for row in rate_rows]
    for axis, field, label in (
        (axes[0], "dG-final", "Final genotype damage"),
        (axes[1], "dP-final", "Final phenotype damage"),
    ):
        y = [row[f"mean-{field}"] for row in rate_rows]
        error = [row[f"sem-{field}"] for row in rate_rows]
        axis.errorbar(
            x,
            y,
            yerr=error,
            marker="o",
            markersize=3.5,
            linewidth=1.25,
            capsize=2,
            color=colour,
            label=f"transport {rate:.2f}",
        )
        axis.set_ylabel(label)
        axis.set_xlabel(r"Effective genotype diversity $N_{\rm eff}$")
        axis.grid(color="0.9", linewidth=0.6)

handles, labels = axes[1].get_legend_handles_labels()
figure.legend(
    handles,
    labels,
    loc="lower center",
    bbox_to_anchor=(0.5, -0.01),
    frameon=False,
    fontsize=8,
    ncol=5,
)
figure.suptitle(
    "Measure-preserving diversity dial: transport, not diversity, controls spread",
    fontsize=11,
)
figure.tight_layout(rect=(0, 0.1, 1, 1))
FIGURES.mkdir(exist_ok=True)
figure.savefig(
    FIGURES / "diversity_dial4.pdf",
    dpi=600,
    bbox_inches="tight",
    facecolor="white",
)
figure.savefig(
    FIGURES / "diversity_dial4.png",
    dpi=300,
    bbox_inches="tight",
    facecolor="white",
)
print("wrote data/diversity_dial4_summary.tsv and figures/diversity_dial4.{pdf,png}")
