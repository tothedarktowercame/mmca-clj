#!/bin/bash
# Emit the local allow-list consumed by baldwin_box_supervisor.sh.
set -Eeuo pipefail

if [[ $# -ne 1 || ! $1 =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "usage: $0 RUN_ID" >&2
  exit 64
fi

run_id=$1
root=/root/baldwin-runs/$run_id

for name in \
  battery.log clj-kondo.log tests.log positive_control.tsv \
  preflight.edn preflight.stdout preflight.stderr \
  canary.tsv canary.edn canary.manifest.edn canary.stderr canary.validation.edn \
  null.tsv null.manifest.edn \
  c05.tsv c05.edn c05.manifest.edn c05.stderr c05.validation.edn \
  c2.tsv c2.edn c2.manifest.edn c2.stderr c2.validation.edn \
  static.tsv static.edn static.manifest.edn static.stderr static.validation.edn \
  separation_c0_c05.edn separation_c05_c2.edn timings.tsv \
  probe.tsv probe.log path.tsv path.log map.tsv map.log map_loci.txt \
  CHECKSUMS.remote.sha256; do
  case "$name" in
    c05.edn|c2.edn|static.edn|map.tsv) min_bytes=1000 ;;
    *.tsv|*.edn|CHECKSUMS.remote.sha256) min_bytes=10 ;;
    *) min_bytes=0 ;;
  esac
  printf '%s/%s\t%s\t%s\n' "$root" "$name" "$name" "$min_bytes"
done
