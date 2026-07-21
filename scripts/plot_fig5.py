"""Figure 5 — the population curve as a lambda-analogue. Reads data/fig5_off*.txt."""
import numpy as np, matplotlib
matplotlib.use("Agg"); import matplotlib.pyplot as plt
def curves(d): return [[int(x) for x in open(f"data/fig5_off{d:+d}_s{s}.txt").read().split()] for s in (0,1)]
fig, ax = plt.subplots(figsize=(7,4.2))
for d in [-4,-2,2,4]:
    ax.plot(np.mean(curves(d),axis=0), color="#bbbbbb", lw=1.0, zorder=1)
for d,col in [(-3,"#66bb6a"),(-1,"#2e7d32"),(1,"#1b5e20"),(3,"#388e3c")]:
    ax.plot(np.mean(curves(d),axis=0), color=col, lw=2.4, zorder=3, label=f"offset {d:+d}  (gcd 1)")
ax.annotate("offset $\\pm2,\\pm4$\ncollapse to 1–2 rules", xy=(70,2), xytext=(45,22),
            fontsize=9, color="#777777", arrowprops=dict(arrowstyle="->", color="#999999"))
ax.annotate("offset $\\pm1,\\pm3$ sustain\n~30 distinct rules", xy=(95,31), xytext=(52,62),
            fontsize=9, color="#1b5e20", arrowprops=dict(arrowstyle="->", color="#2e7d32"))
ax.set_xlabel("time (generations)"); ax.set_ylabel("distinct rules in the field")
ax.set_title("The population curve as a $\\lambda$-analogue: only the 8-cycles ($\\gcd=1$) sustain", fontsize=12)
ax.set_xlim(0,120); ax.set_ylim(0,72); ax.grid(alpha=.25)
ax.legend(fontsize=9, loc="upper center", bbox_to_anchor=(0.5,-0.18), ncol=2)
fig.savefig("figures/lambda_analogue.png", dpi=150, bbox_inches="tight", facecolor="white")
print("wrote figures/lambda_analogue.png")
