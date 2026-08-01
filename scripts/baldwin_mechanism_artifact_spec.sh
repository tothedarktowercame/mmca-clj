#!/bin/bash
# Emit the exact success allow-list; on failure the supervisor banks any subset.
set -Eeuo pipefail

if [[ $# -ne 1 || ! $1 =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "usage: $0 RUN_ID" >&2
  exit 64
fi

run_id=$1
root=/root/baldwin-runs/$run_id

emit() {
  printf '%s/%s\t%s\t%s\n' "$root" "$1" "$1" "$2"
}

emit battery.log 1
emit clj-kondo.log 0
emit tests.log 10
emit positive-control.tsv 10
emit target-stationarity.edn 1000
emit target-stationarity.stdout 100
emit target-stationarity.stderr 0
emit context-stationarity.edn 1000
emit context-stationarity.stdout 100
emit context-stationarity.stderr 0
emit allele-sensitivity.edn 1000
emit allele-sensitivity.stdout 100
emit allele-sensitivity.stderr 0
emit timings.tsv 10
emit assimilation-map.tsv 100000
for start in $(seq 0 5 79); do
  end=$((start + 5))
  (( end <= 80 )) || end=80
  label=$(printf 'assimilation-map-%02d-%02d' "$start" "$end")
  emit "${label}.tsv" 1000
  emit "${label}.stderr" 0
done
emit manifest.txt 100
emit CHECKSUMS.remote.sha256 100
