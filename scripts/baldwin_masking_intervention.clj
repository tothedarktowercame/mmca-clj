(require '[mmca.baldwin-masking-intervention :as masking])
(require '[mmca.baldwin-selection :as selection])

(let [[record-path map-path mode output-directory] *command-line-args*]
  (when-not (and record-path map-path mode output-directory)
    (throw (ex-info
            "usage: baldwin_masking_intervention.clj RECORD MAP pilot|confirmation OUTPUT-DIR"
            {})))
  (let [seeds (case mode
                "pilot" masking/pilot-seeds
                "confirmation" masking/confirmation-seeds
                (throw (ex-info "unknown run mode" {:mode mode})))
        output (java.io.File. output-directory)
        _ (.mkdirs output)
        provenance (masking/validate-discovery! map-path)
        genome (selection/best-genome-from-record record-path)
        rows (masking/run-panel genome seeds)
        result (masking/analyze rows seeds)]
    (masking/write-edn-lines! (java.io.File. output "raw.edn") rows)
    (masking/write-edn! (java.io.File. output "result.edn") result)
    (masking/write-edn!
     (java.io.File. output "manifest.edn")
     (merge {:kind :baldwin-masking-intervention-manifest
             :schema 1
             :mode (keyword mode)
             :lean-revision masking/lean-revision
             :record-sha256 (masking/sha256 record-path)
             :map-sha256 (masking/sha256 map-path)
             :capacity-cost-basis-points masking/capacity-cost-basis-points
             :evaluation-seeds seeds
             :evaluation-sites masking/evaluation-sites
             :arms masking/arms}
            provenance))
    (println (pr-str (select-keys result [:kind :raw-units-per-arm :outcome])))))
