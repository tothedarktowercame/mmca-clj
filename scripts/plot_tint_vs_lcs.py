"""Side-by-side: tint-threshold walls vs LCS coherent structures (offset+1, seed 0).

Reads data/tint_vs_lcs_compare.npz (written by tint_vs_lcs_compare.py).
Writes figures/tint_vs_lcs.{png,pdf}.
"""
import numpy as np, matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap

SEED = 0
d = np.load("data/tint_vs_lcs_compare.npz")
tint, lcs, te = d[f"tint_s{SEED}"], d[f"lcs_s{SEED}"], d[f"te_s{SEED}"]

fig, ax = plt.subplots(1, 4, figsize=(13.5, 4.6), constrained_layout=True)

ax[0].imshow(te, cmap="magma", aspect="auto", interpolation="nearest")
ax[0].set_title("(a) local transfer entropy", fontsize=10)
ax[0].set_ylabel("time $\\rightarrow$")

ax[1].imshow(tint, cmap=ListedColormap(["#f7f7f7", "#1a1a1a"]),
             aspect="auto", interpolation="nearest")
ax[1].set_title(f"(b) tint wall  (density {tint.mean():.2f})", fontsize=10)

ax[2].imshow(lcs, cmap=ListedColormap(["#f7f7f7", "#2166ac"]),
             aspect="auto", interpolation="nearest")
ax[2].set_title(f"(c) LCS coherent structures  ({lcs.mean():.2f})", fontsize=10)

# disagreement: both / tint-only / lcs-only / neither
cat = np.zeros_like(tint, dtype=int)
cat[tint & lcs] = 3
cat[tint & ~lcs] = 1
cat[~tint & lcs] = 2
ax[3].imshow(cat, cmap=ListedColormap(["#f7f7f7", "#1a1a1a", "#2166ac", "#d6604d"]),
             vmin=0, vmax=3, aspect="auto", interpolation="nearest")
inter = (tint & lcs).sum() / (tint | lcs).sum()
ax[3].set_title(f"(d) disagreement  (Jaccard {inter:.2f})", fontsize=10)

for a in ax:
    a.set_xticks([]); a.set_yticks([])
for a in ax[1:]:
    a.set_xlabel("space $\\rightarrow$", fontsize=9)

fig.suptitle("Two segmentations of the same offset$+1$ field disagree at chance level "
             "(black = tint only, blue = LCS only, red = both)", fontsize=11)
for ext in ("png", "pdf"):
    fig.savefig(f"figures/tint_vs_lcs.{ext}", dpi=160)
print("wrote figures/tint_vs_lcs.{png,pdf}")
