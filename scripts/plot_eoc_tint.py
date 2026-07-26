#!/usr/bin/env python3
"""Plot isolated-rule activity tints and report three-way regime entropy."""

from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
FIGURES = ROOT / "figures"


def load_scores():
    rows = np.loadtxt(DATA / "rule_activity_scores.txt")
    scores = np.zeros(256)
    scores[rows[:, 0].astype(int)] = rows[:, 1]
    return scores


def regime_entropy(field, scores):
    """Entropy over the paper's three named bands, normalized over band cells."""
    activity = scores[field]
    counts = np.array([
        np.count_nonzero(activity < 0.15),
        np.count_nonzero((activity >= 0.30) & (activity <= 0.48)),
        np.count_nonzero(activity > 0.48),
    ], dtype=float)
    probabilities = counts / counts.sum()
    entropy = -np.sum(probabilities * np.log2(probabilities), where=probabilities > 0)
    return entropy, probabilities, counts.sum() / activity.size


def main():
    scores = load_scores()
    fields = [
        ("offset1", "offset $+1$ (8-cycle)"),
        ("two4cyc", r"$[6\,7\,0\,2\,1\,4\,3\,5]$ (two 4-cycles)"),
        ("sigma16250374", r"$\sigma=16250374$"),
    ]
    fig, axes = plt.subplots(1, 3, figsize=(11.4, 6.6), constrained_layout=True)
    image = None
    for axis, (name, label) in zip(axes, fields):
        field = np.loadtxt(DATA / f"eoc_tint_{name}.txt", dtype=int)
        entropy, probabilities, coverage = regime_entropy(field, scores)
        image = axis.imshow(scores[field], cmap="coolwarm", vmin=0, vmax=1,
                            aspect="auto", interpolation="nearest")
        axis.set_title(f"{label}\nthree-way entropy = {entropy:.2f} bits", fontsize=10)
        axis.set_xlabel("cell")
        axis.set_ylabel("time")
        print(f"REGIME_ENTROPY {name} {entropy:.6f} "
              f"bands={probabilities.tolist()} coverage={coverage:.6f}")
    colorbar = fig.colorbar(image, ax=axes, shrink=0.72, pad=0.03)
    colorbar.set_label("isolated-rule activity")
    fig.suptitle("Genotype fields tinted by isolated-rule activity", fontsize=12)
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "eoc_tint.png"
    fig.savefig(output, dpi=600, bbox_inches="tight", facecolor="white")
    pdf_output = output.with_suffix(".pdf")
    fig.savefig(pdf_output, bbox_inches="tight", facecolor="white")
    print("wrote", output, "and", pdf_output)


if __name__ == "__main__":
    main()
