"""Figure: causal reach against architecture, on the elementary-rule scale.

Rows are not "our systems vs theirs" but a statement about coupling. Every
construction whose genotype update never reads the phenotype is feedforward: a
phenotype perturbation cannot reach the rule field, and none of them clears rule 90.
The shaded intervals are named for the reference rules that bound them, NOT for
Wolfram classes: rule 90 is class III and sits at 8.00, so this axis does not order
by class and no regime is asserted by a position on it. Every construction whose genotype update does read
the phenotype -- the river, and the conservative phenotype-gated transport --
lands well inside it. One protocol throughout: L=80, t*=60, single phenotype
flip, damaged phenotype cells at dt=59.
"""
import matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

def field_rgb(tag, t0=40, t1=119):
    """Composite RGB: white bg, phenotype grey, damage red (repo convention)."""
    phe = [l.strip() for l in open(f"data/inset_{tag}_phe.txt")]
    dmg = [l.split() for l in open(f"data/inset_{tag}_dmg.txt")]
    H = t1 - t0 + 1; Wd = len(phe[0])
    img = np.ones((H, Wd, 3))
    for r in range(H):
        t = t0 + r
        row = phe[t]
        for x in range(Wd):
            if row[x] == "1":
                img[r, x] = (0.80, 0.80, 0.80)
        di = t if len(dmg) > 60 else t - 60
        if 0 <= di < len(dmg):
            for x in range(Wd):
                if dmg[di][x] == "1":
                    img[r, x] = (0.77, 0.15, 0.11)
    return np.kron(img, np.ones((4, 4, 1)))

ECA = [("rule 0", 0.00), ("rule 204", 1.00), ("rule 90", 8.00),
       ("rule 110", 16.68), ("rule 54", 18.30), ("rule 30", 36.45)]
FF = [("$P_a$ (bare)", 0.03), ("blend $1.00$", 0.03), ("braid $P_a$/two-4", 0.12),
      ("mutation $0.10$", 0.23), ("braid+blend $0.70$", 0.38), ("async $0.25$", 0.47),
      ("niches (8)", 0.50), ("blend $0.70$", 0.55), ("niches (16)", 0.72),
      ("async $0.75$", 0.75), ("mutation $0.40$", 0.85), ("preserving limit", 1.10),
      ("rot$+1$", 2.05), ("blend $0.35$", 3.38), ("braid rot$+2$/rot$+4$", 5.20),
      ("river, edge cut", 5.51), ("blend $0.00$", 8.15)]
FB = [("river construction", 12.97), ("transport $0.50$", 19.27), ("transport $1.00$", 25.38)]

fig = plt.figure(figsize=(15.6, 3.9))
gs = fig.add_gridspec(2, 2, width_ratios=[10.4, 1.9], height_ratios=[1, 1],
                      wspace=0.04, hspace=0.16)
ax = fig.add_subplot(gs[:, 0])
ax_live = fig.add_subplot(gs[0, 1]); ax_cut = fig.add_subplot(gs[1, 1])
for a, tag, ttl in ((ax_live, "river", "river, live edge --- reach 12.97"),
                    (ax_cut, "ablated", "edge cut --- reach 5.51")):
    a.imshow(field_rgb(tag), aspect="equal", interpolation="nearest")
    a.set_xticks([]); a.set_yticks([])
    a.set_title(ttl, fontsize=7.6, color="#c44e52", pad=2.5)
    for sp in a.spines.values(): sp.set_color("#c44e52"); sp.set_linewidth(1.2)
ax_cut.set_xlabel("one flip at $t^*$, damage in red", fontsize=7.2, color="#666")
ax.axvspan(0.02, 8.0, color="#dce8f5", alpha=.75, zorder=0)
ax.axvspan(8.0, 22.0, color="#e6f2e0", alpha=.85, zorder=0)
ax.axvspan(22.0, 60, color="#f9e0dc", alpha=.75, zorder=0)
for x, lab, col in ((0.7, "below rule 90", "#31567d"), (13.0, "rules 90-54", "#3d6b2c"), (36, "above rule 54", "#8d3a2f")):
    ax.text(x, 3.62, lab, ha="center", fontsize=10.5, style="italic", color=col)

DY = {"rule 110": 25, "rule 54": 12}
for n, v in ECA:
    ax.scatter([max(v, .025)], [3.0], marker="|", s=520, c="k", lw=1.8, zorder=4)
    ax.annotate(n, (max(v, .025), 3.0), textcoords="offset points",
                xytext=(0, DY.get(n, 12)), ha="center", fontsize=8.6)
for n, v in FF:
    hl = n in ("blend $0.00$", "river, edge cut")
    ax.scatter([max(v, .025)], [2.0], marker="o", s=64 if hl else 42,
               c="#c44e52" if n.startswith("river") else "#4c72b0",
               edgecolor="white", lw=.7, zorder=4)
for n, dy in (("$P_a$ (bare)", -20), ("blend $0.35$", -20), ("blend $0.00$", 13), ("river, edge cut", -20)):
    v = dict(FF)[n]
    ax.annotate(n, (v, 2.0), textcoords="offset points", xytext=(0, dy), ha="center",
                fontsize=7.8, color="#c44e52" if n.startswith("river") else "#31567d")
FB_DY = {"river construction": -22, "transport $0.50$": -37, "transport $1.00$": -22}
for n, v in FB:
    ax.scatter([v], [1.0], marker="*", s=340, c="#c44e52", edgecolor="white", lw=.8, zorder=5)
    ax.annotate(n, (v, 1.0), textcoords="offset points", xytext=(0, FB_DY[n]),
                ha="center", fontsize=8.4, color="#c44e52", weight="bold")
ax.annotate("", xy=(12.97, 1.42), xytext=(8.15, 1.82),
            arrowprops=dict(arrowstyle="->", color="#8d3a2f", lw=1.5))
ax.text(10.3, 1.80, "adding the edge", fontsize=8.2, color="#8d3a2f",
        style="italic", ha="center", rotation=-14)

ax.set_xscale("symlog", linthresh=1.0, linscale=0.7)
ax.set_xlim(0.02, 60); ax.set_ylim(0.30, 3.95)
ax.set_yticks([1.0, 2.0, 3.0])
ax.set_yticklabels(["genotype reads $X$\n(feedback)", "genotype never reads $X$\n(feedforward)",
                    "elementary rules"], fontsize=9)
ax.set_xlabel("causal reach: mean damaged phenotype cells at $dt=59$   ($L=80$, $t^{*}=60$, single flip)")
ax.set_xticks([0.1, 0.3, 1, 3, 10, 30]); ax.set_xticklabels(["0.1", "0.3", "1", "3", "10", "30"])
ax.grid(axis="x", alpha=.3); ax.set_axisbelow(True)
for sp in ("top", "right", "left"): ax.spines[sp].set_visible(False)
fig.suptitle("What moves causal reach is the feedback edge, not any parameter tested", fontsize=11.5)
for ext in ("png", "pdf"):
    fig.savefig(f"figures/regime_placement.{ext}", dpi=150, bbox_inches="tight")
print("wrote figures/regime_placement.{png,pdf}")
