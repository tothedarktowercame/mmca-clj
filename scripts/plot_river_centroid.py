"""Figure: damage width and centroid displacement for the river vs its control.

Panel (c) uses aspect="equal" + interpolation="none" for the same reason as
plot_river_perturbation.py: aspect="auto" stretches the lattice non-uniformly
and flattens the CA's steep propagating structures into shallow diagonals.
"""
import numpy as np, collections, matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap

W, T0 = 80, 60
acc = collections.defaultdict(lambda: collections.defaultdict(list))
cone = {}
for line in open('data/pert_rows.tsv'):
    seed, mode, x, dt, row = line.rstrip('\n').split('\t')
    a = np.frombuffer(row.encode(), dtype=np.uint8) - 48
    i = np.nonzero(a)[0]
    # Width/centroid are conditional on damage existing: rows with no damage
    # contribute no width, and are excluded rather than entered as zero.
    if i.size:
        off = ((i - int(x) + W // 2) % W) - W // 2
        acc[mode][int(dt)].append((np.sqrt((off ** 2).mean()), abs(off.mean())))
    cone.setdefault((seed, x), {})[int(dt)] = a
dts = np.array(sorted(acc['river']))
def curve(mode, k): return np.array([np.mean([v[k] for v in acc[mode][d]]) for d in dts])
def fit(y, lo=5):
    m = (dts >= lo) & (y > 0)
    return np.polyfit(np.log(dts[m]), np.log(y[m]), 1)[0]

fig = plt.figure(figsize=(13.5, 4.4))
gs = fig.add_gridspec(1, 3, wspace=0.30, width_ratios=[1, 1, 0.62])

ax = fig.add_subplot(gs[0, 0])
for mode, c, lab in (('river', "#0066cc", "river (X→G live)"), ('ablated', "#888", "ablated (X→G cut)")):
    y = curve(mode, 0); ax.loglog(dts, y, "o-" if mode == 'river' else "s--", color=c, ms=3,
                                  label=f"{lab}: $dt^{{{fit(y):.2f}}}$")
ax.loglog(dts, 0.9 * dts, ":", color="k", lw=.9, label="ballistic $dt^{1}$")
ax.loglog(dts, 1.6 * np.sqrt(dts), "-.", color="#c00", lw=.9, label="diffusive $dt^{0.5}$")
ax.set_xlabel("$dt$"); ax.set_ylabel("RMS damage width"); ax.legend(fontsize=7.5)
ax.set_title("(a) width grows", fontsize=10); ax.grid(alpha=.3, which="both")

ax = fig.add_subplot(gs[0, 1])
for mode, c in (('river', "#0066cc"), ('ablated', "#888")):
    y = curve(mode, 1); ax.plot(dts, y, "-" if mode == 'river' else "--", color=c,
                                label=f"{mode}: $dt^{{{fit(y):.2f}}}$")
ax.plot(dts, 0.45 * dts, ":", color="k", lw=.9, label="constant velocity")
ax.set_xlabel("$dt$"); ax.set_ylabel("|centroid displacement|"); ax.legend(fontsize=7.5)
ax.set_title("(b) centroid wanders, does not translate", fontsize=10); ax.grid(alpha=.3)

ax = fig.add_subplot(gs[0, 2])
# show a site that actually propagates, not a dead end
key = max(cone, key=lambda k: cone[k][59].sum())
track = cone[key]
G = np.zeros((max(track) + 1, W))
for d, a in track.items(): G[d] = a
ax.imshow(np.ma.masked_where(G == 0, G), cmap=ListedColormap(["#d40000"]),
          aspect="equal", interpolation="none")
x0 = int(key[1])
cen = [(d, ((np.nonzero(a)[0] - x0 + W // 2) % W - W // 2).mean() + x0)
       for d, a in sorted(track.items()) if a.any()]
ax.plot([c for _, c in cen], [d for d, _ in cen], "-", color="#0066cc", lw=1.1)
ax.plot([x0], [0], marker="v", color="#0066cc", ms=7, clip_on=False)
ax.set_xlabel("space"); ax.set_ylabel("$dt$"); ax.tick_params(labelsize=7)
ax.set_title(f"(c) cone at site {x0}, centroid track", fontsize=10)

fig.suptitle("Damage width and centroid displacement (4 seeds × 10 sites, $L$=80, $t^*$=60)", fontsize=11)
for ext in ("png", "pdf"):
    fig.savefig(f"figures/river_centroid.{ext}", dpi=150, bbox_inches="tight")
print("wrote figures/river_centroid.{png,pdf}")
