(ns mmca.experiments.offset2-finite-size
  "Finite-size scan of the EDGE-OF-CHAOS operator offset+2 [2 3 4 5 6 7 0 1] --
  the operator the paper labels edge-of-chaos (~t100): sustained and structured,
  unlike the collapsing offset+4 that E2's default scanned. Tests the actual EoC
  claim via finite-size scaling: does the susceptibility GROW and its peak-q
  CONVERGE with system size L (the signature of a critical transition), or shrink
  with L (off-critical)? Reuses E2's scan machinery. Deterministic."
  (:require [mmca.experiments.control-param-scan :as e2]))

(def offset2-full-config
  {:writing [2 3 4 5 6 7 0 1]
   :seed-start 0 :seed-count 32
   :widths [30 60 120 240]
   :qs [0.0 0.025 0.05 0.075 0.1 0.125 0.15 0.2 0.3 0.4 0.5 0.75 1.0]
   :steps 300 :late-window 80 :collapse-window 8})

(defn -main [& _]
  (print (e2/markdown-report (e2/scan offset2-full-config))))
