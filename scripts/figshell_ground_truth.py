"""Score TINT and widened-grid LCS against Figure 4's exact stripe boundary."""
from pathlib import Path
import csv
import numpy as np
from scipy.ndimage import uniform_filter

from phenotype_transport import loc

ACTIVITY = np.loadtxt("data/rule_activity_scores.txt")
SCORES = np.zeros(256)
SCORES[ACTIVITY[:, 0].astype(int)] = ACTIVITY[:, 1]


def stationary_two_rule_start(genotype):
    for time in range(len(genotype)):
        if len(np.unique(genotype[time])) == 2 and np.all(
                genotype[time:] == genotype[time]):
            return time
    raise ValueError("genotype never reaches a stationary two-rule tail")


def classification_metrics(mask, truth):
    true_positive = int(np.count_nonzero(mask & truth))
    false_positive = int(np.count_nonzero(mask & ~truth))
    false_negative = int(np.count_nonzero(~mask & truth))
    predicted_positive = true_positive + false_positive
    actual_positive = true_positive + false_negative
    precision = true_positive / predicted_positive if predicted_positive else 0.0
    recall = true_positive / actual_positive if actual_positive else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {"precision": precision, "recall": recall, "f1": f1,
            "true_positive": true_positive, "false_positive": false_positive,
            "false_negative": false_negative}


def main():
    genotype = np.loadtxt("data/figshell_gen.txt", dtype=int)
    phenotype = np.loadtxt("data/figshell_phe.txt", dtype=np.int8)
    lcs_full = np.loadtxt("data/figshell_lcs_mask.txt", dtype=np.int8).astype(bool)
    config = dict(line.rstrip().split("\t", 1)
                  for line in Path("data/figshell_lcs_config.tsv").read_text().splitlines())
    depth = int(config["selected_depth"])
    transient_end = stationary_two_rule_start(genotype)
    stripe_row = genotype[transient_end]
    boundaries = np.flatnonzero(stripe_row != np.roll(stripe_row, 1))

    smooth = uniform_filter(SCORES[genotype], size=5)
    grad_t, grad_x = np.gradient((smooth > 0.35).astype(float))
    tint_full = np.hypot(grad_t, grad_x) > 0

    rows = slice(transient_end, len(genotype))
    cols = slice(depth, genotype.shape[1] - depth)
    truth_full = np.zeros_like(genotype, dtype=bool)
    truth_full[transient_end:, boundaries] = True
    truth = truth_full[rows, cols]
    tint = tint_full[rows, cols]
    lcs = lcs_full[rows, cols]

    metrics = []
    for method, mask in (("tint", tint), ("lcs", lcs)):
        row = {"metric_kind": "boundary_detection", "method": method,
               "transient_end": transient_end, "selected_depth": depth,
               "selected_tolerance": float(config["selected_tolerance"]),
               "evaluation_cells": truth.size, "truth_cells": int(truth.sum()),
               "mask_cells": int(mask.sum())}
        row.update(classification_metrics(mask, truth))
        metrics.append(row)

    dest, self_ = phenotype[1:], phenotype[:-1]
    left, right = np.roll(self_, 1, axis=1), np.roll(self_, -1, axis=1)
    left_to_right = loc(dest, [self_, left], [self_])
    right_to_left = loc(dest, [self_, right], [self_])
    times = slice(transient_end - 1, len(left_to_right))
    boundary_set = set(boundaries.tolist())
    boundary_values, interior_values = [], []
    for right_col in range(genotype.shape[1]):
        left_col = (right_col - 1) % genotype.shape[1]
        values = np.concatenate((left_to_right[times, right_col],
                                 right_to_left[times, left_col]))
        if right_col in boundary_set:
            boundary_values.extend(values)
        else:
            interior_values.extend(values)
    boundary_mean = float(np.mean(boundary_values))
    interior_mean = float(np.mean(interior_values))
    metrics.append({"metric_kind": "phenotype_transport", "method": "known-boundary",
                    "transient_end": transient_end, "boundary_edge_count": len(boundaries),
                    "boundary_samples": len(boundary_values),
                    "interior_samples": len(interior_values),
                    "boundary_mean": boundary_mean, "interior_mean": interior_mean,
                    "boundary_minus_interior": boundary_mean - interior_mean})

    fields = sorted({key for row in metrics for key in row} - {"metric_kind"})
    fields.append("metric_kind")
    with open("data/figshell_ground_truth.tsv", "w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, delimiter="\t",
                                lineterminator="\n")
        writer.writeheader()
        writer.writerows(metrics)

    print(f"stationary two-rule tail: t={transient_end}; rules={np.unique(stripe_row).tolist()}")
    print(f"periodic boundary columns: {boundaries.tolist()}")
    for row in metrics[:2]:
        print(f"{row['method']}: precision={row['precision']:.4f} "
              f"recall={row['recall']:.4f} f1={row['f1']:.4f} "
              f"mask={row['mask_cells']} truth={row['truth_cells']}")
    print(f"phenotype directed transport: boundary={boundary_mean:+.6f} "
          f"interior={interior_mean:+.6f} delta={boundary_mean-interior_mean:+.6f}")
    print("wrote data/figshell_ground_truth.tsv")


if __name__ == "__main__":
    main()
