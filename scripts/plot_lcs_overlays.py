#!/usr/bin/env python3
"""Render Part C held-out LCS masks on the exact tint and river plates."""

from pathlib import Path
import csv

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy.ndimage import uniform_filter

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
GREEN = np.array([0.0, 1.0, 0.0, 1.0])

TINT_FIELDS = [
    ("offset1", "offset $+1$ (8-cycle)"),
    ("two4cyc", r"$[6\,7\,0\,2\,1\,4\,3\,5]$ (two 4-cycles)"),
    ("sigma16250374", r"$\sigma=16250374$"),
]

COLOR_OVERRIDES = {
    29: "#00ff33", 30: "#0033ff", 71: "#00ff33", 90: "#ffcc00",
    110: "#ff3300", 118: "#ff3300", 120: "#0033ff", 135: "#0033ff",
    137: "#ff3300", 145: "#ff3300", 165: "#ffcc00", 184: "#00ff33",
    225: "#0033ff", 226: "#00ff33",
}


def load_scores():
    rows = np.loadtxt(DATA / "rule_activity_scores.txt")
    scores = np.zeros(256)
    scores[rows[:, 0].astype(int)] = rows[:, 1]
    return scores


def rgba_with_mask(values, mask, cmap, vmin=None, vmax=None):
    normalizer = matplotlib.colors.Normalize(vmin=vmin, vmax=vmax)
    rgba = cmap(normalizer(values))
    rgba[mask] = GREEN
    return rgba


def mask_relation_row(name, field, mask, scores, burn_in, steps, depth):
    activity = scores[field]
    active = uniform_filter(activity, size=5) > 0.35
    grad_t, grad_x = np.gradient(active.astype(float))
    boundary = np.hypot(grad_t, grad_x) > 0
    support = np.zeros_like(mask, dtype=bool)
    support[burn_in:steps, depth:-depth] = True
    mask = mask & support
    classes = {
        "boundary": boundary & support,
        "active-interior": active & ~boundary & support,
        "inactive-interior": ~active & ~boundary & support,
    }
    total_mask = int(mask.sum())
    total_support = int(support.sum())
    row = {
        "metric_kind": "tint-relation",
        "coherent_points": total_mask,
        "support_cells": total_support,
    }
    for label, cells in classes.items():
        mask_fraction = np.count_nonzero(mask & cells) / total_mask if total_mask else 0.0
        support_fraction = np.count_nonzero(cells) / total_support
        row[f"{label}_fraction"] = mask_fraction
        row[f"{label}_baseline"] = support_fraction
        row[f"{label}_enrichment"] = (
            mask_fraction / support_fraction if support_fraction else 0.0
        )
    row["dataset"] = name
    return row


def plot_tint(scores, configs):
    rows = []
    figure, axes = plt.subplots(1, 3, figsize=(11.4, 6.6), constrained_layout=True)
    for axis, (name, label) in zip(axes, TINT_FIELDS):
        prefix = DATA / f"lcs_overlay_tint_{name}"
        field = np.loadtxt(f"{prefix}_gen.txt", dtype=int)
        mask = np.loadtxt(f"{prefix}_mask.txt", dtype=np.int8).astype(bool)
        config = configs[name]
        row = mask_relation_row(
            name, field, mask, scores,
            int(config["training-burn-in"]), int(config["target-steps"]),
            int(config["selected-depth"]),
        )
        rows.append(row)
        image = rgba_with_mask(scores[field], mask, plt.cm.coolwarm, 0.0, 1.0)
        axis.imshow(image, aspect="auto", interpolation="nearest")
        axis.set_title(
            f"{label}\nLCS: {row['coherent_points']:,} cells", fontsize=10
        )
        axis.set_xlabel("cell")
        axis.set_ylabel("time")
    figure.suptitle(
        "Held-out genotype LCS structures (bright green) on activity tint",
        fontsize=12,
    )
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "lcs_overlay_tint.png"
    figure.savefig(output, dpi=600, bbox_inches="tight", facecolor="white")
    figure.savefig(output.with_suffix(".pdf"), bbox_inches="tight", facecolor="white")
    plt.close(figure)
    return rows


