"""Figure: the order parameter is the strength of the phenotype-to-genotype coupling.

Panel (a) places every construction on the elementary-rule damage-spreading
scale, split by whether the genotype update reads the phenotype. Panel (b) turns
the coupling into a dial: conservative transport whose swap probability is gated
on the local phenotype interface, against an ungated control at matched mean
swap probability. Both are bijective and leave the rule histogram invariant, so
the two curves differ only in whether the genotype can see the phenotype.
"""
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt, numpy as np

ROOT = Path(__file__).resolve().parents[1]
S = {}
for line in (ROOT / "data" / "regime_placement_summary.tsv").read_text().splitlines()[1:]:
    k, n, m, sem, _ = line.split("\t"); S[n] = (float(m), float(sem))

ECA = ["rule 0", "rule 204", "rule 90", "rule 110", "rule 54", "rule 30"]
FF = ["$P_a$ (bare)", "blend $1.00$", "braid $P_a$/two-4", "mutation $0.10$",
      "braid + blend $0.70$", "async update $0.25$", "niches $P_a$/two-4 (8)",
      "blend $0.70$", "niches $P_a$/two-4 (16)", "async update $0.75$",
      "mutation $0.40$", "preserving limit", "rot$+1$", "blend $0.35$",
      "braid rot$+2$/rot$+4$", "blend $0.00$"]
UNG = [f"ungated ${r}$" for r in ("0.20", "0.35", "0.50", "0.75", "1.00")]
FB = [("river construction", 12.97), ("transport $0.50$", S["transport $0.50$"][0]),
      ("transport $1.00$", S["transport $1.00$"][0])]
RATES = [0.0, 0.05, 0.10, 0.20, 0.35, 0.50, 0.75, 1.00]

fig, (ax, bx) = plt.subplots(1, 2, figsize=(13.6, 4.3), gridspec_kw=dict(width_ratios=[1.42, 1], wspace=0.22))

for a in (ax,):
    a.axvspan(.02, 8, color="#dce8f5", alpha=.75, zorder=0)
    a.axvspan(8, 22, color="#e6f2e0", alpha=.85, zorder=0)
    a.axvspan(22, 60, color="#f9e0dc", alpha=.75, zorder=0)
for x, lab, c in ((.55, "ordered", "#31567d"), (13.5, "complex", "#3d6b2c"), (37, "chaotic", "#8d3a2f")):
    ax.text(x, 3.66, lab, ha="center", fontsize=10, style="italic", color=c)
DY = {"rule 110": 24, "rule 54": 11}
for n in ECA:
    v = S[n][0]
    ax.scatter([max(v, .025)], [3.0], marker="|", s=470, c="k", lw=1.7, zorder=4)
    ax.annotate(n, (max(v, .025), 3.0), textcoords="offset points",
                xytext=(0, DY.get(n, 11)), ha="center", fontsize=8.2)
for n in FF:
    ax.scatter([max(S[n][0], .025)], [2.0], marker="o", s=38, c="#4c72b0",
               edgecolor="white", lw=.6, zorder=4)
for n in UNG:
    ax.scatter([S[n][0]], [2.0], marker="s", s=34, c="#7fa8d4", edgecolor="white", lw=.6, zorder=4)
ax.scatter([5.51], [2.0], marker="o", s=58, c="#c44e52", edgecolor="white", lw=.7, zorder=5)
for n, dy, c in (("$P_a$ (bare)", -19, "#31567d"), ("blend $0.00$", 11, "#31567d"),
                 ("ungated $1.00$", -19, "#4b7bb0")):
    ax.annotate(n.replace(" update", ""), (S[n][0] if n in S else 5.51, 2.0),
                textcoords="offset points", xytext=(0, dy), ha="center", fontsize=7.5, color=c)
ax.annotate("river, edge cut", (5.51, 2.0), textcoords="offset points", xytext=(-4, 26),
            ha="center", fontsize=7.5, color="#c44e52",
            arrowprops=dict(arrowstyle="-", color="#c44e52", lw=.5, shrinkA=1, shrinkB=3))
FBDY = {"river construction": -21, "transport $0.50$": 13, "transport $1.00$": -35}
for n, v in FB:
    ax.scatter([v], [1.0], marker="*", s=310, c="#c44e52", edgecolor="white", lw=.8, zorder=5)
    ax.annotate(n, (v, 1.0), textcoords="offset points", xytext=(0, FBDY[n]),
                ha="center", fontsize=8.1, color="#c44e52", weight="bold")
ax.annotate("", xy=(12.97, 1.44), xytext=(8.15, 1.83),
            arrowprops=dict(arrowstyle="->", color="#8d3a2f", lw=1.5))
ax.set_xscale("symlog", linthresh=1.0, linscale=.7)
ax.set_xlim(.02, 60); ax.set_ylim(.20, 3.98)
ax.set_yticks([1, 2, 3])
ax.set_yticklabels(["genotype reads $X$", "genotype never reads $X$", "elementary rules"], fontsize=8.8)
ax.set_xticks([.1, .3, 1, 3, 10, 30]); ax.set_xticklabels([".1", ".3", "1", "3", "10", "30"])
ax.set_xlabel("causal reach (damaged phenotype cells at $dt=59$)", fontsize=9)
ax.grid(axis="x", alpha=.3); ax.set_axisbelow(True)
ax.set_title("(a) every construction on one scale", fontsize=9.8, pad=17)

bx.axhspan(0, 8, color="#dce8f5", alpha=.75, zorder=0)
bx.axhspan(8, 22, color="#e6f2e0", alpha=.85, zorder=0)
bx.axhspan(22, 40, color="#f9e0dc", alpha=.75, zorder=0)
g = np.array([S[f"transport ${r:.2f}$"] for r in RATES])
bx.errorbar(RATES, g[:, 0], yerr=g[:, 1], marker="o", ms=6, lw=2, c="#c44e52",
            capsize=3, label="gated: swap probability reads $X$", zorder=4)
ur = [0.0] + [0.20, 0.35, 0.50, 0.75, 1.00]
u = np.array([S["transport $0.00$"]] + [S[f"ungated ${r:.2f}$"] for r in ur[1:]])
bx.errorbar(ur, u[:, 0], yerr=u[:, 1], marker="s", ms=5.5, lw=2, c="#4c72b0", ls="--",
            capsize=3, label="ungated control: blind to $X$", zorder=4)
for y, n in ((8.0, "rule 90"), (18.30, "rule 54"), (1.0, "rule 204")):
    bx.axhline(y, color="k", lw=.8, ls=":", alpha=.6)
    bx.text(1.02, y, n, fontsize=7.4, va="center", ha="left")
bx.set_xlim(-.04, 1.16); bx.set_ylim(0, 32)
bx.set_xlabel("coupling strength (phenotype-gated transport rate)", fontsize=9)
bx.set_ylabel("causal reach", fontsize=9)
bx.legend(fontsize=8, loc="upper left", framealpha=.92)
bx.set_title("(b) the coupling is the dial", fontsize=9.8, pad=17)
bx.grid(alpha=.25); bx.set_axisbelow(True)
for sp in ("top", "right"): bx.spines[sp].set_visible(False)
for sp in ("top", "right", "left"): ax.spines[sp].set_visible(False)
fig.suptitle("Causal reach is set by the strength of the phenotype-to-genotype coupling, not by any parameter of the update",
             fontsize=11.2, y=1.0)
for ext in ("png", "pdf"):
    fig.savefig(ROOT / f"figures/regime_placement.{ext}", dpi=150, bbox_inches="tight")
print("wrote figures/regime_placement.{png,pdf}")
