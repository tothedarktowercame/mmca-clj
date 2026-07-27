"""Filament transport measured on phenotype, independent of tint threshold.

The tint mask is still derived from smoothed isolated-rule activity.  Directed
transport and combining are computed on the raw binary phenotype field, so the
measured variable shares neither the activity statistic nor its threshold.

Seed 20260727 controls the size-matched null.  The shift null is exhaustive and
deterministic; the cross-seed null uses every other offset+1 seed.
"""
from pathlib import Path
import csv
import numpy as np
from scipy.ndimage import uniform_filter

ANALYSIS_SEED = 20260727
BURN_IN = 20
MARGIN = 1
D_MIN = 12
N_NULL = 20
MASK_THRESHOLDS = (0.25, 0.30, 0.35, 0.40, 0.50)
_activity = np.loadtxt("data/rule_activity_scores.txt")
scores = np.zeros(256)
scores[_activity[:, 0].astype(int)] = _activity[:, 1]


def conditional_probability(dest, conditions):
    key = np.zeros(dest.shape, dtype=int)
    for index, condition in enumerate(conditions):
        key += condition.astype(int) << index
    probability = np.zeros(dest.shape)
    for value in range(1 << len(conditions)):
        selected = key == value
        if selected.any():
            probability[selected] = dest[selected].mean()
    return probability


def loc(dest, numerator_conditions, denominator_conditions):
    numerator = conditional_probability(dest, numerator_conditions)
    denominator = conditional_probability(dest, denominator_conditions)
    result = np.zeros(dest.shape)
    ones = (dest == 1) & (numerator > 0) & (denominator > 0)
    result[ones] = np.log2(numerator[ones] / denominator[ones])
    zeros = ((dest == 0) & ((1 - numerator) > 0)
             & ((1 - denominator) > 0))
    result[zeros] = np.log2(
        (1 - numerator[zeros]) / (1 - denominator[zeros]))
    return result


def load(prefix):
    return (np.loadtxt(f"{prefix}_gen.txt", dtype=int),
            np.loadtxt(f"{prefix}_phe.txt", dtype=np.int8))


def tint_mask(genotype, threshold=0.35):
    smooth = uniform_filter(scores[genotype], size=5)
    gt, gx = np.gradient((smooth > threshold).astype(float))
    return (np.hypot(gt, gx) > 0)[1:]


def phenotype_measures(phenotype):
    dest, self_ = phenotype[1:], phenotype[:-1]
    left = np.roll(self_, +1, axis=1)
    right = np.roll(self_, -1, axis=1)
    tl = loc(dest, [self_, left], [self_])
    tr = loc(dest, [self_, right], [self_])
    joint = loc(dest, [self_, left, right], [self_])
    return tl + tr, joint - (tl + tr)


def crop(values):
    return values[BURN_IN - 1:, MARGIN:-MARGIN]


def size_null(values, mask, seed):
    rng = np.random.default_rng(seed)
    count = int(mask.sum())
    draws = []
    for _ in range(N_NULL):
        chosen = rng.permutation(mask.size)[:count]
        null = np.zeros(mask.size, dtype=bool)
        null[chosen] = True
        draws.append(values[null.reshape(mask.shape)].mean())
    return float(np.mean(draws))


def shift_null(values, mask):
    fm = np.fft.rfft(mask.astype(float), axis=1)
    fv = np.fft.rfft(values, axis=1)
    profile = np.fft.irfft((np.conj(fm) * fv).sum(0), n=mask.shape[1])
    profile /= mask.sum()
    width = mask.shape[1]
    offsets = [d for d in range(width) if D_MIN <= d <= width - D_MIN]
    return float(profile[offsets].mean())


def score(genotype, phenotype, threshold, seed, cross_masks=()):
    te, combining = map(crop, phenotype_measures(phenotype))
    mask = crop(tint_mask(genotype, threshold)).astype(bool)
    if not mask.any():
        raise ValueError("empty tint mask")
    cross_masks = [m for m in cross_masks if m.any()]
    row = {
        "threshold": threshold,
        "mask_density": float(mask.mean()),
        "transport_on": float(te[mask].mean()),
        "combining_on": float(combining[mask].mean()),
    }
    for name, values in (("transport", te), ("combining", combining)):
        rand = size_null(values, mask, seed)
        shift = shift_null(values, mask)
        row[f"{name}_size"] = float(row[f"{name}_on"] - rand)
        row[f"{name}_shift"] = float(row[f"{name}_on"] - shift)
        if cross_masks:
            cross = float(np.mean([values[m].mean() for m in cross_masks]))
            row[f"{name}_cross"] = float(row[f"{name}_on"] - cross)
    return row, mask


def mean_rows(rows):
    keys = [key for key, value in rows[0].items()
            if isinstance(value, (int, float, np.integer, np.floating))]
    return {key: float(np.mean([row[key] for row in rows])) for key in keys}


def main():
    offset = [load(f"data/phenotype_transport_offset1_s{seed}")
              for seed in range(6)]
    masks = [crop(tint_mask(gen, 0.35)).astype(bool) for gen, _ in offset]
    offset_rows = []
    for seed, (gen, phe) in enumerate(offset):
        row, _ = score(gen, phe, 0.35, ANALYSIS_SEED + seed,
                       [mask for other, mask in enumerate(masks)
                        if other != seed])
        row.update(dataset="offset1-W64-T120", seed=seed)
        offset_rows.append(row)

    eoc_rows = []
    for index, name in enumerate(("offset1", "two4cyc", "sigma16250374")):
        gen, phe = load(f"data/eoc_tint_{name}")
        row, _ = score(gen, phe, 0.35, ANALYSIS_SEED + 100 + index)
        row.update(dataset=f"eoc-{name}-W256-T600", seed=1)
        eoc_rows.append(row)

    sweep_rows = []
    for threshold in MASK_THRESHOLDS:
        rows = []
        for seed, (gen, phe) in enumerate(offset):
            row, _ = score(gen, phe, threshold, ANALYSIS_SEED + 1000 + seed)
            rows.append(row)
        mean = mean_rows(rows)
        mean.update(dataset="offset1-threshold-control", seed="mean")
        sweep_rows.append(mean)

    output = offset_rows + eoc_rows + sweep_rows
    Path("data").mkdir(exist_ok=True)
    with open("data/phenotype_transport.tsv", "w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=sorted(
            {key for row in output for key in row}), delimiter="\t",
                                lineterminator="\n")
        writer.writeheader()
        writer.writerows(output)

    aggregate = mean_rows(offset_rows)
    print(f"analysis seed {ANALYSIS_SEED}; offset+1 seeds 0..5")
    print("phenotype transport against all three nulls:")
    print(f"  on {aggregate['transport_on']:+.4f}; "
          f"size {aggregate['transport_size']:+.4f}; "
          f"shift {aggregate['transport_shift']:+.4f}; "
          f"cross-seed {aggregate['transport_cross']:+.4f}")
    print("phenotype combining against all three nulls:")
    print(f"  on {aggregate['combining_on']:+.4f}; "
          f"size {aggregate['combining_size']:+.4f}; "
          f"shift {aggregate['combining_shift']:+.4f}; "
          f"cross-seed {aggregate['combining_cross']:+.4f}")
    print("threshold control (fixed phenotype measure, varied tint mask):")
    for row in sweep_rows:
        print(f"  mask {row['threshold']:.2f}: "
              f"transport-size {row['transport_size']:+.4f}, "
              f"combining-size {row['combining_size']:+.4f}")
    print("wrote data/phenotype_transport.tsv")


if __name__ == "__main__":
    main()