def rule_rgb_table():
    table = np.zeros((256, 3), dtype=np.uint8)
    for rule in range(256):
        colour = COLOR_OVERRIDES.get(rule, f"#{rule:02x}{rule:02x}{rule:02x}")
        table[rule] = [int(colour[index:index + 2], 16) for index in (1, 3, 5)]
    return table


def plot_river(config):
    genotype = np.loadtxt(DATA / "lcs_overlay_river_gen.txt", dtype=int)
    phenotype = np.loadtxt(DATA / "lcs_overlay_river_phe.txt", dtype=np.int8)
    mask = np.loadtxt(DATA / "lcs_overlay_river_mask.txt", dtype=np.int8).astype(bool)
    genotype_image = rule_rgb_table()[genotype] / 255.0
    genotype_image = np.dstack((genotype_image, np.ones(mask.shape)))
    phenotype_image = plt.cm.gray(1 - phenotype)
    genotype_image[mask] = GREEN
    phenotype_image[mask] = GREEN

    figure, axes = plt.subplots(
        1, 2, figsize=(10.5, 7.8),
        gridspec_kw={"wspace": 0.035, "left": 0.01, "right": 0.99,
                     "top": 0.94, "bottom": 0.01},
    )
    axes[0].imshow(genotype_image, aspect="equal", interpolation="nearest")
    axes[1].imshow(phenotype_image, aspect="equal", interpolation="nearest")
    axes[0].set_title("genotype + joint LCS", fontsize=13)
    axes[1].set_title("phenotype + joint LCS", fontsize=13)
    for axis in axes:
        axis.set_xticks([])
        axis.set_yticks([])
    figure.suptitle(
        r"River seed 1, $L=240$, $T=360$; held-out joint LCS in bright green",
        fontsize=14, y=0.995,
    )
    output = FIGURES / "lcs_overlay_river.png"
    figure.savefig(output, dpi=600, bbox_inches="tight", facecolor="white")
    figure.savefig(output.with_suffix(".pdf"), bbox_inches="tight", facecolor="white")
    plt.close(figure)
    support = np.zeros_like(mask, dtype=bool)
    burn_in = int(config["training-burn-in"])
    depth = int(config["selected-depth"])
    support[burn_in:int(config["target-steps"]), depth:-depth] = True
    return {
        "metric_kind": "river-density",
        "coherent_points": int(mask.sum()),
        "support_cells": int(support.sum()),
        "density": float(mask.sum() / support.sum()),
        "dataset": "river",
    }


def write_summary(rows):
    fields = sorted({key for row in rows for key in row} - {"dataset"})
    fields.append("dataset")
    with open(DATA / "lcs_overlay_summary.tsv", "w", newline="") as handle:
        writer = csv.DictWriter(
            handle, fieldnames=fields, delimiter="\t", lineterminator="\n"
        )
        writer.writeheader()
        writer.writerows(rows)


def load_configs():
    with open(DATA / "lcs_overlay_config.tsv", newline="") as handle:
        return {row["dataset"]: row for row in csv.DictReader(handle, delimiter="\t")}


def main():
    scores = load_scores()
    configs = load_configs()
    rows = plot_tint(scores, configs)
    rows.append(plot_river(configs["river"]))
    write_summary(rows)
    for row in rows:
        if row["metric_kind"] == "tint-relation":
            print(
                f"{row['dataset']}: boundary={row['boundary_fraction']:.3f} "
                f"active-interior={row['active-interior_fraction']:.3f} "
                f"inactive-interior={row['inactive-interior_fraction']:.3f}"
            )
        else:
            print(f"river: coherent density={row['density']:.4f}")
    print("wrote figures/lcs_overlay_{tint,river}.{png,pdf}")
    print("wrote data/lcs_overlay_summary.tsv")


if __name__ == "__main__":
    main()
