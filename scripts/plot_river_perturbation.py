"""Figure: causal perturbation of the river vs its matched feedback-off control.

CA space-time panels use aspect="equal" + interpolation="none": with aspect="auto"
matplotlib stretches the 80x121 lattice non-uniformly into the subplot and the
one-cell-wide vertical structures alias into spurious diagonals in the PDF.
That is the repo convention for every CA panel (cf. plot_fig6, plot_river_grid).
"""
import numpy as np, collections, matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap

P = np.array([[int(c) for c in l] for l in open('/tmp/pert_phe.txt').read().split('\n') if l.strip()])
rows = [l.split('\t') for l in open('/tmp/pert_summary.tsv').read().strip().split('\n')[1:]]
D = collections.defaultdict(dict); mass = {}
for mode, site, dt, m, s in rows:
    D[(mode, int(dt))].setdefault('m', []).append(int(m))
    D[(mode, int(dt))].setdefault('s', []).append(int(s))
    if mode == 'river' and int(dt) == 59: mass[int(site)] = int(m)
site = np.array([mass[i] for i in range(80)]); T0 = 60

fig = plt.figure(figsize=(13, 9.2))
gs = fig.add_gridspec(2, 3, hspace=0.30, wspace=0.28, height_ratios=[1.25, 1])
for k, x in enumerate((20, 40, 60)):
    G = np.array([[int(v) for v in l.split()] for l in
                  open(f'/tmp/pert_grid_{x}.txt').read().split('\n') if l.strip()])
    ax = fig.add_subplot(gs[0, k])
    ax.imshow(P, cmap="Greys", aspect="equal", interpolation="none", alpha=0.35)
    ax.imshow(np.ma.masked_where(G == 0, G), cmap=ListedColormap(["#d40000"]),
              aspect="equal", interpolation="none")
    ax.axhline(T0, color="#0066cc", lw=1.0, ls="--")
    ax.plot([x], [T0], marker="v", color="#0066cc", ms=7, clip_on=False)
    ax.set_title(f"flip at site {x}  ({mass[x]} cells damaged by $dt$=59)", fontsize=9)
    ax.set_xlabel("space", fontsize=8); ax.set_ylabel("time →" if k == 0 else "", fontsize=8)
    ax.tick_params(labelsize=7)

dts = [1, 5, 10, 20, 40, 59]
ax = fig.add_subplot(gs[1, 0])
ax.plot(dts, [np.mean(D[('river', d)]['m']) for d in dts], "o-", color="#0066cc", label="river (X→G live)")
ax.plot(dts, [np.mean(D[('ablated', d)]['m']) for d in dts], "s--", color="#888", label="ablated (X→G cut)")
ax.set_xlabel("$dt$ after perturbation"); ax.set_ylabel("phenotype cells differing")
ax.legend(fontsize=8); ax.set_title("(a) damage mass vs matched control", fontsize=10); ax.grid(alpha=.3)

ax = fig.add_subplot(gs[1, 1])
ax.plot(dts, [np.mean(D[('river', d)]['s']) for d in dts], "o-", color="#0066cc", label="river")
ax.plot(dts, [np.mean(D[('ablated', d)]['s']) for d in dts], "s--", color="#888", label="ablated")
ax.plot(dts, dts, ":", color="k", lw=.9, label="light-cone bound")
ax.set_xlabel("$dt$"); ax.set_ylabel("max circular distance")
ax.legend(fontsize=8); ax.set_title("(b) spatial spread", fontsize=10); ax.grid(alpha=.3)

ax = fig.add_subplot(gs[1, 2])
ax.bar(np.arange(80), site, color=np.where(site > 0, "#d40000", "#bbbbbb"), width=.9)
ax.set_xlabel("perturbation site"); ax.set_ylabel("damage at $dt$=59")
ax.set_title(f"(c) {int((site == 0).sum())}/80 sites are dead ends", fontsize=10)

fig.suptitle("A single phenotype-bit flip spreads farther in the river than in the feedback-cut control\n"
             "(same seed, same RNG tape, same construction; single-seed sweep over all 80 sites, $L$=80)",
             fontsize=11.5)
for ext in ("png", "pdf"):
    fig.savefig(f"figures/river_perturbation.{ext}", dpi=150, bbox_inches="tight")
print("wrote figures/river_perturbation.{png,pdf}")
