#!/usr/bin/env python3
"""Regenerate the Part IV figures from sheets produced by scripts/exotype_sheet.clj.

Usage:  python3 scripts/exotype_figures.py <sheet-dir> <out-dir>

Expects sheets named <beta>-<kappa>-<seed>-phe.txt (and -gen.txt for beta=8),
as produced by scripts/reproduce_exotype_figures.sh.

Frozen is defined as UNCHANGED FOR 15 STEPS.  This threshold is load-bearing: at
a 4-step threshold the median frozen episode is itself 4 steps, so the measured
turnover inflates roughly thirteenfold and reports threshold crossings as
structure.
"""
import sys, os, numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.colors import ListedColormap
from matplotlib.patches import Patch

SURFACE, LIVE, FROZEN, INK, MUTED = '#fcfcfb', '#2a78d6', '#e8e6dd', '#0b0b0b', '#52514e'
W = 15

def load(d, tag, expect=3000):
    """Load a sheet, refusing a truncated or ragged one.

    An earlier version silently dropped rows whose width differed from the first.
    That is precisely what hides a partially-written file: a sheet cut off mid-row
    loaded cleanly and was then classified as never reaching an absorbing state,
    because its truncation looked like ongoing change."""
    p = os.path.join(d, f"{tag}-phe.txt")
    rows = [l.rstrip('\n') for l in open(p)]
    widths = set(len(r) for r in rows)
    if len(widths) != 1:
        raise ValueError(f"{p}: ragged sheet, widths {sorted(widths)} -- truncated write?")
    if expect and len(rows) != expect:
        raise ValueError(f"{p}: {len(rows)} rows, expected {expect} -- incomplete run")
    return rows, np.array([[1 if c == '1' else 0 for c in r] for r in rows], dtype=np.int8)

# ---------------------------------------------------------------------------
# Matplotlib SHEARS a tall spacetime array when it has to scale it: resampling
# 250 columns up to ~2000 device px across 3000 rows accumulates affine error
# into a progressive horizontal shift, so vertical structure comes out diagonal.
# It is a geometry bug, not a quality one, and no interpolation/dpi setting
# avoids it. fig2pair never hits it because it embeds 1:1 with no scaling.
#
# So do the scaling in numpy -- integer replication, exact -- and hand matplotlib
# an array already at its final pixel size, making its transform the identity.
# scripts/check_exotype_pdf.py is the regression.
XSCALE = 8

def expand(M, k=XSCALE):
    """Replicate columns k-fold so matplotlib never has to rescale."""
    return np.repeat(M, k, axis=1)

def frozen_map(P, w=W):
    same = (P[1:] == P[:-1])
    F = np.zeros((P.shape[0]-1, P.shape[1]), np.int8)
    for t in range(w, F.shape[0]):
        F[t] = (same[t-w:t].sum(0) == w)
    return F[w:]

def absorbs_at(rows):
    for t in range(len(rows)-1, 0, -1):
        if rows[t] != rows[t-1]:
            return t if t < len(rows)-50 else None
    return None

