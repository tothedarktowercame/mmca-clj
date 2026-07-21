#!/usr/bin/env python3
"""Plot activity-regime interfaces and their finite-size box dimensions."""

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy.ndimage import uniform_filter

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"
BOX_SIZES = np.array([2, 4, 8, 16, 32, 64])


def load_scores():
    rows = np.loadtxt(DATA / "rule_activity_scores.txt")
    scores = np.zeros(256)
    scores[rows[:, 0].astype(int)] = rows[:, 1]
    return scores


def activity_boundary(field, scores):
    """Boundary of the smoothed score>0.35 activity domains."""
    active = uniform_filter(scores[field], size=5) > 0.35
    grad_t, grad_x = np.gradient(active.astype(float))
    return np.hypot(grad_t, grad_x) > 0


def box_dimension(boundary):
    """Fit D=-slope(log N(epsilon), log epsilon) at the fixed paper scales."""
    counts = []
    for size in BOX_SIZES:
        height, width = boundary.shape
        padded = np.pad(boundary,
                        ((0, (-height) % size), (0, (-width) % size)))
        blocks = padded.reshape(padded.shape[0] // size, size,
                                padded.shape[1] // size, size)
        counts.append(np.count_nonzero(blocks.any(axis=(1, 3))))
    counts = np.asarray(counts)
    if np.any(counts == 0):
        return np.nan
    slope, _ = np.polyfit(np.log(BOX_SIZES), np.log(counts), 1)
    return -slope


def main():
    scores = load_scores()
    operators = [
        ("offset1", "offset $+1$", [1, 2, 3], "#4575b4"),
        ("two4cyc", r"$[6\,7\,0\,2\,1\,4\,3\,5]$", [1, 2, 3], "#1b7837"),
        ("sigma16250374", r"$\sigma$=16250374", [1, 2, 3], "#7570b3"),
        ("river", "river", [1, 2], "#d73027"),
    ]
    widths = [128, 256, 512, 768]
    fig = plt.figure(figsize=(12, 4.4))
    grid = fig.add_gridspec(1, 3, wspace=0.12)

    for column, (name, label, _, _) in enumerate(operators):
        field = np.loadtxt(DATA / f"eoc_interface_top_{name}.txt", dtype=int)
        square = field[-256:, :]
        boundary = activity_boundary(square, scores)
        dimension = box_dimension(boundary)
        axis = fig.add_subplot(grid[0, column])
        axis.imshow(boundary, cmap="gray_r", vmin=0, vmax=1,
                    interpolation="nearest", aspect="equal")
        suffix = "no boundary" if np.isnan(dimension) else f"D={dimension:.2f}"
        axis.set_title(f"{label}: {suffix}", fontsize=11)
        axis.set_xticks([])
        axis.set_yticks([])
        print(f"BOX_DIMENSION L256 {name} "
              f"{'nan' if np.isnan(dimension) else f'{dimension:.6f}'}")

    # Finite-size stability of D is computed for the record (and quoted in the
    # caption) but no longer plotted: the old D-vs-L panel only restated the
    # numbers already shown in the boundary-image titles.
    for name, _, seeds, _ in operators:
        for width in widths:
            values = []
            for seed in seeds:
                field = np.loadtxt(
                    DATA / f"eoc_interface_{name}_L{width}_s{seed}.txt",
                    dtype=int)
                values.append(box_dimension(activity_boundary(field, scores)))
            finite = np.asarray([value for value in values if np.isfinite(value)])
            mean = np.mean(finite) if finite.size else np.nan
            print(f"BOX_DIMENSION_SERIES {name} L={width} "
                  f"mean={mean:.3f} values={values}")
    fig.suptitle("Activity-domain boundaries at threshold 0.35", fontsize=12)
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "eoc_interface.png"
    fig.savefig(output, dpi=150, bbox_inches="tight", facecolor="white")
    print("wrote", output)


if __name__ == "__main__":
    main()
