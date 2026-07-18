;; Clojure resolves the namespace segment `codex-4` as the resource
;; `codex_4.clj`. The E4 handoff requires the implementation artifact itself to
;; be named `codex-4.clj`, so this loader preserves both that artifact contract
;; and ordinary `clojure -M -m mmca.experiments.codex-4` invocation.
(load "/mmca/experiments/codex-4")