def fig_bifurcation(d, out):
    """Frozen fraction over time for the seven long-run configurations, with the two
    extremes shown as spacetime diagrams.

    Deliberately free of in-figure narration: the reader is given the measured
    quantity, the configurations, and which runs reach an absorbing state.  What it
    means belongs in the caption and the text, not lettered onto the axes.
    """
    ARMS = [(2,'0.0'),(2,'0.1'),(4,'0.0'),(4,'0.1'),(4,'0.2'),(32,'0.1'),(64,'0.1')]
    fig = plt.figure(figsize=(15.2, 7.4)); fig.patch.set_facecolor(SURFACE)
    gs = fig.add_gridspec(1, 3, width_ratios=[1.45, 1, 1], wspace=0.16)
    ax = fig.add_subplot(gs[0, 0])
    for b, k in ARMS:
        tag = f"{b}-{k}-2026102000"
        try: rows, P = load(d, tag)
        except FileNotFoundError: continue
        same = (P[1:] == P[:-1])
        W4 = 4
        ff = [ (same[max(0,t-W4):t].sum(0) == min(t, W4)).mean() for t in range(W4, len(same), 10) ]
        ts = list(range(W4, len(same), 10))
        dead = absorbs_at(rows) is not None
        ax.plot(ts, ff, lw=1.5, color=('#b8291f' if dead else LIVE), alpha=0.85,
                label=rf"$\beta$={b}, $\kappa$={k}" + ("  (absorbing)" if dead else ""))
    ax.set_xlabel('time step', fontsize=11.5); ax.set_ylabel('frozen fraction', fontsize=11.5)
    ax.set_ylim(-0.03, 1.05)
    ax.legend(fontsize=9, frameon=False, loc='center right')
    ax.tick_params(labelsize=9)
    for sp in ax.spines.values(): sp.set_edgecolor('#cfcdc4')
    ax.spines['top'].set_visible(False); ax.spines['right'].set_visible(False)
    for j, (tag, title) in enumerate([("32-0.1-2026102000", r"$\beta$=32, $\kappa$=0.1"),
                                      ("2-0.0-2026102000",  r"$\beta$=2, $\kappa$=0")]):
        a2 = fig.add_subplot(gs[0, j+1])
        rows, P = load(d, tag); t_abs = absorbs_at(rows)
        a2.imshow(expand(P), cmap='binary', aspect='auto', interpolation='nearest')
        a2.set_title(title, fontsize=12.5, pad=7, color=INK)
        a2.set_xticks([]); a2.set_yticks([0,1000,2000,3000] if j==0 else [])
        a2.tick_params(labelsize=9)
        a2.set_xlabel(rf"absorbing at $t={t_abs}$" if t_abs else "no absorbing state",
                      fontsize=10.5, color=('#b8291f' if t_abs else MUTED), labelpad=5)
        if j == 0: a2.set_ylabel('time step', fontsize=11)
        for sp in a2.spines.values():
            sp.set_edgecolor('#b8291f' if t_abs else '#cfcdc4'); sp.set_linewidth(1.6 if t_abs else 0.8)
    plt.subplots_adjust(left=0.055, right=0.985, top=0.94, bottom=0.115)
    for ext in ('png', 'pdf'):
        plt.savefig(os.path.join(out, 'exo-bifurcation.' + ext), dpi=300, facecolor=SURFACE)
    plt.close(); print("wrote exo-bifurcation.{png,pdf}")

def fig_bisection(d, out):
    betas = [8, 10, 12, 14, 16]
    cmap = ListedColormap([LIVE, FROZEN])
    fig, axes = plt.subplots(1, 5, figsize=(16.4, 11.6), gridspec_kw={'wspace': 0.09})
    fig.patch.set_facecolor(SURFACE)
    for ax, b in zip(axes, betas):
        rows, P = load(d, f"{b}-0.1-2026102000"); F = frozen_map(P); a = absorbs_at(rows)
        ax.imshow(expand(F), cmap=cmap, aspect='auto', interpolation='nearest', vmin=0, vmax=1)
        ax.set_xticks([]); ax.set_yticks([0, 1000, 2000, 3000] if b == betas[0] else [])
        ax.set_title(rf"$\beta$={b}", fontsize=14, pad=9, fontweight='bold', color=INK)
        ax.set_xlabel((f"absorbs at t={a}" if a else "never absorbs")
                      + f"\nfrozen {F[1000:].mean():.3f}", fontsize=10.2,
                      color=(MUTED if a else '#b8291f'), labelpad=6)
        for s in ax.spines.values():
            s.set_edgecolor('#cfcdc4' if a else '#b8291f'); s.set_linewidth(0.7 if a else 2.4)
        if b == betas[0]: ax.set_ylabel('time step', fontsize=11.5, color=INK)
    fig.suptitle('The bisection in frozen/live coordinates', fontsize=16, y=0.972, color=INK)
    fig.text(0.5, 0.94, r'Frozen := unchanged for 15 steps. $\kappa$=0.1, seed 2026102000.',
             ha='center', fontsize=10.5, color=MUTED)
    fig.legend(handles=[Patch(facecolor=LIVE, label='live (changing)'),
                        Patch(facecolor=FROZEN, edgecolor='#cfcdc4', label=r'frozen ($\geq$15 steps)'),
                        Patch(facecolor='none', edgecolor='#b8291f', lw=2.2, label='never absorbs')],
               loc='lower center', ncol=3, frameon=False, fontsize=11.5, bbox_to_anchor=(0.5, 0.012))
    plt.subplots_adjust(left=0.062, right=0.986, top=0.895, bottom=0.10)
    for ext in ('png', 'pdf'):
        plt.savefig(os.path.join(out, 'exo-bisection.' + ext), dpi=300, facecolor=SURFACE)
    plt.close(); print("wrote exo-bisection.{png,pdf}")

