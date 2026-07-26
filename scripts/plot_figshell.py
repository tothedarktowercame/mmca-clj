"""Critical-shell figure: a genotypically-dead but phenotypically-alive CA."""
import numpy as np, matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
raw=open("data/figshell.txt").read().splitlines()
gi=raw.index("GEN")+1; pi=raw.index("PHE")
gen=[r.split() for r in raw[gi:pi] if r.strip()]
phe=[r for r in raw[pi+1:] if r.strip()]
hx=lambda h:[int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
G=np.array([[hx(c) for c in r] for r in gen],dtype=np.uint8)
P=np.array([[0 if ch=='1' else 255 for ch in r] for r in phe],dtype=np.uint8)
fig,ax=plt.subplots(1,2,figsize=(8,5.6),gridspec_kw=dict(wspace=0.05))
ax[0].imshow(G,aspect="auto",interpolation="nearest"); ax[0].set_xticks([]); ax[0].set_yticks([])
ax[1].imshow(P,aspect="auto",cmap="gray",interpolation="nearest"); ax[1].set_xticks([]); ax[1].set_yticks([])
ax[0].set_title("genotype — collapses to Rule 105 (dead)",fontsize=11)
ax[1].set_title("phenotype — stays complex (alive)",fontsize=11)
fig.savefig("figures/critical_shell.png",dpi=600,bbox_inches="tight",facecolor="white")
fig.savefig("figures/critical_shell.pdf",bbox_inches="tight",facecolor="white")
print("wrote figures/critical_shell.png and figures/critical_shell.pdf")
