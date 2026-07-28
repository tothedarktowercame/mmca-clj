"""Collect every diversity/damage configuration into one table.

Coordinate is SUSTAINED diversity -- distinct rules held through the response
window -- not diversity at the fork instant. A field seeded with all 256 rules
holds them for an instant and collapses during the very window the damage is
measured in, so fork diversity is not what the perturbation travels through.
"""
import collections, numpy as np, pathlib
rows = []
def add(div, dmg, mech, label): rows.append((float(div), float(dmg), mech, label))

d = collections.defaultdict(lambda: collections.defaultdict(list))
for l in open('data/full_population_sweep.tsv'):
    f = l.rstrip('\n').split('\t')
    if len(f) == 6 and f[0] != 'op':
        d[f[0]]['re'].append(int(f[4])); d[f[0]]['dG'].append(int(f[5]))
for o in d:
    if o != 'preserver': add(np.mean(d[o]['re']), np.mean(d[o]['dG']), 'family', o)

m = collections.defaultdict(lambda: collections.defaultdict(list))
for l in open('data/mutation_axis.tsv'):
    f = l.rstrip('\n').split('\t')
    if len(f) == 6 and f[0] != 'cfg' and float(f[1]) > 0:
        m[(f[0], f[1])]['ru'].append(int(f[3])); m[(f[0], f[1])]['dG'].append(int(f[5]))
for (cfg, p), v in m.items():
    add(np.mean(v['ru']), np.mean(v['dG']), 'mutation', f'{cfg} p={p}')

for l in open('data/diversity_dial2_summary.tsv'):
    f = l.rstrip('\n').split('\t')
    if len(f) == 7 and f[0] != 'cfg':
        try:
            mech = ('blend' if f[0].startswith('blend') else
                    'async refuge' if f[0].startswith('async') else 'spatial niche')
            add(f[4], f[6], mech, f[0])
        except ValueError: pass

for l in open('data/diversity_dial3_summary.tsv'):
    f = l.rstrip('\n').split('\t')
    if len(f) >= 7:
        try: add(f[4], f[6], 'braid (fork-time)', f'{f[0]} t={f[1]}')
        except ValueError: pass

add(256.0, 1.00, 'preserving limit', 'genotype held fixed')

out = pathlib.Path('data/diversity_axis.tsv')
out.write_text("diversity\tdamage\tmechanism\tlabel\n" +
               "".join(f"{a:.1f}\t{b:.3f}\t{c}\t{e}\n" for a, b, c, e in sorted(rows)))
print(f"wrote {out} : {len(rows)} configurations, "
      f"{len({r[2] for r in rows})} mechanisms, diversity {min(r[0] for r in rows):.0f}-{max(r[0] for r in rows):.0f}")
