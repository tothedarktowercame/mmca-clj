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
SCAN = ROOT / "holes" / "E2b-offset2-finite-size-results.md"
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
    fig = plt.figure(figsize=(13, 8))
    grid = fig.add_gridspec(2, 4, height_ratios=[1.15, 1],
                            hspace=0.34, wspace=0.28)
    ax_a, ax_b, ax_c, ax_d = (fig.add_subplot(grid[0, i]) for i in range(4))
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
        ax_a.plot(sample[:, 1], sample[:, 2], "o", color=color, ms=3.5,
                  label=f"L={int(system_width)} ($w$={params[3]:.2f}, "
                        f"$R^2$={r_squared:.3f})")
        ax_a.plot(q_grid, logistic(q_grid, *params), "-", color=color,
                  lw=1.1, alpha=0.7)
        ax_b.plot(sample[:, 1], sample[:, 5], "o-", color=color, ms=3)
        ax_c.plot(sample[:, 1], sample[:, 7], "o-", color=color, ms=3)
        ax_d.plot(sample[:, 1], sample[:, 6], "o-", color=color, ms=3)
    ax_a.axvspan(0.10, 0.50, color="0.88", zorder=0)
    ax_a.set_title(r"(a) activity $a_G$: smooth logistic crossover", fontsize=9)
    ax_a.legend(fontsize=5.6, loc="upper left")
    ax_a.annotate("$w$ size-independent ($\\sim L^{-0.04}$):\n"
                  "no sharpening $\\Rightarrow$ no transition",
                  (0.52, 0.10), fontsize=6.2, ha="left", color="0.25")
    ax_b.set_title(r"(b) susceptibility $L\,\mathrm{Var}(a_G)$", fontsize=9)
    ax_b.annotate("no convergent interior maximum;\n"
                  "large-L maxima at the sampled boundary",
                  (0.02, 2.5), fontsize=6.5, color="#a50026", ha="left")
    ax_c.set_title("(c) finite-horizon P(collapse)", fontsize=9)
    ax_c.annotate("vanishes with size", (0.25, 0.35), fontsize=6.5, color="0.35")
    ax_d.set_title("(d) Binder cumulant (no crossing)", fontsize=9)
    ax_d.axhline(0, color="gray", lw=0.5, ls=":")
    for axis in (ax_a, ax_b, ax_c, ax_d):
        axis.set_xlabel("propagator duty cycle  $q$", fontsize=8)
        axis.tick_params(labelsize=7)
        axis.grid(alpha=0.25)

    panels = [(0, "$q=0$: blend-dominated (low activity)"),
              (50, "$q=0.05$: sparse"),
              (250, "$q=0.25$: intermediate (coexisting domains)"),
              (750, "$q=0.75$: propagator-dominated (high activity)")]
    for i, (q_code, label) in enumerate(panels):
        axis = fig.add_subplot(grid[1, i])
        field = np.loadtxt(DATA / f"eoc_phase_q{q_code:03d}.txt", dtype=int)
        axis.imshow(scores[field], cmap="coolwarm", vmin=0, vmax=1,
                    aspect="auto", interpolation="nearest")
        axis.set_xticks([])
        axis.set_yticks([])
        axis.set_title(label, fontsize=7.5)
    fig.suptitle("offset$+2$ feedforward finite-size scan ($L=30$–$240$, "
                 "32 seeds): no sharp critical point, a finite-width crossover band\n"
                 "(bottom: paired illustrative realization, width 240, single seed, shared IC)",
                 fontsize=10)
    FIGURES.mkdir(parents=True, exist_ok=True)
    output = FIGURES / "eoc_phase.png"
    fig.savefig(output, dpi=115, bbox_inches="tight", facecolor="white")
    print("wrote", output)


if __name__ == "__main__":
    main()
