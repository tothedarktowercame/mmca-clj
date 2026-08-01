# Baldwin mechanism diagnostic box plan

Status: **locally smoke-tested, not yet provisioned**. The one-seed/one-site
allele probe completed with 640 rows and a checksum-valid archive at
`data/baldwin-diagnostics/allele-sensitivity-smoke-327a3bd/`.

This is one paid box carrying three measurements, not three independent box
runs. They share one immutable evolved-population record and one source
revision, and execute sequentially because each evaluator already parallelises
over the machine.

The paid-battery decision is formally registered in
`DarkTower/BaldwinMechanismBatteryPreregistration.lean`. In particular,
"content response" is not inferred from mutation reachability: each allele is
tested by a two-sided exact paired sign test, ties excluded, with Bonferroni
familywise correction over the fixed family of 640 possible probes. At the
production 24 paired units, 22 concordant signs pass and 21 do not.

1. Reproduce the preregistered four-cell stationarity grid and its exact fully
   fixed apparatus control.
2. Reproduce the 16-context diagnostic.
3. Measure inherited-allele sensitivity at every unheld locus with eight
   preregistered probe rules, paired on 3 seeds by 8 sites.
4. Complete the full 80-locus by 256-rule assimilation map at 3 seeds by 10
   sites, checkpointed in sixteen five-locus chunks.

There are deliberately no evolution arms. Stage A found neither cell nor
context stationarity, so another search would spend compute before establishing
that the inherited coordinate has a gradient or a useful static endpoint.

The smoke produced 57 positive paired differences, no negative differences and
583 ties, but its sole baseline pair had reach zero. It validates execution and
shows that inherited alleles are not *universally* inert; it is not an effect
estimate and cannot choose a scientific branch. That choice remains reserved
for the preregistered production panel.

## Launch binding

Before creating a worker, record:

- a clean full `mmca-clj` revision containing the battery;
- the built, axiom-audited
  `DarkTower/BaldwinMechanismBatteryPreregistration.lean` and its SHA-256;
- the SHA-256 of the recovered guidance `learning-evolution.edn` input;
- a unique run id;
- a run-specific `linodes:read_write` token whose expiry exceeds the hard
  dead-man deadline by one hour.

Copy the input record outside the worker checkout, verify its checksum there,
then start:

```sh
nohup /root/mmca-clj/scripts/baldwin_mechanism_remote_battery.sh \
  "$RUN_ID" "$REVISION" /root/baldwin-input/learning-evolution.edn \
  "$INPUT_SHA256" \
  /root/baldwin-input/BaldwinMechanismBatteryPreregistration.lean \
  "$REGISTRATION_SHA256" >"/root/${RUN_ID}.launcher.log" 2>&1 &
```

Generate the exact success allow-list locally and start the existing Chicago
supervisor plus local watcher immediately:

```sh
scripts/baldwin_mechanism_artifact_spec.sh "$RUN_ID" \
  >"/tmp/${RUN_ID}.artifacts.tsv"

systemd-run --user --unit "baldwin-supervisor-${RUN_ID}" \
  scripts/baldwin_box_supervisor.sh \
  INSTANCE_ID IP "$RUN_ID" 720 \
  "/tmp/${RUN_ID}.artifacts.tsv" data/baldwin-runs

systemd-run --user --unit "baldwin-local-watcher-${RUN_ID}" \
  scripts/baldwin_local_watcher.sh \
  linode-chicago "$RUN_ID" INSTANCE_ID 720 data/baldwin-runs
```

The 720-minute limit is a hard outer ceiling, not an expected runtime. On any
error, available checkpoint chunks and logs are banked, the worker is retained
for bounded inspection, and the independently armed Chicago dead-man remains
active. A successful run is checksum-verified, banked, and deleted immediately.

## Fixed interpretation

- If allele perturbations have no consistent paired effect but useful map
  endpoints exist, inherited alleles are empirically neutral under rewriting;
  the next experiment is usage cost crossed with prepare-then-fix.
- If allele effects and useful endpoints both exist, the problem is search-path
  supply; prepare-then-fix can be tested without changing the fitness function.
- If neither exists, no assimilation experiment is admitted on the current
  representation. The result is a mechanistic boundary, not a failed search.
- If an inherited effect exists but the map has no useful held endpoints, test
  guidance only; do not claim an assimilation path.

All claims use paired `(seed, site)` deltas. Arm means and single spacetime
pictures are descriptive only.
