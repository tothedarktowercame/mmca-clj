"""Figure 3 — the cycle-parity dichotomy, five operators run ALONE."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def load(id):
    raw=open(f"data/fig3_{id}.txt").read().splitlines()
    i=raw.index("GEN")+1; gen=[]
    while raw[i]!="PHE" and raw[i]!="": gen.append(raw[i].split()); i+=1
    phe=[r for r in raw[raw.index("PHE")+1:] if r.strip()]
    return gen,phe
hx=lambda h:[int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
COLS=[("rot2","rot2 k→k+2","cycles (4 4)","even, fixed pt: SETTLES","even","#8b1a1a"),
      ("rot1","rot1 k→k+1","cycles (8)","even, fixed pt: yet LIVES","even","#2e7d32"),
      ("reduced0246","reduced rot2 {0,2,4,6}","cycles (4 1 1 1 1)","odd, no fixed pt: lives","odd","#2e7d32"),
      ("reduced024","reduced rot2 {0,2,4}","cycles (3 1 1 1 1 1)","odd, no fixed pt: lives","odd","#2e7d32"),
      ("reduced02","reduced rot2 {0,2}","cycles (2 1 1 1 1 1 1)","odd, no fixed pt: lives","odd","#2e7d32")]
fig=plt.figure(figsize=(13,7.2))
gs=fig.add_gridspec(2,5,height_ratios=[1,1],hspace=0.04,wspace=0.05,top=0.78,bottom=0.05,left=0.01,right=0.99)
fig.suptitle("CYCLE PARITY DICHOTOMY: a Boolean fixed point exists iff every cycle is even",
             fontsize=11,x=0.01,ha="left",y=0.985,family="monospace")
for col,(id,l1,l2,l3,kind,vcol) in enumerate(COLS):
    gen,phe=load(id)
    G=np.array([[hx(c) for c in r] for r in gen],dtype=np.uint8)
    P=np.array([[0 if ch=='1' else 255 for ch in r] for r in phe],dtype=np.uint8)
    c1="#8b1a1a" if kind=="even" else "#2e7d32"
    c2="#a020a0" if kind=="even" else "#2e7d32"
    axg=fig.add_subplot(gs[0,col]); axg.imshow(G,aspect="auto",interpolation="nearest"); axg.set_xticks([]); axg.set_yticks([])
    axp=fig.add_subplot(gs[1,col]); axp.imshow(P,aspect="auto",cmap="gray",interpolation="nearest"); axp.set_xticks([]); axp.set_yticks([])
    axg.text(0.0,1.22,l1,transform=axg.transAxes,fontsize=8.5,color=c1,family="monospace")
    axg.text(0.0,1.12,l2,transform=axg.transAxes,fontsize=8.5,color=c2,family="monospace")
    axg.text(0.0,1.02,l3,transform=axg.transAxes,fontsize=8.5,color=vcol,family="monospace")
    div=len(set(gen[-1]))
    axp.text(0.0,-0.05,f"diversity {div}",transform=axp.transAxes,fontsize=10,color="#111111",family="monospace",va="top")
fig.savefig("figures/parity-dichotomy.png",dpi=600,bbox_inches="tight",facecolor="white")
fig.savefig("figures/parity-dichotomy.pdf",bbox_inches="tight",facecolor="white")
print("wrote figures/parity-dichotomy.png and figures/parity-dichotomy.pdf; diversities:",
      [len(set(load(id)[0][-1])) for id,*_ in COLS])
