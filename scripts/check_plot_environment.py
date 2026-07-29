#!/usr/bin/env python3
"""Fail unless the paper's fully pinned plotting environment is active."""

import sys

import matplotlib
import numpy
import PIL
import scipy


EXPECTED = {
    "python": "3.12.3",
    "numpy": "1.26.4",
    "matplotlib": "3.11.1",
    "pillow": "11.3.0",
    "scipy": "1.11.4",
}

ACTUAL = {
    "python": ".".join(map(str, sys.version_info[:3])),
    "numpy": numpy.__version__,
    "matplotlib": matplotlib.__version__,
    "pillow": PIL.__version__,
    "scipy": scipy.__version__,
}

errors = [
    f"{name}: expected {EXPECTED[name]}, found {ACTUAL[name]}"
    for name in EXPECTED
    if ACTUAL[name] != EXPECTED[name]
]
if errors:
    raise SystemExit(
        "Paper figure environment mismatch:\n  "
        + "\n  ".join(errors)
        + "\nInstall with: python3.12 -m pip install -r requirements-figures.txt"
    )

# check_fig2pair_pdf.py shells out to pdfimages to decode the embedded phenotype
# rasters. It runs near the END of reproduce_all.sh, so a missing binary used to
# surface only after the whole pipeline had already been computed -- 41 minutes
# in, on a clean box. Fail here instead.
import shutil

missing = [b for b in ("pdfimages",) if shutil.which(b) is None]
if missing:
    raise SystemExit(
        "Missing system binaries: " + ", ".join(missing)
        + "\nInstall with: sudo apt-get install poppler-utils"
    )

print(
    "plot environment:"
    f" Python {ACTUAL['python']},"
    f" NumPy {ACTUAL['numpy']},"
        f" Matplotlib {ACTUAL['matplotlib']},"
        f" Pillow {ACTUAL['pillow']},"
        f" SciPy {ACTUAL['scipy']},"
        " poppler-utils present"
)
