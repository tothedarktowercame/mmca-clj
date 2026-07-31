#!/bin/bash
# Unattended completion for the Baldwin knob arms.
#
# Polls the box for the completion marker, banks every artefact locally, then
# DESTROYS the box. A hard timeout guarantees teardown even if the run hangs --
# an idle billing box is the failure this exists to prevent.
#
# Deletion is done from here, not on the box: putting an API token on a remote
# host to let it delete itself is a worse trade than a local watchdog.

IP=45.56.109.37
LINODE=101801986
DEST=/home/joe/code/mmca-clj/data/baldwin
LOG=/home/joe/code/mmca-clj/data/baldwin/WATCHDOG.log
MAX_MIN=260                      # hard ceiling: bank and destroy regardless

mkdir -p "$DEST"
say () { echo "$(date -u +%H:%M:%SZ) $*" >> "$LOG"; }

# PREFLIGHT -- resolve every external tool NOW, not hours from now when the box is
# billing and the only remaining job is to kill it. A systemd --user unit gets a
# minimal PATH without ~/.local/bin, so linode-cli resolved interactively and not
# here; that was discovered only at teardown, and the delete never ran.
CLI=""
for cand in "$(command -v linode-cli 2>/dev/null)" /home/joe/.local/bin/linode-cli \
            /usr/local/bin/linode-cli "$HOME/.local/bin/linode-cli"; do
  [ -n "$cand" ] && [ -x "$cand" ] && { CLI="$cand"; break; }
done
if [ -z "$CLI" ]; then
  say "FATAL: linode-cli not found in this environment (PATH=$PATH)"
  say "       refusing to start -- a watchdog that cannot destroy the box is worse"
  say "       than none, because it looks like teardown is handled."
  exit 1
fi
# Prove it actually WORKS, not merely that the file exists: a present-but-unauthorised
# CLI fails identically to a missing one at the moment it matters.
if ! timeout 60 "$CLI" linodes list --text --format=id >/dev/null 2>&1; then
  say "FATAL: $CLI present but cannot list linodes (auth or network). Refusing to start."
  exit 1
fi
say "preflight ok: $CLI can list linodes"
ssh_box () { timeout 60 ssh -o BatchMode=yes -o StrictHostKeyChecking=no \
             -o ConnectTimeout=15 root@$IP "$@" 2>/dev/null; }

say "watchdog start; polling for run2_done (max ${MAX_MIN}min)"
done_seen=""
for i in $(seq 1 $((MAX_MIN / 2))); do
  # BOTH runs must finish: the knob arms and the pinned arms launched alongside
  # them. Tearing down on run2_done alone would kill the pinned arms mid-flight.
  m2=$(ssh_box 'cat /root/run2_done 2>/dev/null')
  m3=$(ssh_box 'cat /root/run3_done 2>/dev/null')
  if [ -n "$m2" ] && [ -n "$m3" ]; then
    done_seen=yes; say "both markers seen after $((i*2))min"; break
  fi
  sleep 120
done
[ -z "$done_seen" ] && say "TIMEOUT at ${MAX_MIN}min -- banking whatever exists and destroying anyway"

say "banking artefacts"
timeout 600 scp -q -o BatchMode=yes -o StrictHostKeyChecking=no \
  "root@$IP:/root/mmca-clj/data_*.tsv" "$DEST/" 2>/dev/null && say "  data ok"
timeout 900 scp -q -o BatchMode=yes -o StrictHostKeyChecking=no \
  "root@$IP:/root/mmca-clj/rec_*.edn" "$DEST/" 2>/dev/null && say "  records ok"
timeout 300 scp -q -o BatchMode=yes -o StrictHostKeyChecking=no \
  "root@$IP:/root/mmca-clj/probe.tsv" "root@$IP:/root/run2.log" \
  "root@$IP:/root/mmca-clj/run2.err" "$DEST/" 2>/dev/null && say "  probe/logs ok"
say "banked: $(ls -1 "$DEST" | wc -l) files"

say "destroying linode $LINODE"
timeout 180 "$CLI" linodes delete "$LINODE" >> "$LOG" 2>&1

# VERIFY FAIL-CLOSED. The previous version piped a missing binary into grep -c, got
# 0, and reported success -- the check shared a failure mode with the thing it was
# checking. Distinguish three outcomes, and treat "cannot tell" as failure.
sleep 8
listing=$(timeout 90 "$CLI" linodes list --text --format=id 2>/dev/null)
if [ -z "$listing" ]; then
  say "!! CANNOT VERIFY: linode list returned nothing. $LINODE MAY STILL BE BILLING."
  say "   check by hand: $CLI linodes list"
elif echo "$listing" | grep -qx "$LINODE"; then
  say "!! DELETE FAILED: linode $LINODE is STILL PRESENT and billing. Retrying once."
  timeout 180 "$CLI" linodes delete "$LINODE" >> "$LOG" 2>&1
  sleep 8
  if timeout 90 "$CLI" linodes list --text --format=id 2>/dev/null | grep -qx "$LINODE"; then
    say "!! RETRY FAILED -- linode $LINODE still present. MANUAL ACTION REQUIRED."
  else
    say "DESTROYED on retry, confirmed absent from a non-empty listing"
  fi
else
  say "DESTROYED and confirmed absent from a non-empty listing of $(echo "$listing" | wc -l) linodes"
fi
say "watchdog done"
