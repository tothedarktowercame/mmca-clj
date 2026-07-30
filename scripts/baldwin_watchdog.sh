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
timeout 180 linode-cli linodes delete "$LINODE" >> "$LOG" 2>&1
sleep 8
remaining=$(timeout 90 linode-cli linodes list --text --format=id 2>/dev/null | grep -c "^${LINODE}$")
if [ "$remaining" = "0" ]; then
  say "DESTROYED and confirmed absent"
else
  say "!! DELETE DID NOT CONFIRM -- linode $LINODE may still be billing; check manually"
fi
say "watchdog done"
