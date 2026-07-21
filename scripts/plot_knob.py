"""Complexity knob: sustained diversity vs the fraction of steps spent in the
mixing operator sigma^1 (8-cycle), braided with the dead sigma^2 (two 4-cycles).
Reads data/knob_curve.txt + data/knob_{low,mid,high}.txt; writes figures/knob.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def loadgen(name):
    raw = open(f"data/knob_{name}.txt").read().splitlines()
    gi = raw.index("GEN") + 1; pi = raw.index("PHE")
    return [r.split() for r in raw[gi:pi] if r.strip()]
hx = lambda h: [int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]
gimg = lambda gen: np.array([[hx(x) for x in row] for row in gen], dtype=np.uint8)
cur = np.array([[float(x) for x in l.split()] for l in
                open("data/knob_curve.txt").read().splitlines() if l.strip()])
fig = plt.figure(figsize=(9, 6.6))
gs = fig.add_gridspec(2, 3, height_ratios=[1.2, 1], hspace=0.30, wspace=0.08,
                      left=0.10, right=0.98, top=0.91, bottom=0.04)
ax = fig.add_subplot(gs[0, :])
ax.plot(cur[:, 0], cur[:, 1], "-o", color="#1b5e20", lw=2, zorder=2)
ax.set_xlabel(r"fraction of steps in the mixing operator $\sigma^1$ (8-cycle)")
ax.set_ylabel("sustained diversity\n(distinct rules)")
ax.set_title(r"A complexity knob: braiding dead $\sigma^2$ with mixing $\sigma^1$ dials the sustained complexity",
             fontsize=11)
ax.grid(alpha=0.25)
ax.set_ylim(0, float(cur[:, 1].max()) * 1.15)  # headroom so the "high" label clears the title
marks = {"low": 0.25, "mid": 0.5, "high": 1.0}
for name, fr in marks.items():
    yi = float(np.interp(fr, cur[:, 0], cur[:, 1]))
    ax.plot([fr], [yi], "o", color="#a11111", zorder=3)
    ax.annotate(name, (fr, yi), textcoords="offset points", xytext=(0, 9),
                ha="center", fontsize=9, color="#a11111", fontweight="bold")
for ci, (name, fr) in enumerate(marks.items()):
    axp = fig.add_subplot(gs[1, ci])
    axp.imshow(gimg(loadgen(name)), aspect="auto", interpolation="nearest")
    axp.set_xticks([]); axp.set_yticks([])
    axp.set_title(f"{name}: $\\sigma^1$ fraction {fr:g}", fontsize=9.5, color="#a11111")
fig.savefig("figures/knob.png", dpi=120, bbox_inches="tight", facecolor="white")
print("wrote figures/knob.png")
