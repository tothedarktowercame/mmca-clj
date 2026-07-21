"""Figure 2, two terminal-diversity solutions of the same historical operator
under the Wolfram order. The draft3 conjugate [1 2 4 5 3 6 7 7] collapses to two
terminal genotypes (rules 76, 77); the draft4 form [0 0 1 2 3 4 5 6] stays diverse
(~30). Reads data/fig2pair_{draft3,draft4}.txt; writes figures/fig2pair.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
from matplotlib.patches import Patch
T76, T77 = (31, 119, 180), (255, 127, 14)  # tint the two draft3-terminal rules distinctly
def load(name):
    raw = open(f"data/fig2pair_{name}.txt").read().splitlines()
    gi = raw.index("GEN") + 1; pi = raw.index("PHE")
    gen = [r.split() for r in raw[gi:pi] if r.strip()]
    phe = [r for r in raw[pi + 1:] if r.strip()]
    return gen, phe
hx = lambda h: [int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]
def gimg(gen):
    G = np.array([[hx(x) for x in row] for row in gen], dtype=np.uint8)
    for r, c in {76: T76, 77: T77}.items():  # rules 76,77 are grayscale (0x4c,0x4d); recolor
        G[(G[:, :, 0] == r) & (G[:, :, 1] == r) & (G[:, :, 2] == r)] = c
    return G
pimg = lambda phe: np.array([[0 if ch == '1' else 255 for ch in row] for row in phe], dtype=np.uint8)
fig = plt.figure(figsize=(8.5, 6.4))
gs = fig.add_gridspec(2, 2, wspace=0.08, hspace=0.26, left=0.14, right=0.98, top=0.86, bottom=0.03)
rows = [("draft3", r"$[1\,2\,4\,5\,3\,6\,7\,7]$ (conjugate)", "#a11111"),
        ("draft4", r"$[0\,0\,1\,2\,3\,4\,5\,6]$ (Wolfram-direct)", "#1b5e20")]
for ri, (name, op, col) in enumerate(rows):
    gen, phe = load(name)
    term = len(set(tuple(gen[-1])))
    axg = fig.add_subplot(gs[ri, 0]); axg.imshow(gimg(gen), aspect="auto", interpolation="nearest")
    axp = fig.add_subplot(gs[ri, 1]); axp.imshow(pimg(phe), aspect="auto", cmap="gray", interpolation="nearest")
    for a in (axg, axp): a.set_xticks([]); a.set_yticks([])
    if ri == 0:
        axg.set_title("genotype", fontsize=10); axp.set_title("phenotype", fontsize=10)
        axg.legend(handles=[Patch(color=np.array(T76) / 255, label="rule 76"),
                            Patch(color=np.array(T77) / 255, label="rule 77")],
                   loc="lower center", ncol=2, fontsize=7, framealpha=0.9)
    fig.text(0.035, 0.64 - 0.43 * ri, f"{name}\n{term} terminal\ngenotypes",
             rotation=90, va="center", ha="center", fontsize=11, fontweight="bold", color=col)
    axg.set_xlabel(op, fontsize=9, color=col)
fig.suptitle("Figure 2 — two terminal-diversity solutions of the same operator under the Wolfram order:\n"
             "the draft3 conjugate dies to two genotypes; the draft4 form stays diverse",
             fontsize=10.5, y=0.965)
fig.savefig("figures/fig2pair.png", dpi=120, bbox_inches="tight", facecolor="white")
print("wrote figures/fig2pair.png")
