"""Two operators of identical cycle type (two 4-cycles) with opposite fate:
cycle structure does not determine aliveness. The sustaining one is the
Wolfram-order conjugate of draft3's offset+2 edge-of-chaos exemplar.
Reads data/two4cycle_{sustain,collapse}.txt; writes figures/two4cycle.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def load(name):
    raw = open(f"data/two4cycle_{name}.txt").read().splitlines()
    gi = raw.index("GEN") + 1; pi = raw.index("PHE")
    gen = [r.split() for r in raw[gi:pi] if r.strip()]
    phe = [r for r in raw[pi + 1:] if r.strip()]
    return gen, phe
hx = lambda h: [int(h.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4)]
fig = plt.figure(figsize=(9, 5.6))
gs = fig.add_gridspec(1, 4, wspace=0.08, left=0.03, right=0.98, top=0.82, bottom=0.03)
specs = [("sustain",  r"$[6\,7\,0\,2\,1\,4\,3\,5]$",         "sustains  (live / live)",   "#1b5e20"),
         ("collapse", r"$[2\,3\,4\,5\,6\,7\,0\,1]$ = offset $+2$", "collapses  (dead / dead)", "#a11111")]
for c, (name, label, verdict, col) in enumerate(specs):
    gen, phe = load(name)
    G = np.array([[hx(x) for x in row] for row in gen], dtype=np.uint8)
    P = np.array([[0 if ch == '1' else 255 for ch in row] for row in phe], dtype=np.uint8)
    axg = fig.add_subplot(gs[0, 2 * c]); axg.imshow(G, aspect="auto", interpolation="nearest")
    axp = fig.add_subplot(gs[0, 2 * c + 1]); axp.imshow(P, aspect="auto", cmap="gray", interpolation="nearest")
    for a in (axg, axp): a.set_xticks([]); a.set_yticks([])
    axg.set_title("genotype", fontsize=10); axp.set_title("phenotype", fontsize=10)
    fig.text(0.03 + 0.485 * c, 0.90, f"{label}  —  two 4-cycles", fontsize=11, fontweight="bold", color=col)
    fig.text(0.03 + 0.485 * c, 0.865, verdict, fontsize=10, color=col)
fig.suptitle("Same cycle type (two 4-cycles), opposite fate: cycle structure does not determine aliveness",
             fontsize=12, y=0.98)
fig.savefig("figures/two4cycle.png", dpi=120, bbox_inches="tight", facecolor="white")
print("wrote figures/two4cycle.png")
