"""Two coupling dials, opposite curvature.

Both constructions vary the gain of the phenotype-to-genotype loop and nothing
else. Conservative transport gates its swap probability on the local phenotype
interface; the river gates the currency of the four context bits its genotype
step reads. Both are monotone, and neither reaches beyond its full-coupling
endpoint -- but transport buys most of its reach in the first fifth of the dial
and saturates, while the river buys nearly half of its span in the last eighth.
"""
from pathlib import Path
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt, numpy as np

ROOT = Path(__file__).resolve().parents[1]
G = np.array([[float(x) for x in l.split("\t")[:3]]
              for l in (ROOT/"data"/"river_gain_summary.tsv").read_text().splitlines()[1:]])
S = {}
for l in (ROOT/"data"/"regime_placement_summary.tsv").read_text().splitlines()[1:]:
    k, n, m, sem, _ = l.split("\t"); S[n] = (float(m), float(sem))
RATES = [0.0, 0.05, 0.10, 0.20, 0.35, 0.50, 0.75, 1.00]
T = np.array([[r, *S[f"transport ${r:.2f}$"]] for r in RATES])
U = np.array([[0.0, *S["transport $0.00$"]]] +
             [[r, *S[f"ungated ${r:.2f}$"]] for r in (0.20, 0.35, 0.50, 0.75, 1.00)])

fig, axes = plt.subplots(1, 2, figsize=(11.4, 4.1), sharey=True,
                         gridspec_kw=dict(wspace=0.08))
for ax, (X, lab, c, extra) in zip(axes, [
        (T, "conservative transport\n(swap probability reads $X$)", "#c44e52", U),
        (G, "the river\n(context bits read the live $X$)", "#4c72b0", None)]):
    ax.axhspan(0, 8, color="#dce8f5", alpha=.7, zorder=0)
    ax.axhspan(8, 22, color="#e6f2e0", alpha=.8, zorder=0)
    ax.axhspan(22, 40, color="#f9e0dc", alpha=.7, zorder=0)
    ax.errorbar(X[:, 0], X[:, 1], yerr=X[:, 2], marker="o", ms=6, lw=2.1,
                c=c, capsize=3, zorder=4, label=lab.split("\n")[0])
    if extra is not None:
        ax.errorbar(extra[:, 0], extra[:, 1], yerr=extra[:, 2], marker="s", ms=5,
                    lw=1.8, ls="--", c="#7f7f7f", capsize=3, zorder=3,
                    label="ungated control (blind to $X$)")
        ax.legend(fontsize=7.6, loc="upper left", framealpha=.93)
    for y, n in ((8.0, "rule 90"), (18.30, "rule 54"), (1.0, "rule 204")):
        ax.axhline(y, color="k", lw=.8, ls=":", alpha=.55)
        ax.text(1.015, y, n, fontsize=7.2, va="center", ha="left")
    ax.set_xlim(-.04, 1.14); ax.set_ylim(0, 30)
    ax.set_xlabel("coupling gain", fontsize=9.5)
    ax.set_title(lab, fontsize=9.5, pad=8)
    ax.grid(alpha=.22); ax.set_axisbelow(True)
    for sp in ("top", "right"): ax.spines[sp].set_visible(False)
axes[0].set_ylabel("causal reach (damaged phenotype cells at $dt=59$)", fontsize=9.5)
axes[0].annotate("concave: pays early,\nsaturates past $0.75$", (0.42, 24.5), fontsize=8,
                 color="#8d3a2f", style="italic", ha="center")
axes[1].annotate("convex: 45% of the span\nin the last eighth", (0.44, 24.5), fontsize=8,
                 color="#31567d", style="italic", ha="center")
axes[1].annotate("", xy=(1.0, 12.39), xytext=(0.875, 7.38),
                 arrowprops=dict(arrowstyle="->", color="#31567d", lw=1.4))
fig.suptitle("Causal reach is monotone in coupling gain in both constructions, with opposite curvature",
             fontsize=11, y=1.07)
for e in ("png", "pdf"):
    fig.savefig(ROOT/f"figures/gain_curves.{e}", dpi=150, bbox_inches="tight")
print("wrote figures/gain_curves.{png,pdf}")
