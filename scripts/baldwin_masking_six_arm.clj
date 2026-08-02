(ns baldwin-masking-six-arm
  (:require [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-six-arm :as six]
            [mmca.baldwin-selection :as selection]))

(let [[record-path map-path mode output-directory] *command-line-args*]
  (when-not (and record-path map-path mode output-directory)
    (throw (ex-info
            "usage: baldwin_masking_six_arm.clj RECORD MAP pilot|confirmation OUTPUT-DIR"
            {})))
  (let [environment-seeds
        (case mode
          "pilot" six/pilot-environment-seeds
          "confirmation" six/confirmation-environment-seeds
          (throw (ex-info "unknown run mode" {:mode mode})))
        output (java.io.File. output-directory)
        _ (.mkdirs output)
        provenance (masking/validate-discovery! map-path)
        genome (selection/best-genome-from-record record-path)
        rows (six/run-panel genome environment-seeds)
        result (six/analyze rows environment-seeds)]
    (six/write-edn-lines! (java.io.File. output "raw.edn") rows)
    (six/write-edn! (java.io.File. output "result.edn") result)
    (six/write-edn!
     (java.io.File. output "manifest.edn")
     (merge {:kind :baldwin-masking-six-arm-manifest
             :schema 1
             :mode (keyword mode)
             :lean-revision six/lean-revision
             :record-sha256 (six/sha256 record-path)
             :map-sha256 (six/sha256 map-path)
             :capacity-cost-basis-points masking/capacity-cost-basis-points
             :environment-seeds environment-seeds
             :discovery-rewrite-tapes six/discovery-rewrite-tapes
             :novel-rewrite-tapes six/novel-rewrite-tapes
             :evaluation-sites masking/evaluation-sites
             :arms six/arms
             :shared-tape-context-prerequisite-passed false}
            provenance))
    (println (pr-str (select-keys result [:kind :raw-units-per-arm :outcome])))))
