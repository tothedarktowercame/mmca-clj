"""Braiding two operators that each collapse alone. Offset+2 x offset+4
revives; offset+2 x offset-2 stays dead. Both pairs preserve parity, so the
contrast is not explained by a common-block criterion alone. Reads
data/braid_*.txt; writes figures/braid.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def load(name):
    raw = open(f"data/braid_{name}.txt").read().splitlines()
    gi = raw.index("GEN") + 1; pi = raw.index("PHE")
    gen = [r.split() for r in raw[gi:pi] if r.strip()]
    phe = [r for r in raw[pi + 1:] if r.strip()]
    return gen, phe
hx = lambda h: [int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]
gimg = lambda gen: np.array([[hx(x) for x in row] for row in gen], dtype=np.uint8)
pimg = lambda phe: np.array([[0 if ch == '1' else 255 for ch in row] for row in phe], dtype=np.uint8)
fig = plt.figure(figsize=(9.5, 5.8))
gs = fig.add_gridspec(2, 4, wspace=0.08, hspace=0.28, left=0.10, right=0.98, top=0.84, bottom=0.03)
rows = [("$+2/+4$\n→ ALIVE", "#1b5e20", "off2", "off4", "complementary", "offset $+2$", "offset $+4$"),
        ("$+2/-2$\n→ DEAD",  "#a11111", "off2", "offm2", "samefamily",   "offset $+2$", "offset $-2$")]
col_titles = ["operator A (dead alone)", "operator B (dead alone)", "braid: genotype", "braid: phenotype"]
for ri, (rlabel, col, a, b, braid, la, lb) in enumerate(rows):
    ga, _ = load(a); gb, _ = load(b); gbr, pbr = load(braid)
    panels = [gimg(ga), gimg(gb), gimg(gbr), pimg(pbr)]
    for ci in range(4):
        ax = fig.add_subplot(gs[ri, ci])
        ax.imshow(panels[ci], aspect="auto", cmap=("gray" if ci == 3 else None), interpolation="nearest")
        ax.set_xticks([]); ax.set_yticks([])
        if ri == 0: ax.set_title(col_titles[ci], fontsize=9.5)
        if ci == 0: ax.set_xlabel(la, fontsize=9, color=col)
        if ci == 1: ax.set_xlabel(lb, fontsize=9, color=col)
    fig.text(0.028, 0.63 - 0.42 * ri, rlabel, rotation=90, va="center", ha="center",
             fontsize=12, fontweight="bold", color=col)
fig.suptitle("Temporal braid contrast: offset $+2/+4$ revives; offset $+2/-2$ remains dead\n"
             "both pairs preserve parity, so common-block invariance does not explain the split",
             fontsize=11, y=0.965)
fig.savefig("figures/braid.png", dpi=120, bbox_inches="tight", facecolor="white")
print("wrote figures/braid.png")
