#!/usr/bin/env bash
# Twice-from-empty determinism check for the paper's reproducibility claim.
# Runs reproduce_all.sh from an empty data/ twice and compares every artifact.
# Existing data/ and figures/ are moved aside and restored at the end.
set -uo pipefail
cd "$(dirname "$0")/.."
export PYTHON="$PWD/.venv/bin/python"
PATH="$PWD/.venv/bin:$PATH"; export PATH
OUT=/tmp/determinism; rm -rf "$OUT"; mkdir -p "$OUT"

stash() { for d in data figures; do [ -d "$d" ] && mv "$d" "$OUT/${d}.saved"; done; }
restore() { rm -rf data figures; for d in data figures; do [ -d "$OUT/${d}.saved" ] && mv "$OUT/${d}.saved" "$d"; done; }
trap restore EXIT

snapshot() {  # $1 = pass label; hash every generated artifact, content only
  find data figures -type f \( -name '*.tsv' -o -name '*.npz' -o -name '*.txt' \) 2>/dev/null \
    | sort | while read -r f; do printf '%s  %s\n' "$(sha256sum <"$f" | cut -d' ' -f1)" "$f"; done \
    > "$OUT/hashes.$1"
}

stash
for pass in 1 2; do
  echo "=== pass $pass start $(date -Is) ==="
  rm -rf data figures; mkdir -p data figures
  t0=$SECONDS
  scripts/reproduce_all.sh > "$OUT/run.$pass.log" 2>&1
  rc=$?
  echo "pass $pass exit=$rc elapsed=$((SECONDS-t0))s"
  echo "$((SECONDS-t0))" > "$OUT/elapsed.$pass"
  echo "$rc" > "$OUT/rc.$pass"
  snapshot "$pass"
done

echo "=== comparison ==="
if diff -u "$OUT/hashes.1" "$OUT/hashes.2" > "$OUT/hash.diff"; then
  echo "DETERMINISTIC: $(wc -l < "$OUT/hashes.1") artifacts identical across both runs"
else
  echo "NON-DETERMINISTIC: $(grep -c '^[+-][^+-]' "$OUT/hash.diff") differing lines; see $OUT/hash.diff"
fi
