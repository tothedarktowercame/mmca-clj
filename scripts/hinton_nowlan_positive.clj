(ns hinton-nowlan-positive
  (:require [mmca.hinton-nowlan :as hn]))

(let [[generations-s population-s mutation-rate-s seed-s] *command-line-args*
      config {:generations (Long/parseLong (or generations-s "100"))
              :population-size (Long/parseLong (or population-s "200"))
              :mutation-rate (Double/parseDouble (or mutation-rate-s "0.02"))
              :evolution-seed (Long/parseLong (or seed-s "20260730"))}
      trajectory (hn/run config)]
  (println "gen\tbest-score\tbest-plastic\tbest-function\tmean-plastic")
  (doseq [row trajectory]
    (println (format "%d\t%.9f\t%d\t%.1f\t%.6f"
                     (:generation row) (:best-score row) (:best-plastic row)
                     (:best-function row) (:mean-plastic row))))
  (when-not (hn/positive-control-passes? trajectory)
    (throw (ex-info "planted-target positive control did not assimilate"
                    {:config config :last (last trajectory)}))))
