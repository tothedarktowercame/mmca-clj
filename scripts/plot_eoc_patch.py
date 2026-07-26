"""A zoomed space-time patch from the synthetic family (offset+1), with the
activity-domain walls highlighted. Marks a real collision---a junction where
long wall arms meet---and a real wall run. Writes high-resolution PNG and PDF
files."""
import numpy as np
from scipy.ndimage import uniform_filter, convolve, binary_erosion, binary_dilation, label
import matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap

_r = np.loadtxt("data/rule_activity_scores.txt")
scores = np.zeros(256); scores[_r[:, 0].astype(int)] = _r[:, 1]
field = np.loadtxt("data/eoc_sweep_order_q1000_s0.txt", dtype=int)   # offset+1 (edge operator)
smooth = uniform_filter(scores[field], size=5)
active = (smooth > 0.35).astype(int)
ab = active.astype(bool)
bnd = (binary_dilation(ab) & ~binary_erosion(ab))                   # thin ~1px domain wall
H, W = bnd.shape

# A REAL collision (time runs DOWNWARD): two walls that converge as time increases
# = the BOTTOM tip of an island (a domain closing to a point / annihilating). Not
# the top of an island (nucleation, walls diverge) and not an elbow (one wall bends).
h = w = 46; m = 24
best = None; bestscore = -1
for domain in (~ab, ab):                                     # white islands and grey islands
    lab, n = label(domain, structure=np.ones((3, 3)))
    for i in range(1, n+1):
        ys, xs_ = np.where(lab == i)
        if len(ys) < 25:
            continue
        rt, rb = ys.min(), ys.max(); ht = rb - rt
        if ht < 10 or ht > 34 or rt < 6 or rb > H-6:          # tall enough, not cut off
            continue
        widths = np.array([(ys == r).sum() for r in range(rt, rb+1)])
        wb = widths[-3:].max()                                # width near the bottom tip
        wtop = widths[:2*len(widths)//3].max()                # widest part above
        if wb <= 4 and wtop >= wb + 6:                        # narrows to a point going DOWN
            tipx = int(round(xs_[ys >= rb-1].mean())); tipy = rb
            if not (m < tipx < W-m):
                continue
            below = bnd[rb+2:rb+12, max(0, tipx-9):tipx+10]    # sea just below the tip
            below_dens = below.mean() if below.size else 1.0
            score = (wtop - wb) + 0.4*ht - 34*below_dens       # sharp tall wedge, open sea below
            if score > bestscore:
                bestscore, best, best_mask = score, (tipy, tipx), (lab == i)
tipy, tipx = best
t0 = min(max(tipy - int(0.62*h), 0), H-h)                     # island body above, tip in lower-middle
x0 = min(max(tipx - w//2, 0), W-w)
A = active[t0:t0+h, x0:x0+w]; B = bnd[t0:t0+h, x0:x0+w]
cy, cx = tipy - t0, tipx - x0                                 # collision in window coords
jt, jx = tipy, tipx

fig, ax = plt.subplots(figsize=(3.0, 3.0))
ax.imshow(A, cmap=ListedColormap(["white", "#d9d9d9"]), interpolation="nearest", aspect="equal")
wall = np.ma.masked_where(~B, np.ones_like(B))
ax.imshow(wall, cmap=ListedColormap(["#2a9d8f"]), interpolation="nearest", aspect="equal", alpha=0.9)
# mark the real collision (bottom of an island: two walls converge going down)
# trace the island's two walls a few rows above the tip and draw them converging in
r_up = jt - 9
row_cols = np.where(best_mask[r_up, x0:x0+w])[0] if 0 <= r_up-t0 < h else []
if len(row_cols) >= 2:
    yl = r_up - t0
    for xw, rad in ((row_cols.min(), 0.18), (row_cols.max(), -0.18)):
        ax.annotate("", xy=(cx, cy-1.2), xytext=(xw, yl),
                    arrowprops=dict(arrowstyle="-|>", color="#c1440e", lw=1.5, alpha=0.95,
                                    connectionstyle=f"arc3,rad={rad}"))
ax.add_patch(plt.Circle((cx, cy), 2.6, fill=False, ec="#c1440e", lw=1.3))
lx, ly = (cx+10 if cx < w-13 else cx-10), min(cy+10, h-3)
ax.annotate("two walls\ncollide", (cx, cy), xytext=(lx, ly), fontsize=7.5, color="#c1440e",
            ha="center", va="center", arrowprops=dict(arrowstyle="->", color="#c1440e", lw=0.8))
# a clear wall run well clear of the collision
bestrun = 0; wcol = wrow = None
for c in range(w):
    if abs(c - cx) <= 12: continue
    run = 0
    for r in range(h):
        if B[r, c]:
            run += 1
            if run > bestrun: bestrun, wcol, wrow = run, c, r-run//2
        else:
            run = 0
if wcol is not None:
    ly = wrow-11 if wrow > 14 else wrow+11
    ax.annotate("wall", (wcol, wrow), xytext=(wcol, ly), ha="center", va="center",
                fontsize=8, color="#2a7d70", arrowprops=dict(arrowstyle="->", color="#2a7d70", lw=0.9))
ax.set_xticks([]); ax.set_yticks([])
ax.set_xlabel("space $\\rightarrow$", fontsize=8)
# unmistakable downward time arrow on the left
ax.annotate("", xy=(-0.05, 0.04), xytext=(-0.05, 0.96), xycoords="axes fraction",
            arrowprops=dict(arrowstyle="-|>", color="0.35", lw=1.5))
ax.text(-0.12, 0.5, "time", rotation=90, va="center", ha="center",
        transform=ax.transAxes, fontsize=8.5, color="0.35")
fig.savefig("figures/eoc_patch.png", dpi=600, bbox_inches="tight", facecolor="white")
fig.savefig("figures/eoc_patch.pdf", bbox_inches="tight", facecolor="white")
print(f"collision (island bottom) at t={jt} x={jx}; window t0={t0} x0={x0}; score={bestscore:.1f}")
print("wrote figures/eoc_patch.png and figures/eoc_patch.pdf")
