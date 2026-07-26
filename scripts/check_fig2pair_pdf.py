#!/usr/bin/env python3
"""Verify that Figure 3's PDF contains the exact phenotype grids.

This is a regression for the malformed one-bit indexed image streams emitted
by Matplotlib 3.6.3. ``pdfimages`` decodes the embedded raster streams; each
phenotype is then reduced with nearest-neighbour sampling to its original
space-time grid and compared cell-for-cell with the generated data.
"""

from pathlib import Path
import subprocess
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
PDF = ROOT / "figures" / "fig2pair.pdf"
CASES = [
    (ROOT / "data" / "fig2pair_draft3.txt", 1),
    (ROOT / "data" / "fig2pair_draft4.txt", 3),
]


def phenotype_rows(path):
    lines = path.read_text(encoding="utf-8").splitlines()
    start = lines.index("PHE") + 1
    rows = [line.strip() for line in lines[start:] if line.strip()]
    if not rows or len({len(row) for row in rows}) != 1:
        raise SystemExit(f"invalid phenotype grid in {path}")
    return rows


def assert_grid(path, image_path):
    rows = phenotype_rows(path)
    width, height = len(rows[0]), len(rows)
    expected = bytes(
        0 if cell == "1" else 255
        for row in rows
        for cell in row
    )
    with Image.open(image_path) as image:
        actual = (
            image.convert("L")
            .resize((width, height), Image.Resampling.NEAREST)
            .tobytes()
        )
    mismatches = sum(left != right for left, right in zip(expected, actual))
    if mismatches:
        raise SystemExit(
            f"{PDF.name}: {mismatches}/{width * height} phenotype cells "
            f"disagree with {path.name}"
        )
    return width * height


if not PDF.is_file():
    raise SystemExit(f"missing {PDF}")

with tempfile.TemporaryDirectory(prefix="fig2pair-pdf-check-") as tmp:
    prefix = Path(tmp) / "image"
    subprocess.run(
        ["pdfimages", "-png", str(PDF), str(prefix)],
        check=True,
    )
    images = sorted(Path(tmp).glob("image-*.png"))
    if len(images) != 4:
        raise SystemExit(f"{PDF.name}: expected 4 embedded images, found {len(images)}")
    checked = sum(assert_grid(data, images[index]) for data, index in CASES)

print(f"{PDF.name}: embedded phenotype rasters match all {checked} source cells")
