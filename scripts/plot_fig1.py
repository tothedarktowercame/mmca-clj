"""Figure 1 — two operators, genotype (rule colour) + phenotype. Representative
seed. Reads data/fig1_16250374.txt, data/fig1_10275364.txt."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
N=90
def load(name):
    raw=open(f"data/fig1_{name}.txt").read().splitlines()
    i=raw.index("GEN")+1; gen=[]
    while raw[i]!="PHE" and raw[i]!="": gen.append(raw[i].split()); i+=1
    phe=[r for r in raw[raw.index("PHE")+1:] if r.strip()]
    return gen, phe   # full field; caller truncates for display, measures on full
hx=lambda h:[int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
def frac110(gen):    # over the full genotype field (all T rows), matching the paper
    c=[x for r in gen for x in r]; return 100*sum(1 for x in c if x=="#ff3300")/len(c)
fig=plt.figure(figsize=(13,6.5)); gs=fig.add_gridspec(1,5,width_ratios=[1,1,0.25,1,1],wspace=0.08)
fig.subplots_adjust(bottom=0.11, top=0.84)
panel_notes=[]
for k,(name,sub) in enumerate([("16250374","Rule 110 predominates"),("10275364","a different regime — green/blue rule-domains")]):
    gen,phe=load(name); pct=frac110(gen)
    G=np.array([[hx(c) for c in r] for r in gen[:N]],dtype=np.uint8)
    P=np.array([[0 if ch=='1' else 255 for ch in r] for r in phe[:N]],dtype=np.uint8)
    col=0 if k==0 else 3
    ag=fig.add_subplot(gs[col]); ag.imshow(G,aspect="auto",interpolation="nearest"); ag.set_xticks([]); ag.set_yticks([])
    ap=fig.add_subplot(gs[col+1]); ap.imshow(P,aspect="auto",cmap="gray",interpolation="nearest"); ap.set_xticks([]); ap.set_yticks([])
    note=(f"Rule 110 = {pct:.0f}% of genotype cells (red); seed 0" if k==0
          else f"< 2% Rule 110; seed 0")
    ag.set_title(f"$\\sigma$ = {name}\n{sub}", fontsize=13, loc="left", fontweight="bold")
    panel_notes.append((ag, ap, note))
fig.canvas.draw()
for ag, ap, note in panel_notes:
    left=ag.get_position(); right=ap.get_position()
    fig.text((left.x0+right.x1)/2, min(left.y0,right.y0)-0.025, note,
             ha="center", va="top", fontsize=8.5, color="#555555")
fig.savefig("figures/rule110_spread.png",dpi=600,bbox_inches="tight",facecolor="white")
fig.savefig("figures/rule110_spread.pdf",bbox_inches="tight",facecolor="white")
print("wrote figures/rule110_spread.png and figures/rule110_spread.pdf")
