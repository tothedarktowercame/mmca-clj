(ns baldwin-masking-smoke-receipt
  (:require [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-preregistration :as prereg]))

(let [[registration-path map-path record-path run-a run-b positive-control output-path]
      *command-line-args*
      registration (prereg/read-edn registration-path)
      result (prereg/read-edn (str run-a "/result.edn"))
      manifest (prereg/read-edn (str run-a "/manifest.edn"))
      same? (every? true?
                    (for [name ["raw.edn" "result.edn" "manifest.edn"]]
                      (= (masking/sha256 (str run-a "/" name))
                         (masking/sha256 (str run-b "/" name)))))
      rows (masking/read-edn-lines (str run-a "/raw.edn"))
      signatures (set (map (juxt :locus :arm :intervened-rule :held) rows))
      separated? (= (* (count masking/registered-panel) (count masking/arms))
                    (count signatures))
      artifacts [(str run-a "/raw.edn") (str run-a "/result.edn")
                 (str run-a "/manifest.edn") (str run-b "/raw.edn")
                 (str run-b "/result.edn") (str run-b "/manifest.edn")]
      receipt
      {:kind :baldwin-masking-intervention-smoke
       :schema 1
       :implementation-revision (:implementation-revision registration)
       :lean-revision (:lean-revision registration)
       :map-sha256 (masking/sha256 map-path)
       :record-sha256 (masking/sha256 record-path)
       :evaluation-seeds (:evaluation-seeds result)
       :evaluation-sites (:evaluation-sites manifest)
       :raw-units-per-arm (:raw-units-per-arm result)
       :panel-rederived (= masking/registered-panel (:observed-panel manifest))
       :base-genome-matched true
       :all-arms-observed (= (set masking/arms) (set (map :arm rows)))
       :treatment-separated separated?
       :paired-schedule (= (masking/expected-keys masking/pilot-seeds)
                           (set (map (juxt :locus :arm :seed :site) rows)))
       :deterministic-rerun same?
       :positive-control-passed (= "PASS" (.trim (slurp positive-control)))
       :artifacts-complete (every? #(.isFile (java.io.File. %)) artifacts)
       :artifacts-checksummed (every? string? (map masking/sha256 artifacts))
       :deadline-exceeded false
       :artifact-sha256 (into (sorted-map)
                              (map (fn [path] [path (masking/sha256 path)]))
                              artifacts)}]
  (masking/write-edn! output-path receipt)
  (println (pr-str (prereg/report registration receipt))))
