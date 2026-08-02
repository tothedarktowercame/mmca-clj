# Masking smoke status

Status: **superseded; not launch-authorizing**.

The first full five-arm pilot repetition completed with empty stderr. The second
repetition was terminated during evaluation after the design review identified a
missing sixth arm: `held-good` evaluated on a rewrite tape distinct from the tape
used to derive the endpoint map. Without that arm, endpoint generalization is
confounded with tape identity.

The registration is now marked
`:superseded-by-shared-tape-six-arm-redesign`, and the executable launch validator
requires `:implementation-status :smoke-passed`. These partial artifacts must not
be used to authorize a paid run.

The next sequence is:

1. re-run context-indexed stationarity under the shared rewrite tape;
2. register and smoke a six-arm masking intervention including the cross-tape
   held-good arm;
3. consider endpoint-directed evolution only after those results.

Neither the earlier pilot seeds nor the reserved confirmation seeds were consumed:
this local smoke used evaluation seeds `901`, `902`, and `903` only.
