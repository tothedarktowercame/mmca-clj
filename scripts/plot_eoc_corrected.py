"""Figure: null-corrected transport and combining across the synthetic sweep.

Replaces the single uncorrected synergy curve. Both panels plot the
filament-SPECIFIC score: on-filament minus a mask-matched random null of the
same cell count. Panel (c) carries the boundary fraction, which is why the
correction is needed -- the extracted filament swells from 20% of the field at
the bare operator to 39% under noise, so an uncorrected on-filament mean drifts
toward the field mean for reasons unrelated to transport.

Reads data/sweep_corrected.npz (see sweep_corrected.py); writes figures/.
"""
import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

OUT = "figures/edge_of_chaos_curve.pdf"

d = np.load("data/sweep_corrected.npz", allow_pickle=True)
names = [str(x) for x in d["names"]]
te, sy, bnd = d["te_c"], d["syn_c"], d["bnd"]

xs = np.arange(len(names))
edge_idx = names.index("q1.0")          # where the blend flank meets the noise flank
div = edge_idx + 0.5
peak = int(np.argmax(sy.mean(1)))
band_lo, band_hi = names.index("q0.3"), names.index("q0.7")

plt.rcParams.update({"font.family": "serif", "mathtext.fontset": "cm"})
fig, axes = plt.subplots(3, 1, figsize=(7.6, 6.4), sharex=True,
                         gridspec_kw={"height_ratios": [3, 3, 1.15], "hspace": 0.13})

def flanks(ax, ymax, label=False):
    ax.axvspan(-0.6, div, color="#dfe7f0", alpha=0.5, zorder=0, lw=0)
    ax.axvspan(div, len(xs) - 0.4, color="#f2e4d8", alpha=0.5, zorder=0, lw=0)
    ax.axvline(edge_idx, color="#666", lw=0.9, ls=":", zorder=1)
    if label:
        ax.text(2.6, ymax * 0.95, "order flank (blend, $q$)", ha="center", va="top",
                fontsize=8, color="#3a5a7a")
        ax.text((edge_idx + len(xs)) / 2.0, ymax * 0.95, "chaos flank (noise, $p$)",
                ha="center", va="top", fontsize=8, color="#9a5a3a")

for ax, arr, lab, tag in ((axes[0], te, "transport: directed transfer entropy\nabove a mask-matched null (bits)", "(a)"),
                          (axes[1], sy, "combining: synergy above a\nmask-matched null (bits)", "(b)")):
    m, e = arr.mean(1), arr.std(1, ddof=1) / np.sqrt(arr.shape[1])
    lo, hi = min(-0.05 * m.max(), m.min() - 0.10 * m.max()), m.max() * 1.28
    ax.set_xlim(-0.6, len(xs) - 0.4); ax.set_ylim(lo, hi)
    flanks(ax, hi)
    ax.axhline(0, color="0.45", lw=0.8, zorder=1)
    # q0.0 is bias-dominated (the shuffled surrogate outscores the real field
    # there); plotted open and excluded from the claims in the text.
    ax.errorbar(xs[1:], m[1:], yerr=e[1:], fmt="o-", color="#1a1a1a", ms=4, lw=1.4,
                capsize=2, zorder=3)
    ax.errorbar(xs[:2], m[:2], yerr=e[:2], fmt="o--", color="#999", mfc="white",
                ms=4, lw=1.0, capsize=2, zorder=2)
    # the transitional band, not a point: within q=0.3-0.7 the location of the
    # maximum is not resolved by these seeds (no point separates from q=0.4)
    ax.axvspan(band_lo - 0.5, band_hi + 0.5, color="#1b7a3d", alpha=0.10, zorder=1, lw=0)
    ax.plot(xs[band_lo:band_hi + 1], m[band_lo:band_hi + 1], "o-", color="#1b7a3d",
            ms=5, lw=1.8, zorder=4)
    ax.set_ylabel(lab, fontsize=8.5)
    ax.tick_params(labelsize=7.5)
    ax.text(0.012, 0.93, tag, transform=ax.transAxes, fontsize=10, fontweight="bold", va="top")

# panel (a) annotations: the zero crossing is the substantive new claim
m = te.mean(1)
neg = [k for k in range(edge_idx, len(names)) if m[k] < 0]
axes[0].annotate("filament-specific transport\nvanishes, then inverts",
                 (xs[neg[0]], m[neg[0]]), xytext=(xs[neg[0]] - 2.6, m.max() * 0.52),
                 fontsize=7.5, color="#9a5a3a", ha="left",
                 arrowprops=dict(arrowstyle="->", color="#9a5a3a", lw=0.9))
axes[0].annotate("sampled edge: transitional band\n$q\\approx0.3$\u2013$0.7$",
                 (xs[band_hi], m[band_hi]),
                 xytext=(xs[band_hi] + 0.8, m.max() * 1.16), ha="left", fontsize=8,
                 color="#1b7a3d", fontweight="bold",
                 arrowprops=dict(arrowstyle="->", color="#1b7a3d", lw=0.9))
axes[0].annotate("bias-dominated\n(sparse; excluded)", (xs[0], m[0]),
                 xytext=(xs[0] + 0.35, m.max() * 0.11), fontsize=7, color="#777", ha="left",
                 arrowprops=dict(arrowstyle="->", color="#999", lw=0.8))
ms = sy.mean(1)
axes[1].annotate(r"offset$+1$" "\n(bare propagator)", (edge_idx, ms[edge_idx]),
                 xytext=(edge_idx - 2.3, ms[edge_idx] - ms.max() * 0.42), fontsize=7.5,
                 color="#444", ha="left",
                 arrowprops=dict(arrowstyle="->", color="#666", lw=0.8))
axes[1].text(len(xs) - 0.6, ms.max() * 0.60, r"$N=8$ seeds/point", ha="right",
             va="center", fontsize=7.5, color="0.45", style="italic")

# panel (c): why the correction is needed
axb = axes[2]
bm = bnd.mean(1)
axb.set_xlim(-0.6, len(xs) - 0.4); axb.set_ylim(0, bm.max() * 1.85)
flanks(axb, bm.max() * 1.85, label=True)
axb.plot(xs, bm, "s-", color="#7a4a8a", ms=3, lw=1.2, zorder=3)
axb.set_ylabel("filament\nfraction", fontsize=8.5)
axb.text(0.012, 0.88, "(c)", transform=axb.transAxes, fontsize=10, fontweight="bold", va="top")
axb.tick_params(labelsize=7.5)
axb.set_xticks(xs); axb.set_xticklabels(names, fontsize=7, rotation=90)
axb.set_xlabel(r"increasing disorder $\rightarrow$", fontsize=9)

fig.suptitle("Transport and combining are both elevated in a transitional band", fontsize=11, y=0.945)
fig.savefig(OUT, bbox_inches="tight", facecolor="white")
fig.savefig(OUT.replace(".pdf", ".png"), dpi=600, bbox_inches="tight", facecolor="white")
print("wrote", OUT)
print("peak:", names[peak], "transport", f"{te.mean(1)[peak]:+.4f}", "combining", f"{sy.mean(1)[peak]:+.4f}")
