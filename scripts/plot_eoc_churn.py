#!/usr/bin/env python3
"""Measure and plot genotype churn in activity-domain walls and interiors."""

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy.ndimage import uniform_filter

from plot_eoc_interface import activity_boundary, load_scores


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
WIDTHS = [128, 256, 512, 768]
OPERATORS = [
    ("offset1", "offset +1", [1, 2, 3]),
    ("sigma16250374", "σ = 16250374", [1, 2, 3]),
    ("river", "river", [1, 2]),
]
REGIONS = ["wall", "ordered-interior", "chaotic-interior"]


def region_churn(field, scores):
    """Return changed-rule fractions for the wall and its two domain interiors.

    The wall is delegated to ``plot_eoc_interface.activity_boundary`` so this
    measurement uses exactly the extraction plotted in Figure ``fig:interface``.
    Region masks are aligned with churn rows 1..T before counting changes.
    """
    high_activity = uniform_filter(scores[field], size=5) > 0.35
    wall = activity_boundary(field, scores)
    masks = {
        "wall": wall[1:],
        "ordered-interior": (~high_activity & ~wall)[1:],
        "chaotic-interior": (high_activity & ~wall)[1:],
    }
    changed = field[1:] != field[:-1]
    return {
        region: np.count_nonzero(changed & mask) / np.count_nonzero(mask)
        for region, mask in masks.items()
    }


def load_measurements(scores):
    measurements = {}
    for name, _, seeds in OPERATORS:
        for width in WIDTHS:
            for seed in seeds:
                field = np.loadtxt(
                    DATA / f"eoc_interface_{name}_L{width}_s{seed}.txt",
                    dtype=int,
                )
                measurements[name, width, seed] = region_churn(field, scores)
    return measurements


def print_measurements(measurements):
    wall_above_chaotic_everywhere = True
    wall_above_both_count = 0
    chaotic_stablest_count = 0
    field_count = 0
    chaotic_stability_failures = []
    minimum_advantage = None
    print("PER_SEED_CHURN operator width seed wall ordered-interior "
          "chaotic-interior wall_gt_both")
    for name, _, seeds in OPERATORS:
        for width in WIDTHS:
            for seed in seeds:
                values = measurements[name, width, seed]
                passes = values["wall"] > max(
                    values["ordered-interior"],
                    values["chaotic-interior"],
                )
                wall_above_chaotic = (
                    values["wall"] > values["chaotic-interior"]
                )
                advantage = values["wall"] - max(
                    values["ordered-interior"],
                    values["chaotic-interior"],
                )
                candidate = (advantage, name, width, seed)
                if minimum_advantage is None or candidate < minimum_advantage:
                    minimum_advantage = candidate
                chaotic_stablest = (
                    values["chaotic-interior"] < values["ordered-interior"]
                )
                field_count += 1
                wall_above_both_count += int(passes)
                chaotic_stablest_count += int(chaotic_stablest)
                wall_above_chaotic_everywhere &= wall_above_chaotic
                if not chaotic_stablest:
                    chaotic_stability_failures.append((name, width, seed))
                print(
                    f"PER_SEED_CHURN {name} {width} {seed} "
                    f"{values['wall']:.6f} "
                    f"{values['ordered-interior']:.6f} "
                    f"{values['chaotic-interior']:.6f} "
                    f"{'PASS' if passes else 'FAIL'}"
                )

    print("AGGREGATE_CHURN operator width region mean sample_sd n")
    for name, _, seeds in OPERATORS:
        for width in WIDTHS:
            for region in REGIONS:
                values = np.asarray([
                    measurements[name, width, seed][region] for seed in seeds
                ])
                sample_sd = values.std(ddof=1) if len(values) > 1 else 0.0
                print(
                    f"AGGREGATE_CHURN {name} {width} {region} "
                    f"{values.mean():.6f} {sample_sd:.6f} {len(values)}"
                )
    print(
        "WALL_GT_BOTH_FIELDS",
        f"{wall_above_both_count}/{field_count}",
    )
    print(
        "WALL_GT_CHAOTIC_ALL_FIELDS",
        "PASS" if wall_above_chaotic_everywhere else "FAIL",
    )
    advantage, name, width, seed = minimum_advantage
    print(
        f"MINIMUM_WALL_ADVANTAGE {advantage:.6f} "
        f"operator={name} width={width} seed={seed}"
    )
    print(
        "CHAOTIC_MOST_STABLE_FIELDS",
        f"{chaotic_stablest_count}/{field_count}",
        f"failures={chaotic_stability_failures}",
    )
    return wall_above_chaotic_everywhere


def plot_width_256(measurements):
    width = 256
    x = np.arange(len(OPERATORS))
    bar_width = 0.24
    colors = ["#202020", "#4575b4", "#d73027"]
    labels = ["wall", "ordered interior", "chaotic interior"]
    figure, axis = plt.subplots(figsize=(8.2, 4.8), constrained_layout=True)

    for index, (region, color, label) in enumerate(zip(REGIONS, colors, labels)):
        means = []
        deviations = []
        for name, _, seeds in OPERATORS:
            values = np.asarray([
                measurements[name, width, seed][region] for seed in seeds
            ])
            means.append(values.mean())
            deviations.append(values.std(ddof=1) if len(values) > 1 else 0.0)
        axis.bar(
            x + (index - 1) * bar_width,
            means,
            bar_width,
            yerr=deviations,
            capsize=3,
            color=color,
            label=label,
        )

    axis.set_xticks(x, [label for _, label, _ in OPERATORS])
    axis.set_ylabel("genotype churn (changed-rule fraction)")
    axis.set_ylim(0, 1.08)
    axis.grid(axis="y", alpha=0.25)
    axis.legend(frameon=False, ncol=3, loc="upper center")
    axis.set_title(
        "Activity-domain walls churn more than chaotic interiors "
        "(q=1, L=256)\nmean ± sample SD across committed seeds"
    )
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "eoc_churn.png"
    figure.savefig(output, dpi=600, bbox_inches="tight", facecolor="white")
    pdf_output = output.with_suffix(".pdf")
    figure.savefig(pdf_output, bbox_inches="tight", facecolor="white")
    print("wrote", output, "and", pdf_output)


def main():
    scores = load_scores()
    measurements = load_measurements(scores)
    passes = print_measurements(measurements)
    plot_width_256(measurements)
    if not passes:
        raise SystemExit("wall churn must exceed chaotic-interior churn")


if __name__ == "__main__":
    main()
