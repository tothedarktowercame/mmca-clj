"""Figure 4 — the rotation survey, panels (a) negative and (b) positive offsets.
Reads data/fig4_off*.txt; writes figures/survey_a.png, figures/survey_b.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def load(d,s):
    raw=open(f"data/fig4_off{d:+d}_s{s}.txt").read().splitlines()
    i=raw.index("GEN")+1; gen=[]
    while raw[i]!="PHE" and raw[i]!="": gen.append(raw[i].split()); i+=1
    phe=[r for r in raw[raw.index("PHE")+1:] if r.strip()]
    return gen,phe
hx=lambda h:[int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
def collapse(gen,thr=3):
    for t,row in enumerate(gen):
        if len(set(row))<=thr: return t
    return len(gen)-1
STRUCT={4:"four 2-cycles",2:"two 4-cycles",1:"one 8-cycle"}
def panel(offsets,letter,sign,fname):
    n=len(offsets); fig=plt.figure(figsize=(10,14))
    gs=fig.add_gridspec(n,5,width_ratios=[1.15,1,1,1,1],hspace=0.16,wspace=0.05,
                        left=0.02,right=0.99,top=0.925,bottom=0.02)
    fig.suptitle(f"A family of operators, ranked by cycle structure  ({letter} · {sign} offsets)",
                 fontsize=15,fontweight="bold",x=0.02,ha="left",y=0.985)
    fig.text(0.02,0.95,"Each row: bit[$\\sigma$(k)] := ¬bit[k], $\\sigma$ = rotate-by-offset (wrap). "
             "genotype 256-colour | phenotype b/w, 100 gens, two seeds.",fontsize=10,color="#555555",ha="left")
    for r,d in enumerate(offsets):
        g=int(np.gcd(abs(d),8)); eoc=(g==1); col=collapse(load(d,0)[0])  # Wolfram order: 8-cycles (gcd 1, offset +/-1,+/-3) sustain
        axl=fig.add_subplot(gs[r,0]); axl.axis("off")
        axl.text(0,0.70,f"offset {d:+d}",fontsize=15,fontweight="bold",transform=axl.transAxes,
                 color="#1b5e20" if eoc else "#111111")
        axl.text(0,0.54,f"gcd {g} · {STRUCT[g]}",fontsize=10,color="#555555",transform=axl.transAxes)
        axl.text(0,0.39,("no collapse (~t100)" if eoc else f"collapses ~t{col}"),
                 fontsize=11,fontweight="bold",color=("#2e7d32" if eoc else "#a11111"),transform=axl.transAxes)
        rowaxes=[]
        for c,(s,kind) in enumerate([(0,"g"),(0,"p"),(1,"g"),(1,"p")]):
            gen,phe=load(d,s); ax=fig.add_subplot(gs[r,c+1]); rowaxes.append(ax)
            if kind=="g":
                ax.imshow(np.array([[hx(x) for x in row] for row in gen],dtype=np.uint8),aspect="auto",interpolation="nearest")
            else:
                ax.imshow(np.array([[0 if ch=='1' else 255 for ch in row] for row in phe],dtype=np.uint8),aspect="auto",cmap="gray",interpolation="nearest")
            ax.set_xticks([]); ax.set_yticks([])
        if eoc:  # green highlight band behind the whole row
            p0=axl.get_position(); p1=rowaxes[-1].get_position()
            y0,y1=p1.y0-0.006,p1.y1+0.006; x0=0.008; x1=p1.x1+0.006
            fig.patches.append(plt.Rectangle((x0,y0),x1-x0,y1-y0,transform=fig.transFigure,
                               facecolor="#e8f5e9",edgecolor="none",zorder=-1))
            fig.patches.append(plt.Rectangle((x0,y0),0.004,y1-y0,transform=fig.transFigure,
                               facecolor="#2e7d32",edgecolor="none",zorder=1))
    fig.savefig(fname,dpi=100,bbox_inches="tight",facecolor="white")
    print("wrote",fname)
panel([-4,-3,-2,-1],"a","negative","figures/survey_a.png")
panel([1,2,3,4],"b","positive","figures/survey_b.png")
