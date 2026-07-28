"""Compact two-page population of diversity-dial spacetime fields.

Each page is a 2x3 population.  Genotype and phenotype are stacked within a
tile, eliminating the full-width row gutters of the previous layout while
retaining the lattice aspect ratio and the PDF-safe raster settings.
"""

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


def load(tag):
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
    # PDF backends embed interpolation="none" arrays at their native size.
    # Exact 5x pixel replication raises the placed resolution above 300 ppi
    # without inventing intermediate values or changing a single CA cell.
    genotype = np.repeat(np.repeat(genotype, 5, axis=0), 5, axis=1)
    phenotype = np.repeat(np.repeat(phenotype, 5, axis=0), 5, axis=1)
    return genotype, phenotype


manifest = {}
for line in (DATA / "diversity_sampler_manifest.tsv").read_text(
    encoding="utf-8"
).splitlines()[1:]:
    _, tag, _, rules, effective = line.split("\t")
    manifest[tag] = (int(rules), float(effective))


PAGES = {
    "a": (
        "Structured high-diversity fields",
        [
            ("dial1-braid-pa-t4", "temporal braid", "$P_a$ / two-4-cycle"),
            ("dial1-braid-r2-r4", "collapsing pair", "rot$+2$ / rot$+4$"),
            ("dial2-blend035", "continuous blend", "strength $0.35$"),
            ("dial2-blend070", "continuous blend", "strength $0.70$"),
            ("dial2-niches16", "spatial niches", "width $16$"),
            ("dial2-niches64", "spatial niches", "width $64$"),
        ],
    ),
    "b": (
        "Mechanism-specific suppression and a propagating control",
        [
            ("dial1-noise020", "replacement mutation", "rate $0.20$"),
            ("dial1-noise040", "replacement mutation", "rate $0.40$"),
            ("dial2-async025", "asynchronous holding", "update $0.25$"),
            ("dial2-async050", "asynchronous holding", "update $0.50$"),
            ("dial2-async075", "asynchronous holding", "update $0.75$"),
            (
                "dial3-braid-blend070",
                "propagating control",
                "braid + blend $0.70$",
            ),
        ],
    ),
}


for page, (title, entries) in PAGES.items():
    figure = plt.figure(figsize=(11.2, 8.25))
    outer = figure.add_gridspec(
        3,
        2,
        hspace=0.26,
        wspace=0.045,
        top=0.935,
        bottom=0.04,
        left=0.02,
        right=0.995,
    )
    for index, (tag, mechanism, setting) in enumerate(entries):
        row, column = divmod(index, 2)
        tile = outer[row, column].subgridspec(2, 1, hspace=0.04)
        genotype_axis = figure.add_subplot(tile[0, 0])
        phenotype_axis = figure.add_subplot(tile[1, 0])
        genotype, phenotype = load(tag)
        genotype_axis.imshow(
            genotype, aspect="equal", interpolation="none"
        )
        phenotype_axis.imshow(
            phenotype,
            cmap="gray",
            vmin=0,
            vmax=255,
            aspect="equal",
            interpolation="none",
        )
        rules, effective = manifest[tag]
        genotype_axis.set_title(
            f"{mechanism}: {setting}\n"
            rf"$N={rules}$, $N_{{\rm eff}}={effective:.1f}$",
            fontsize=8.4,
            pad=3,
        )
        phenotype_axis.set_xlabel(
            "genotype $G$ above; phenotype $X$ below",
            fontsize=7.2,
            labelpad=2,
        )
        suppressed = page == "b" and index < 5
        for axis in (genotype_axis, phenotype_axis):
            axis.set_xticks([])
            axis.set_yticks([])
            for spine in axis.spines.values():
                spine.set_linewidth(1.25 if suppressed else 0.65)
                spine.set_color("#b2182b" if suppressed else "black")

    figure.suptitle(f"({page}) {title}", fontsize=11.5, y=0.985)
    figure.savefig(
        FIGURES / f"diversity_spread_{page}.pdf",
        dpi=600,
        bbox_inches="tight",
        facecolor="white",
    )
    figure.savefig(
        FIGURES / f"diversity_spread_{page}.png",
        dpi=300,
        bbox_inches="tight",
        facecolor="white",
    )
    print(f"wrote figures/diversity_spread_{page}.{{png,pdf}}")
