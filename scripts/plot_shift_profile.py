"""Diagnostic: transport as a function of how far the filament mask is displaced.

The geometry-preserving null in one picture. Circularly translate the filament
mask along x by d and score the field under it. At d=0 the mask sits on the real
filament; as d grows it is the same shape in a generic place. Averaged over the
8 seeds of a sweep point, for three regimes.

At the edge the profile spikes at d=0 and falls to a flat plateau -- the filament
is special. Under strong noise the spike is INVERTED: the real filament scores
below the plateau, which is the sign change reported in the paper.

Run from the mmca-clj repo root. Writes figures/shift_profile.{pdf,png}.
"""
import numpy as np, glob, sys, pathlib
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from geom_null import prep, shift_profile, D_MIN

plt.rcParams.update({"font.family": "serif", "font.size": 9,
                     "mathtext.fontset": "dejavuserif"})

REGIMES = [("data/eoc_sweep_order_q400_s*.txt", r"transitional band ($q=0.4$)", "#1b7a3d"),
           ("data/eoc_sweep_order_q1000_s*.txt", r"bare propagator ($q=1$)", "#333333"),
           ("data/eoc_sweep_chaos_p700_s*.txt", r"strong noise ($p=0.7$)", "#b3541e")]

fig, ax = plt.subplots(figsize=(6.4, 3.4))
for pat, label, colour in REGIMES:
    profs = []
    for path in sorted(glob.glob(pat)):
        te, _, bnd = prep(path)
        profs.append(shift_profile(te, bnd))
    P = np.array(profs)
    L = P.shape[1]
    d = np.arange(-(L // 2), L - L // 2)
    m = np.roll(P.mean(0), L // 2)
    ax.plot(d, m, lw=1.3, color=colour, label=label)
    ax.plot([0], [m[L // 2]], "o", color=colour, ms=5, zorder=5)
    plateau = np.concatenate([P[:, D_MIN:L - D_MIN + 1]]).mean()
    ax.axhline(plateau, color=colour, lw=0.7, ls=":", alpha=0.8)

ax.axvspan(-D_MIN, D_MIN, color="0.85", alpha=0.5, lw=0, zorder=0)
lo, hi = ax.get_ylim()
ax.text(22, lo + 0.02 * (hi - lo), "shaded: excluded from the null\n(mask still overlaps itself)",
        ha="left", va="bottom", fontsize=7, color="0.35")
ax.annotate("under noise the real filament\nfalls BELOW its own plateau",
            (0, 0.0236), xytext=(-118, 0.004), ha="left", fontsize=7.5,
            color="#b3541e", arrowprops=dict(arrowstyle="->", color="#b3541e", lw=0.9))
ax.axhline(0, color="0.6", lw=0.8)
ax.set_xlabel(r"displacement $d$ of the filament mask along $x$ (cells)")
ax.set_ylabel("directed transfer entropy\nunder the displaced mask (bits)")
ax.set_title("The filament is special because of where it is, not its shape", fontsize=10)
ax.legend(frameon=False, fontsize=8, loc="upper right", bbox_to_anchor=(1.0, 0.86))
ax.spines[["top", "right"]].set_visible(False)
fig.tight_layout()
fig.savefig("figures/shift_profile.pdf", bbox_inches="tight", facecolor="white")
fig.savefig("figures/shift_profile.png", dpi=600, bbox_inches="tight", facecolor="white")
print("wrote figures/shift_profile.{pdf,png}")
