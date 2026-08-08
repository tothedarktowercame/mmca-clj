#!/usr/bin/env python3
"""Verify the exotype spacetime PDFs contain the exact phenotype grids.

Same regression as `check_fig2pair_pdf.py`: Matplotlib will happily embed a
raster SMALLER than the source array, silently crushing several data rows into
each pixel. On a 3000-row sheet at the default dpi that is 2.6 rows per pixel,
which destroys the fine diagonal structure and reads as moire.

This decodes the embedded stream with `pdfimages`, samples the centre of every
data cell, and compares cell-for-cell with the sheet the figure was drawn from.
"""
import subprocess, sys, tempfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

def sheet_rows(path):
    rows = [l.strip() for l in open(path) if l.strip()]
    if len({len(r) for r in rows}) != 1:
        raise SystemExit(f"ragged sheet: {path}")
    return rows

def check(pdf, sheet, image_index):
    rows = sheet_rows(sheet)
    H, W = len(rows), len(rows[0])
    with tempfile.TemporaryDirectory() as td:
        subprocess.run(["pdfimages", "-png", str(pdf), f"{td}/i"], check=True)
        imgs = sorted(Path(td).glob("i-*.png"))
        if image_index >= len(imgs):
            raise SystemExit(f"{pdf.name}: expected >{image_index} images, got {len(imgs)}")
        im = Image.open(imgs[image_index]).convert("L")
    w, h = im.size
    if h < H:
        raise SystemExit(f"FAIL {pdf.name}: embedded raster {w}x{h} has FEWER rows "
                         f"than the {H}-row sheet -- data is being crushed")
    px = im.load()
    xs = [min(w - 1, int((c + 0.5) * w / W)) for c in range(W)]
    # The raster is an UPSAMPLE of the sheet (h >= H), so matplotlib duplicates
    # some rows and a fixed centre-sample drifts out of phase. Each sheet row must
    # appear SOMEWHERE in its neighbourhood; that is the fidelity claim -- no row
    # was dropped. A missing row is what crushing looks like.
    bad_rows = 0
    for r in range(H):
        y0 = min(h - 1, int(r * h / H))
        best = W + 1
        for y in range(max(0, y0 - 2), min(h, y0 + 3)):
            d = sum(1 for c in range(W) if (px[xs[c], y] < 128) != (rows[r][c] == '1'))
            if d < best:
                best = d
            if best == 0:
                break
        if best > 0:
            bad_rows += 1
    print(f"  {pdf.name}: raster {w}x{h} vs sheet {W}x{H} -- "
          f"{H - bad_rows}/{H} rows reproduced exactly")
    return bad_rows, H

if __name__ == "__main__":
    d = ROOT / "data" / "exotype-sheets"
    figs = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "figures"
    bad, total = check(figs / "exo-lavalamp-spacetime.pdf", d / "8-0.1-2026102000-phe.txt", 0)
    # 1% tolerance: cell centres land on pixel boundaries where the raster is not an
    # integer multiple of the grid, so a thin band of cells is legitimately ambiguous.
    if bad > 0:
        raise SystemExit(f"FAIL: {bad}/{total} sheet rows do not appear in the embedded "
                         f"raster -- rows are being crushed")
    print("PASS: embedded raster reproduces the phenotype sheet")
