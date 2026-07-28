"""Render the three-panel diversity-axis figure committed with the paper.

The current exploratory diversity plot may evolve independently.  This entry
point intentionally preserves the exact seven-mechanism published panel.
"""
import collections
import csv

import numpy as np, matplotlib; matplotlib.use("Agg")
import matplotlib.pyplot as plt

rows = [l.rstrip('\n').split('\t') for l in open('data/diversity_axis.tsv')][1:]
D = np.array([float(r[0]) for r in rows]); G = np.array([float(r[1]) for r in rows])
M = np.array([r[2] for r in rows])

STYLE = {'family': ('#4c72b0', 'o'), 'braid (fork-time)': ('#dd8452', 's'),
         'mutation': ('#c44e52', 'v'), 'blend': ('#55a868', '^'),
         'async refuge': ('#8172b3', 'D'), 'spatial niche': ('#937860', 'P'),
         'preserving limit': ('#000000', '*')}

fig = plt.figure(figsize=(13, 4.6))
gs = fig.add_gridspec(1, 3, wspace=0.42, width_ratios=[1.6, 1, 1])

ax = fig.add_subplot(gs[0, 0])
for mech, (c, mk) in STYLE.items():
    s = M == mech
    if s.sum():
        ax.scatter(D[s], G[s], c=c, marker=mk, s=54 if mech != 'preserving limit' else 200,
                   edgecolor='white', linewidth=.6, label=f'{mech} ({s.sum()})', zorder=3)
edges = np.array([0, 40, 70, 100, 115, 132, 175, 260])
mid, med, q1, q3 = [], [], [], []
for a, b in zip(edges[:-1], edges[1:]):
    s = (D >= a) & (D < b)
    if s.sum() >= 3:
        mid.append(D[s].mean()); med.append(np.median(G[s]))
        q1.append(np.percentile(G[s], 25)); q3.append(np.percentile(G[s], 75))
ax.errorbar(mid, med, yerr=[np.array(med)-np.array(q1), np.array(q3)-np.array(med)],
            color='k', lw=1.6, marker='o', ms=5, capsize=3, zorder=4,
            label='binned median (IQR)')
ax.axvspan(135, 262, color='#d9d9d9', alpha=.55, zorder=0)
ax.text(197, 4.7, "reachable only by mutation\nor by freezing the genotype\n— diversity confounded\nwith mechanism here",
        ha='center', va='center', fontsize=6.8, style='italic',
        bbox=dict(fc='white', ec='#999', lw=.5, alpha=.9), zorder=5)
ax.set_xlabel("sustained genotype diversity (distinct rules)")
ax.set_ylabel("causal reach (cells damaged)")
ax.set_title("(a) ascending limb robust across 6 mechanisms; descending limb is mutation alone", fontsize=9.5)
ax.legend(fontsize=6.3, loc='upper left', framealpha=.95); ax.grid(alpha=.3)

ax = fig.add_subplot(gs[0, 1])
blend = collections.defaultdict(lambda: collections.defaultdict(list))
with open("data/diversity_dial2_summary.tsv", newline="", encoding="utf-8") as source:
    for row in csv.DictReader(source, delimiter="\t"):
        if row["cfg"].startswith("blend-strength"):
            strength = float(row["cfg"].split()[-1])
            blend[strength]["diversity"].append(float(row["rules-t30"]))
            blend[strength]["damage"].append(float(row["dG"]))
strengths = sorted(blend)
bl = [np.mean(blend[value]["diversity"]) for value in strengths]
bd = [np.mean(blend[value]["damage"]) for value in strengths]
ax.plot(strengths, bd, 'o-', color='#55a868', label='causal reach')
ax2 = ax.twinx(); ax2.plot(strengths, bl, 's--', color='#999', label='diversity')
ax.set_xlabel("blend strength $b$"); ax.set_ylabel("causal reach", color='#55a868', fontsize=9)
ax2.set_ylabel("sustained diversity", color='#777', fontsize=9)
ax.set_title("(b) the ascending limb\n(blend strength, no noise)", fontsize=10); ax.grid(alpha=.3)

ax = fig.add_subplot(gs[0, 2])
mutation = collections.defaultdict(lambda: collections.defaultdict(list))
with open("data/mutation_axis.tsv", newline="", encoding="utf-8") as source:
    for row in csv.DictReader(source, delimiter="\t"):
        if row["cfg"] == "p-a":
            rate = float(row["p"])
            mutation[rate]["diversity"].append(float(row["rules"]))
            mutation[rate]["damage"].append(float(row["dG"]))
ps = sorted(mutation)
dm = [np.mean(mutation[value]["damage"]) for value in ps]
dv = [np.mean(mutation[value]["diversity"]) for value in ps]
ax.plot(ps, dm, 'v-', color='#c44e52', label='causal reach')
ax3 = ax.twinx(); ax3.plot(ps, dv, 's--', color='#999')
ax3.set_ylabel("sustained diversity", color='#777', fontsize=9, labelpad=2)
ax.set_xscale('symlog', linthresh=0.005)
ax.set_xlabel("mutation rate $p$"); ax.set_ylabel("causal reach", color='#c44e52', fontsize=9)
ax.set_title("(c) the descending limb\n(mutation: churn stays 0.89–0.99)", fontsize=10); ax.grid(alpha=.3)

fig.suptitle("Causal reach rises with sustained genotype diversity, then collapses — but only where diversity is driven by mutation",
             fontsize=11.5)
for ext in ("png", "pdf"):
    fig.savefig(f"figures/diversity_axis.{ext}", dpi=150, bbox_inches="tight")
print("wrote figures/diversity_axis.{png,pdf}")
