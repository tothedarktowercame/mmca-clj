import numpy as np, matplotlib; matplotlib.use("Agg"); import matplotlib.pyplot as plt
def load(s):
    raw=open(f"data/fig6_s{s}.txt").read().splitlines()
    gi=raw.index("GEN")+1; pi=raw.index("PHE")
    gen=[r.split() for r in raw[gi:pi] if r.strip()]
    phe=[r for r in raw[pi+1:] if r.strip()]
    return gen,phe
hx=lambda h:[int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
fig=plt.figure(figsize=(15,7.4))
gs=fig.add_gridspec(2,6,height_ratios=[1,1],hspace=0.03,wspace=0.04,top=0.96,bottom=0.01,left=0.005,right=0.995)
for col,s in enumerate([1,2,3,4,5,6]):
    gen,phe=load(s)
    G=np.array([[hx(c) for c in r] for r in gen],dtype=np.uint8)
    P=np.array([[0 if ch=='1' else 255 for ch in r] for r in phe],dtype=np.uint8)
    ag=fig.add_subplot(gs[0,col]); ag.imshow(G,aspect="auto",interpolation="nearest"); ag.set_xticks([]); ag.set_yticks([])
    ap=fig.add_subplot(gs[1,col]); ap.imshow(P,aspect="auto",cmap="gray",interpolation="nearest"); ap.set_xticks([]); ap.set_yticks([])
    ag.text(0.02,0.97,f"seed {s}",transform=ag.transAxes,fontsize=11,color="#c0392b",family="monospace",va="top",fontweight="bold")
fig.savefig("figures/river.png",dpi=600,bbox_inches="tight",facecolor="white")
fig.savefig("figures/river.pdf",bbox_inches="tight",facecolor="white")
print("wrote figures/river.png and figures/river.pdf")
