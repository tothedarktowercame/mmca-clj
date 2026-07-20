#!/usr/bin/env python3
"""Reproduce the EoC finite-size crossover figure from repository data."""

from pathlib import Path
import re

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from scipy.optimize import curve_fit

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
SCAN = ROOT / "holes" / "E2b-offset1-finite-size-results.md"
FIGURES = ROOT / "figures"


def logistic(q, a0, amplitude, q0, width):
    return a0 + (amplitude - a0) / (1 + np.exp(-(q - q0) / width))


def load_scan():
    rows = []
    pattern = re.compile(
        r"\|\s*(\d+)\s*\|\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|"
        r"\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|\s*([-\d.]+)\s*\|"
        r"\s*([-\d.]+)\s*\|\s*([\d.]+)\s*\|")
    for line in SCAN.read_text().splitlines():
        match = pattern.match(line)
        if match:
            rows.append([float(value) for value in match.groups()])
    return np.array(rows)


def load_scores():
    rows = np.loadtxt(DATA / "rule_activity_scores.txt")
    scores = np.zeros(256)
    scores[rows[:, 0].astype(int)] = rows[:, 1]
    return scores


def main():
    scan = load_scan()
    scores = load_scores()
    widths = sorted(set(scan[:, 0]))
    colors = {30: "#4575b4", 60: "#74add1", 120: "#f46d43", 240: "#a50026"}
    fig = plt.figure(figsize=(12, 7.6))
    grid = fig.add_gridspec(2, 4, height_ratios=[1.2, 1],
                            hspace=0.32, wspace=0.32)
    ax_a = fig.add_subplot(grid[0, 0:2])
    ax_b = fig.add_subplot(grid[0, 2:4])
    q_grid = np.linspace(0, 1, 200)
    for system_width in widths:
        sample = scan[scan[:, 0] == system_width]
        color = colors[int(system_width)]
        params, _ = curve_fit(
            logistic, sample[:, 1], sample[:, 2],
            p0=[0.06, 0.7, 0.18, 0.18],
            bounds=([0, 0.4, 0, 0.02], [0.12, 1.0, 1, 1]), maxfev=20000)
        residual = sample[:, 2] - logistic(sample[:, 1], *params)
        r_squared = 1 - np.sum(residual ** 2) / np.sum(
            (sample[:, 2] - sample[:, 2].mean()) ** 2)
        ax_a.plot(sample[:, 1], sample[:, 2], "o", color=color, ms=4,
                  label=f"L={int(system_width)} ($w$={params[3]:.2f}, "
                        f"$R^2$={r_squared:.3f})")
        ax_a.plot(q_grid, logistic(q_grid, *params), "-", color=color,
                  lw=1.2, alpha=0.7)
        ax_b.plot(sample[:, 1], sample[:, 5], "o-", color=color, ms=4,
                  label=f"L={int(system_width)}")
    ax_a.axvspan(0.10, 0.50, color="0.88", zorder=0)
    ax_a.set_title(r"(a) activity $a_G$: a smooth crossover that never sharpens",
                   fontsize=10)
    ax_a.legend(fontsize=6.8, loc="upper left")
    ax_a.annotate("the crossover width $w$ does not shrink with size\n"
                  "($w \\sim L^{-0.04}$): the curve never steepens into a\n"
                  "step, as a real transition would $\\Rightarrow$ no critical point",
                  (0.28, 0.10), fontsize=7.3, ha="left", color="0.25")
    ax_a.set_ylabel(r"genotype activity $a_G$", fontsize=9)

    ax_b.set_title(r"(b) run-to-run spread grows with size only at $q=1$",
                   fontsize=10)
    ax_b.legend(fontsize=7, loc="upper center", ncol=2)
    ax_b.annotate("at $q=1$ the curves fan out with $L$:\n"
                  "activity does not average out\n"
                  "$\\Rightarrow$ system-spanning domains",
                  (0.40, 2.25), fontsize=7.3, ha="left", color="#a50026")
    ax_b.annotate("in the band: flat and low across $L$\n"
                  "(averages out, no critical point)",
                  (0.05, 1.55), fontsize=7.3, ha="left", color="0.30")
    ax_b.set_ylabel(r"run-to-run activity spread  $L\,\mathrm{Var}(a_G)$", fontsize=9)

    for axis in (ax_a, ax_b):
        axis.set_xlabel(r"propagator fraction $q$   (0: all blend $\to$ 1: all propagator)",
                        fontsize=8.5)
        axis.tick_params(labelsize=8)
        axis.grid(alpha=0.25)

    panels = [(0, "$q=0$: all blend (low activity)"),
              (50, "$q=0.05$: sparse"),
              (250, "$q=0.25$: coexisting domains"),
              (1000, "$q=1$: system-spanning domains")]
    for i, (q_code, label) in enumerate(panels):
        axis = fig.add_subplot(grid[1, i])
        field = np.loadtxt(DATA / f"eoc_phase_q{q_code:03d}.txt", dtype=int)
        axis.imshow(scores[field], cmap="coolwarm", vmin=0, vmax=1,
                    aspect="auto", interpolation="nearest")
        axis.set_xticks([])
        axis.set_yticks([])
        axis.set_title(label, fontsize=8)
    fig.suptitle("offset$+1$ feedforward scan across system size $L$ (row width in cells, "
                 "$30$–$240$; 32 seeds): a smooth crossover with no critical point in the band,\n"
                 "yet run-to-run fluctuations grow with $L$ at $q=1$, where system-spanning "
                 "domains form  (bottom: one realization across $q$, at $L=240$)",
                 fontsize=9.5)
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "eoc_phase.png"
    fig.savefig(output, dpi=115, bbox_inches="tight", facecolor="white")
    print("wrote", output)


if __name__ == "__main__":
    main()
