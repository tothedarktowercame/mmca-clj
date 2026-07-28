#!/usr/bin/env python3
"""Deterministically reduce raw Supplement 1 sweeps to plotted TSV summaries."""

import argparse
import csv
import math
import statistics
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path


DATA = Path(__file__).resolve().parents[1] / "data"


def rows(name):
    with (DATA / name).open(newline="", encoding="utf-8") as source:
        return list(csv.DictReader(source, delimiter="\t"))


def write(name, header, output_rows):
    path = DATA / name
    with path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.writer(target, delimiter="\t", lineterminator="\n")
        writer.writerow(header)
        writer.writerows(output_rows)
    print(f"wrote {path.relative_to(DATA.parent)}")


def sem(values):
    return statistics.stdev(values) / math.sqrt(len(values)) if len(values) > 1 else 0.0


def decimal_mean(group, field, places):
    mean = sum(Decimal(row[field]) for row in group) / Decimal(len(group))
    quantum = Decimal(1).scaleb(-places)
    return format(mean.quantize(quantum, rounding=ROUND_HALF_UP), f".{places}f")


def summarize_regime():
    groups = defaultdict(list)
    for row in rows("regime_placement.tsv"):
        groups[(row["class"], row["name"])].append(float(row["damage"]))
    output = []
    for (class_name, name), values in groups.items():
        output.append(
            (class_name, name, statistics.mean(values), sem(values), len(values))
        )
    # Python's stable sort retains generator order for equal means, matching the
    # order in which tied configurations appear in the raw sweep.
    output.sort(key=lambda row: row[2])
    write(
        "regime_placement_summary.tsv",
        ("class", "name", "mean", "sem", "n"),
        (
            (class_name, name, f"{mean:.4f}", f"{error:.4f}", count)
            for class_name, name, mean, error, count in output
        ),
    )


def summarize_gain():
    groups = defaultdict(list)
    for row in rows("river_gain.tsv"):
        groups[float(row["gamma"])].append(float(row["mass"]))
    write(
        "river_gain_summary.tsv",
        ("gamma", "mean", "sem", "n"),
        (
            (
                f"{gamma:.4f}",
                f"{statistics.mean(groups[gamma]):.4f}",
                f"{sem(groups[gamma]):.4f}",
                len(groups[gamma]),
            )
            for gamma in sorted(groups)
        ),
    )


def summarize_diversity():
    dial2 = defaultdict(list)
    for row in rows("diversity_dial2.tsv"):
        dial2[(row["cfg"], int(row["seed"]))].append(row)
    write(
        "diversity_dial2_summary.tsv",
        (
            "cfg",
            "seed",
            "rules-t30",
            "effective-t30",
            "rules-t70",
            "effective-t70",
            "dG",
        ),
        (
            (
                cfg,
                seed,
                f"{statistics.mean(float(row['rules-t30']) for row in group):.0f}",
                f"{statistics.mean(float(row['effective-t30']) for row in group):.2f}",
                f"{statistics.mean(float(row['rules-t70']) for row in group):.0f}",
                f"{statistics.mean(float(row['effective-t70']) for row in group):.2f}",
                f"{statistics.mean(float(row['dG']) for row in group):.2f}",
            )
            for (cfg, seed), group in dial2.items()
        ),
    )

    dial3 = defaultdict(list)
    for row in rows("diversity_dial3.tsv"):
        dial3[(row["cfg"], int(row["fork-time"]))].append(row)
    write(
        "diversity_dial3_summary.tsv",
        (
            "cfg",
            "fork-time",
            "rules-fork",
            "effective-fork",
            "rules-end",
            "effective-end",
            "dG-final",
            "dG-peak",
            "dG-area",
        ),
        (
            (
                cfg,
                fork_time,
                decimal_mean(group, "rules-fork", 1),
                decimal_mean(group, "effective-fork", 1),
                decimal_mean(group, "rules-end", 1),
                decimal_mean(group, "effective-end", 1),
                decimal_mean(group, "dG-final", 2),
                decimal_mean(group, "dG-peak", 2),
                decimal_mean(group, "dG-area", 1),
            )
            for (cfg, fork_time), group in dial3.items()
        ),
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("dataset", choices=("regime", "gain", "diversity"))
    args = parser.parse_args()
    {"regime": summarize_regime, "gain": summarize_gain, "diversity": summarize_diversity}[
        args.dataset
    ]()


if __name__ == "__main__":
    main()
