"""Two-page spread of diversity-dial spacetime fields, organised by argument.

Page (a) shows mechanisms that reach high sustained diversity and propagate;
page (b) shows the two flanks that suppress reach, with a propagating field at
matched diversity as the control. Panels are the diversity_sampler runs
(full-population seed 0, L=256, T=70), reorganised from the by-script grouping
of plot_diversity_sampler.py into the grouping the argument uses.

Panels use aspect="equal" and interpolation="none": "auto" shears the lattice,
and layering a two-valued panel makes the PDF backend emit a one-bit indexed
stream. See README.md on the figure pipeline. Generate with .venv.
"""
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt, numpy as np

ROOT = Path(__file__).resolve().parents[1]
DATA, FIGURES = ROOT / "data", ROOT / "figures"
COLOUR_OVERRIDES = {29: "#00ff33", 30: "#0033ff", 71: "#00ff33", 90: "#ffcc00",
                    110: "#ff3300", 118: "#ff3300", 120: "#0033ff", 135: "#0033ff",
                    137: "#ff3300", 145: "#ff3300", 165: "#ffcc00", 184: "#00ff33",
                    225: "#0033ff", 226: "#00ff33"}

def rgb(rule):
    c = COLOUR_OVERRIDES.get(rule, f"#{rule:02x}{rule:02x}{rule:02x}").lstrip("#")
    return [int(c[i:i+2], 16) for i in (0, 2, 4)]

def load(tag):
    raw = (DATA / f"diversity_sampler_{tag}.txt").read_text().splitlines()
    g, p = raw.index("GEN") + 1, raw.index("PHE")
    gen = np.array([[rgb(int(r)) for r in row.split()] for row in raw[g:p] if row.strip()],
                   dtype=np.uint8)
    phe = np.array([[0 if c == "1" else 255 for c in row] for row in raw[p+1:] if row.strip()],
                   dtype=np.uint8)
    return gen, phe

PAGES = {
 "a": ("Reaching high diversity: four mechanisms that share no machinery",
       [("dial1-braid-pa-t4",   "braid $P_a$ / two-4-cycle", "temporal alternation", 106, 3.23),
        ("dial1-braid-r2-r4",   "braid rot$+2$ / rot$+4$", "two operators that collapse alone", 78, 3.81),
        ("dial2-blend070",      "$P_a$, blend strength $0.70$", "continuous blending", 108, 3.60),
        ("dial2-niches64",      "$P_a$ / two-4-cycle niches, width $64$", "spatial partition", 121, 2.72)]),
 "b": ("The two flanks: reach suppressed at diversity the other mechanisms also reach",
       [("dial1-noise040",      "$P_a$ + replacement noise $0.40$", "OVER-MUTATION", 159, 0.00),
        ("dial2-async025",      "$P_a$, asynchronous fraction $0.25$", "OVER-PRESERVATION", 104, 1.22),
        ("dial3-braid-async075","braid $P_a$ / two-4-cycle, async $0.75$", "OVER-PRESERVATION on a braid", 111, 0.75),
        ("dial3-braid-blend070","braid $P_a$ / two-4-cycle, blend $0.70$", "CONTROL: propagates at matched diversity", 129, 3.88)]),
}

for page, (title, entries) in PAGES.items():
    fig, axes = plt.subplots(4, 2, figsize=(11.2, 7.4),
                             gridspec_kw=dict(hspace=0.52, wspace=0.07,
                                              height_ratios=[1, 1, 1, 1]))
    for row, (tag, name, role, rules, reach) in enumerate(entries):
        gen, phe = load(tag)
        for col, (img, cmap, lab) in enumerate(
                ((gen, None, "genotype"), (phe, "gray", "phenotype"))):
            ax = axes[row, col]
            ax.imshow(img, cmap=cmap, aspect="equal", interpolation="none")
            ax.set_xticks([]); ax.set_yticks([])
            for sp in ax.spines.values():
                sp.set_linewidth(1.4 if page == "b" and row < 3 else 0.6)
                sp.set_color("#c00" if page == "b" and row < 3 else "black")
            if row == 0: ax.set_title(lab, fontsize=8, style="italic", pad=3)
        axes[row, 0].set_ylabel(f"$N$={rules}", fontsize=7.5)
        axes[row, 0].text(0, -0.16, f"{name} — {role}   (causal reach {reach:.2f} cells)",
                          transform=axes[row, 0].transAxes, fontsize=8.2, va="top")
    fig.suptitle(f"({page}) {title}", fontsize=11, y=0.965)
    fig.savefig(FIGURES / f"diversity_spread_{page}.pdf", dpi=150, bbox_inches="tight")
    fig.savefig(FIGURES / f"diversity_spread_{page}.png", dpi=150, bbox_inches="tight")
    print(f"wrote figures/diversity_spread_{page}.{{png,pdf}}")
