#!/bin/bash
set -Eeuo pipefail
if [[ $# -ne 1 || ! $1 =~ ^[A-Za-z0-9._-]+$ ]]; then exit 64; fi
run_id=$1
root=/root/baldwin-runs/$run_id
names=(battery.log preregistration.validation.edn clj-kondo.log tests.log
  positive-control.tsv preflight.edn preflight.stdout preflight.stderr timings.tsv
  treatment-separation.edn result.edn analysis.stdout CHECKSUMS.remote.sha256)
for arm in neutral independent-variable coupled-variable independent-fixed coupled-fixed; do
  names+=("$arm.tsv" "$arm.edn" "$arm.manifest.edn" "$arm.stderr" "$arm.validation.edn")
done
for name in "${names[@]}"; do
  case $name in
    *.edn|*.tsv|CHECKSUMS.remote.sha256) min=10 ;;
    *) min=0 ;;
  esac
  printf '%s/%s\t%s\t%s\n' "$root" "$name" "$name" "$min"
done
