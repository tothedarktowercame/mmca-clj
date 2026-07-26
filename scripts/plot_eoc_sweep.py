"""Edge-of-chaos computation curve from the synthetic sweep.
For each grid point (order q, chaos p) average the on-filament combining
(synergy) and transport TE over 8 seeds, with SEM error bars, and plot vs a
disorder axis: ORDER (q:0->1) -> EDGE (q=1=p=0) -> CHAOS (p:0->1).
Run from the mmca-clj repo root. Writes high-resolution PNG and PDF files."""
import numpy as np, glob
from scipy.ndimage import uniform_filter
import matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt

_r = np.loadtxt("data/rule_activity_scores.txt")
scores = np.zeros(256); scores[_r[:, 0].astype(int)] = _r[:, 1]

def cond(dest, cs):
    key = np.zeros(dest.shape, int)
    for i, c in enumerate(cs): key += (c.astype(int) << i)
    p = np.zeros(dest.shape)
    for k in range(1 << len(cs)):
        m = key == k
        if m.any(): p[m] = dest[m].mean()
    return p

def field_measures(path):
    field = np.loadtxt(path, dtype=int)
    smooth = uniform_filter(scores[field], size=5)
    act = (smooth > 0.35).astype(np.int8)
    gt, gx = np.gradient((smooth > 0.35).astype(float)); bnd = (np.hypot(gt, gx) > 0)[1:]
    dest, self_ = act[1:], act[:-1]
    L = np.roll(act[:-1], +1, axis=1); R = np.roll(act[:-1], -1, axis=1)
    def loc(nc, dc):
        pn, pd = cond(dest, nc), cond(dest, dc); o = np.zeros(dest.shape)
        a = (dest==1)&(pn>0)&(pd>0); o[a] = np.log2(pn[a]/pd[a])
        z = (dest==0)&((1-pn)>0)&((1-pd)>0); o[z] = np.log2((1-pn[z])/(1-pd[z]))
        return o
    te_l, te_r = loc([self_, L],[self_]), loc([self_, R],[self_])
    te_j = loc([self_, L, R],[self_])
    if bnd.sum() == 0: return 0.0, 0.0
    syn = (te_j - (te_l + te_r))[bnd].mean()
    te = (te_l + te_r)[bnd].mean()
    return syn, te

def point(pattern):
    vals = np.array([field_measures(f) for f in sorted(glob.glob(pattern))])
    syn, te = vals[:, 0], vals[:, 1]
    return syn.mean(), syn.std(ddof=1)/np.sqrt(len(syn)), te.mean(), te.std(ddof=1)/np.sqrt(len(te))

order_qs = [i/10 for i in range(11)]           # 0.0 .. 1.0 (1.0 = edge)
chaos_ps = [0.01,0.02,0.05,0.1,0.2,0.4,0.7,1.0] # edge (p=0) omitted; == q=1.0
xs, syn_m, syn_e, te_m, te_e, tick = [], [], [], [], [], []
for q in order_qs:
    m,e,tm,te_ = point(f"data/eoc_sweep_order_q{round(q*1000):03d}_s*.txt")
    xs.append(q); syn_m.append(m); syn_e.append(e); te_m.append(tm); te_e.append(te_)
    tick.append(f"q{q:.1f}")
for p in chaos_ps:
    m,e,tm,te_ = point(f"data/eoc_sweep_chaos_p{round(p*1000):03d}_s*.txt")
    xs.append(1+p); syn_m.append(m); syn_e.append(e); te_m.append(tm); te_e.append(te_)
    tick.append(f"p{p:g}")

xs = np.arange(len(syn_m))          # uniform categorical spacing (q and p differ in units)
edge_idx = len(order_qs) - 1        # offset+1 (q=1.0): where blend flank meets noise flank
peak_i = int(np.argmax(syn_m))
print("edge (q=1.0) synergy =", round(syn_m[10],4), "peak at", tick[peak_i], "=", round(syn_m[peak_i],4))
for t,m,e in zip(tick,syn_m,syn_e): print(f"  {t:>6}  synergy {m:+.4f} ± {e:.4f}")

plt.rcParams.update({"font.family": "serif", "mathtext.fontset": "cm"})
fig, ax = plt.subplots(figsize=(7.6, 4.0))
top = max(syn_m); ymax = top*1.20
ax.set_xlim(-0.6, len(xs)-0.4); ax.set_ylim(-0.002, ymax)
ax.axhline(0, color="0.75", lw=0.7, zorder=0)
# two flanks meet at offset+1 (edge_idx); blend/order flank left, noise/chaos flank right
div = edge_idx + 0.5
ax.axvspan(-0.6, div, color="#dfe7f0", alpha=0.5, zorder=0, lw=0)   # order flank (cool)
ax.axvspan(div, len(xs)-0.4, color="#f2e4d8", alpha=0.5, zorder=0, lw=0)  # chaos flank (warm)
ax.text(1.7, ymax*0.96, "order flank\n(blend, $q$)", ha="center", va="top", fontsize=8, color="#3a5a7a")
ax.text((edge_idx+len(xs))/2.0, ymax*0.96, "chaos flank\n(noise, $p$)", ha="center", va="top", fontsize=8, color="#9a5a3a")
ax.errorbar(xs, syn_m, yerr=syn_e, fmt="o-", color="#1a1a1a", ms=4, lw=1.4, capsize=2, zorder=2)
# computational maximum (the sampled edge) and where the paper's operator sits
ax.plot([xs[peak_i]], [syn_m[peak_i]], "o", color="#1b7a3d", ms=10, zorder=3)
ax.annotate(r"sampled edge (peak, $q\approx0.4$)", (xs[peak_i], syn_m[peak_i]),
            xytext=(xs[peak_i]+1.1, ymax*0.90), ha="left", fontsize=8, color="#1b7a3d", fontweight="bold",
            arrowprops=dict(arrowstyle="->", color="#1b7a3d", lw=0.9))
ax.axvline(edge_idx, color="#666", lw=0.9, ls=":", zorder=1)
ax.annotate(r"offset$+1$" "\n(pure propagator)", (edge_idx, syn_m[edge_idx]),
            xytext=(edge_idx-1.7, syn_m[edge_idx]-top*0.34), fontsize=7.5, color="#444", ha="left",
            arrowprops=dict(arrowstyle="->", color="#666", lw=0.8))
ax.text(len(xs)-0.6, ymax*0.52, r"$N=8$ seeds/point", ha="right", va="center",
        fontsize=7.5, color="0.45", style="italic")
ax.set_xticks(xs); ax.set_xticklabels(tick, fontsize=7, rotation=90)
ax.set_ylabel("filament combining: synergy (bits)", fontsize=9)
ax.set_xlabel(r"increasing disorder $\rightarrow$", fontsize=9)
ax.tick_params(labelsize=7.5)
fig.suptitle("Combining peaks at a literal edge of chaos", fontsize=11, y=0.995)
fig.savefig("figures/edge_of_chaos_curve.png", dpi=600, bbox_inches="tight", facecolor="white")
fig.savefig("figures/edge_of_chaos_curve.pdf", bbox_inches="tight", facecolor="white")
print("wrote figures/edge_of_chaos_curve.png and figures/edge_of_chaos_curve.pdf")