def fig_lavalamp(d, out):
    tag = "8-0.1-2026102000"
    rows, P = load(d, tag)
    G = [l.split() for l in open(os.path.join(d, f"{tag}-gen.txt"))]
    ids = sorted({x for r in G for x in r}); m = {v: i for i, v in enumerate(ids)}
    G = np.array([[m[x] for x in r] for r in G])
    rng = np.random.RandomState(7)
    cm = ListedColormap(plt.cm.turbo(np.linspace(0, 1, 256))[rng.permutation(256)])
    # figsize is SOLVED, not chosen: at dpi=300 it puts the phenotype panel at exactly
    # 3000 device px for a 3000-row sheet, so matplotlib's resample is the identity and
    # every row survives into the PDF. Off by even one pixel (3001) the ratio is
    # non-integer and rows are BLENDED even under interpolation='nearest'.
    # scripts/check_exotype_pdf.py is the regression for this.
    fig = plt.figure(figsize=(14.764, 16.5945)); fig.patch.set_facecolor(SURFACE)
    gs = fig.add_gridspec(2, 2, height_ratios=[2.5, 1], hspace=0.11, wspace=0.06)
    for j, (M, c, lab) in enumerate([(P, 'binary', 'PHENOTYPE'), (G % 256, cm, 'GENOTYPE')]):
        ax = fig.add_subplot(gs[0, j])
        ax.imshow(expand(M), cmap=c, aspect='auto', interpolation='nearest')
        ax.set_title(lab, fontsize=14, pad=9, fontweight='bold', color=INK)
        ax.set_xticks([]); ax.set_yticks([0, 1000, 2000, 3000] if j == 0 else [])
        if j == 0: ax.set_ylabel('time step', fontsize=12, color=INK)
    T0, T1, X0, X1 = 1600, 1900, 60, 190
    for j, (M, c) in enumerate([(P, 'binary'), (G % 256, cm)]):
        ax = fig.add_subplot(gs[1, j])
        ax.imshow(M[T0:T1, X0:X1], cmap=c, aspect='auto', interpolation='nearest')
        ax.set_xticks([]); ax.set_yticks([0, 100, 200, 300])
        ax.set_yticklabels([T0, T0+100, T0+200, T0+300])
        ax.set_xlabel(f'zoom: t = {T0}–{T1}, cells {X0}–{X1}', fontsize=10.5, color=MUTED)
        for s in ax.spines.values(): s.set_edgecolor('#b8291f'); s.set_linewidth(2.0)
    fig.suptitle(r'$\beta$=8, $\kappa$=0.1 — 3000 steps, width 250, seed 2026102000',
                 fontsize=16, y=0.973, color=INK)
    plt.subplots_adjust(left=0.055, right=0.985, top=0.935, bottom=0.045)
    for ext in ('png', 'pdf'):
        plt.savefig(os.path.join(out, 'exo-lavalamp-spacetime.' + ext), dpi=300, facecolor=SURFACE)
    plt.close(); print("wrote exo-lavalamp-spacetime.{png,pdf}")

if __name__ == '__main__':
    d, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    fig_bifurcation(d, out); fig_bisection(d, out); fig_lavalamp(d, out)
