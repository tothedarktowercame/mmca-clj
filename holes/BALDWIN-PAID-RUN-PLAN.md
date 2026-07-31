# Baldwin paid-run plan

Status: prepared, locally gated, **no worker provisioned**.

This plan replaces the overnight watchdog and the ambiguous `--pin-plasticity`
arms. Its purpose is to buy one auditable mechanistic battery, not another cost
sweep.

## Question and modes

`hold-only` fixes, for every individual and generation:

- `gamma = 1`;
- `update-prob = 1`;
- every mask bit is live.

The inherited rule field and per-cell hold bits remain evolvable. Holding is
therefore the only route by which measured dependence can fall.

`static-search` fixes every hold bit as well. Only the inherited rule field can
evolve. This tests whether the evolutionary search has any usable gradient
toward a functional fixed field.

The runner asserts these contracts over every genome at generation entry and
after breeding. A violation aborts before another generation is evaluated.

## Before creating a worker

From the committed, clean checkout:

```sh
cd /home/joe/code/mmca-clj
scripts/baldwin_cloud_preflight.sh
clj-kondo --lint src/mmca/baldwin_selection.clj src/mmca/baldwin_artifacts.clj \
  scripts/baldwin_selection.clj scripts/assimilation_probe.clj \
  scripts/assimilation_map.clj scripts/assimilation_path.clj \
  src/mmca/hinton_nowlan.clj scripts/hinton_nowlan_positive.clj
clojure -M:test -m mmca.test-runner
emacs -Q --batch -l ../futon4/dev/check-parens.el \
  --eval '(arxana-check-parens-cli)' -- --no-defaults \
  src/mmca/baldwin_selection.clj scripts/assimilation_probe.clj \
  scripts/assimilation_map.clj scripts/assimilation_path.clj
```

Provision only after these pass. Record the returned instance ID and IP
directly; do not edit them into a script.

## Start order

Choose a unique run ID and a full committed revision:

```sh
RUN_ID=baldwin-hold-only-YYYYMMDD-HHMMSS
REVISION=$(git rev-parse HEAD)
EVOLUTION_SEED=20260730
```

On the worker, start exactly one sequential battery:

```sh
cd /root/mmca-clj
nohup scripts/baldwin_remote_battery.sh \
  "$RUN_ID" "$REVISION" "$EVOLUTION_SEED" \
  >"/root/${RUN_ID}.launcher.log" 2>&1 &
```

Immediately on the local machine, generate the immutable artifact allow-list
and start the supervisor:

```sh
scripts/baldwin_artifact_spec.sh "$RUN_ID" >"/tmp/${RUN_ID}.artifacts.tsv"
systemd-run --user --unit "baldwin-supervisor-${RUN_ID}" \
  scripts/baldwin_box_supervisor.sh \
  INSTANCE_ID IP "$RUN_ID" 240 \
  "/tmp/${RUN_ID}.artifacts.tsv" \
  /home/joe/code/mmca-clj/data/baldwin-runs
```

The 240-minute value is an outer emergency ceiling, not an expected duration.
After the production-shape canary supplies timing, reduce it if the projected
battery plus a conservative margin is smaller. Never lengthen it merely because
a run is slow without first inspecting progress and cost.

The supervisor:

1. confirms the instance through a direct HTTPS client;
2. confirms the unique remote start marker;
3. arms a separate systemd dead-man deletion;
4. accepts only the matching success marker;
5. copies an explicit artifact set into a fresh staging directory;
6. validates presence and minimum sizes and writes local checksums;
7. deletes through `linode-cli`;
8. independently requires an API `GET` to return 404.

Authentication errors, missing executables, empty output, and HTTP errors are
failures, never evidence of absence.

## Sequential experimental battery

The worker runs one JVM at a time because one `pmap` evaluation already occupies
the machine.

1. Source gates and a cheap planted-target Hinton--Nowlan positive control.
2. One revision-bound expensive preflight.
3. One-generation production-shape canary.
4. Cheap empirical mutation-only null using the production breeding operator.
5. `hold-only`, `c = 0.05`.
6. Treatment-separation checks over every post-warm-up generation:
   `0 vs 0.05` and `0.05 vs 2`.
7. `hold-only`, `c = 2`, only if the observed `c05` populations prove that the
   stronger cost can change selection ordering.
8. `static-search`, `c = 0`.
9. Current-allele one-locus probe on the best `c05` genome.
10. Greedy current-allele assimilation path, capped at 12 holds.
11. Full-precision all-256-rule maps at the four least-damaging loci whose
    current inherited allele failed the probe.

Step 11 is deliberately a targeted positive search, not an exhaustive negative
claim. The map executable is chunkable over all 80 loci if its timing and the
remaining budget justify escalation. It has no low-precision rejection screen.

## Stop rules

Stop and tear down if any of these occurs:

- source revision or clean-worktree check fails;
- preflight certificate does not exactly match revision, evaluation seeds,
  sites, and protocol;
- a mode assertion fails for any genome;
- artifact shape validation fails;
- treatment rankings remain equivalent throughout the observed trajectory;
- any arm reaches its 75-minute timeout;
- the global dead-man deadline fires.

Do not add arms interactively to “use up” an already-running box. Bank the
planned artifacts and delete it.

## Interpretation fixed before the run

Evidence of assimilation requires:

- held fraction above the empirical hold-only mutation null;
- raw band performance maintained;
- dependence declining;
- a score-preserving accessible path, or a functional held endpoint.

Holding rising while raw function falls is loss of function. An inert cost is
not a negative result. A useful alternative rule in the targeted maps means the
representation permits assimilation but mutation/search did not install the
allele. No useful rule in four targeted loci is only a targeted negative; it
does not license a claim about all 80 loci.

The first evolution seed is a pilot. Any headline positive or negative must be
replicated with preregistered additional evolution seeds; duplicating cost arms
under one hard-coded seed is not replication.
