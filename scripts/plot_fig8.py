"""Figure 2 — the 'Figure 8' triptych: genotype | phenotype | population.
Reads data/fig8_raw.txt (from gen_fig8.clj), writes figures/fig8.png."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
SEED = 4; N = 100
raw = open("data/fig8_raw.txt").read().splitlines()
def sec(name, stop):
    i = raw.index(name)+1; r=[]
    while i < len(raw) and raw[i] not in stop and raw[i] != "": r.append(raw[i]); i+=1
    return r
gen = [x.split() for x in sec("GEN", {"RULES","PHE"})]; sig = sec("RULES", {"PHE"}); phe = sec("PHE", set())
gen, sig, phe = gen[:N], sig[:N], phe[:N]
hx   = lambda h: [int(h.lstrip('#')[i:i+2],16) for i in (0,2,4)]
rule = lambda h: (lambda s: int(s[0:2],16) if s[0:2]==s[2:4]==s[4:6] else -1)(h.lstrip('#'))
G = np.array([[hx(c) for c in r] for r in gen], dtype=np.uint8)
P = np.array([[0 if ch=='1' else 255 for ch in r] for r in phe], dtype=np.uint8)
W = len(gen[0]); t = np.arange(N)
distinct = [len(set(r)) for r in sig]
c42  = [sum(1 for c in row if rule(c)==42)  for row in gen]
c170 = [sum(1 for c in row if rule(c)==170) for row in gen]
fig = plt.figure(figsize=(9,5.0)); gs = fig.add_gridspec(1,3,width_ratios=[1,1,1.45],wspace=0.30)
a0 = fig.add_subplot(gs[0]); a0.imshow(G,aspect="auto",interpolation="nearest"); a0.set_title("genotype",fontsize=12); a0.set_xticks([]); a0.set_ylabel("time",fontsize=11)
a1 = fig.add_subplot(gs[1],sharey=a0); a1.imshow(P,aspect="auto",cmap="gray",interpolation="nearest"); a1.set_title("phenotype",fontsize=12); a1.set_xticks([]); plt.setp(a1.get_yticklabels(),visible=False)
a2 = fig.add_subplot(gs[2],sharey=a0)
a2.plot(distinct,t,color="#888888",lw=1.7,label="distinct rules")
a2.plot(c170,t,color="#c0392b",lw=1.5,label="rule 170")
a2.plot(c42,t,color="#2c3e50",lw=1.5,label="rule 42")
a2.set_title("population",fontsize=12); a2.set_xlim(0,W+24); a2.set_ylim(N-1,0)
a2.set_xlabel(f"cells (of {W})",fontsize=10); a2.grid(axis="x",alpha=0.25); plt.setp(a2.get_yticklabels(),visible=False)
a2.legend(fontsize=8,loc="upper right",framealpha=0.95,borderpad=0.5)
fig.suptitle(f"Figure 8 (seed {SEED}): the cast dies off; two survivors take over",fontsize=13,y=0.99)
fig.savefig("figures/fig8.png",dpi=600,bbox_inches="tight",facecolor="white")
fig.savefig("figures/fig8.pdf",bbox_inches="tight",facecolor="white")
print("wrote figures/fig8.png and figures/fig8.pdf")
