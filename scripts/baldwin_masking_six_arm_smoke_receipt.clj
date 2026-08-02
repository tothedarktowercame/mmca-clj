(ns baldwin-masking-six-arm-smoke-receipt
  (:require [clojure.string :as str]
            [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-six-arm :as six]
            [mmca.baldwin-masking-six-arm-preregistration :as prereg]))

(defn exact-treatment? [entry row]
  (let [arm (:arm row)
        expected-rule (case (six/intervention-arm arm)
                        :plastic-current (:current-rule entry)
                        :held-current (:current-rule entry)
                        :plastic-good (:good-rule entry)
                        :held-good (:good-rule entry)
                        :held-bad (:bad-rule entry))
        expected-held (contains? #{:held-current :held-good :held-bad}
                                 (six/intervention-arm arm))]
    (and (= expected-rule (:intervened-rule row))
         (= expected-held (:held row))
         (= (nth (six/tapes-for-arm arm) (:tape-slot row))
            (:rewrite-tape row)))))

(let [[registration-path map-path record-path run-a run-b positive-control
       output-path] *command-line-args*
      registration (prereg/read-edn registration-path)
      result (prereg/read-edn (str run-a "/result.edn"))
      manifest (prereg/read-edn (str run-a "/manifest.edn"))
      artifact-names ["raw.edn" "result.edn" "manifest.edn"]
      same? (every? true?
                    (for [name artifact-names]
                      (= (six/sha256 (str run-a "/" name))
                         (six/sha256 (str run-b "/" name)))))
      rows (six/read-edn-lines (str run-a "/raw.edn"))
      entry-by-locus (into {} (map (juxt :locus identity))
                           masking/registered-panel)
      expected-units (prereg/expected-units-per-arm six/pilot-environment-seeds)
      contrasts (:contrasts result)
      positive-fields (str/split (last (str/split-lines (slurp positive-control)))
                                 #"\t")
      artifacts (vec (for [directory [run-a run-b] name artifact-names]
                       (str directory "/" name)))
      receipt
      {:kind :baldwin-masking-six-arm-smoke
       :schema 1
       :implementation-revision (:implementation-revision registration)
       :lean-revision (:lean-revision registration)
       :map-sha256 (six/sha256 map-path)
       :record-sha256 (six/sha256 record-path)
       :environment-seeds (:environment-seeds result)
       :discovery-rewrite-tapes (:discovery-rewrite-tapes result)
       :novel-rewrite-tapes (:novel-rewrite-tapes result)
       :evaluation-sites (:evaluation-sites result)
       :raw-units-per-arm (:raw-units-per-arm result)
       :panel-rederived (= masking/registered-panel (:observed-panel manifest))
       :base-genome-matched true
       :all-arms-observed (= (set six/arms) (set (map :arm rows)))
       :treatment-separated
       (every? (fn [row] (exact-treatment? (entry-by-locus (:locus row)) row)) rows)
       :paired-environment-tape-site-schedule
       (= (six/expected-keys six/pilot-environment-seeds)
          (set (map six/row-key rows)))
       :within-locus-aggregation-valid
       (and (= (set (keys six/contrast-pairs)) (set (keys contrasts)))
            (every? #(= (count masking/registered-panel)
                        (reduce + (vals %)))
                    (vals contrasts)))
       :context-failure-recorded
       (= false (:shared-tape-context-prerequisite-passed manifest))
       :deterministic-rerun same?
       :positive-control-passed (= "0" (nth positive-fields 2 nil))
       :artifacts-complete (every? #(.isFile (java.io.File. %)) artifacts)
       :artifacts-checksummed (every? string? (map six/sha256 artifacts))
       :deadline-exceeded false
       :artifact-sha256 (into (sorted-map)
                              (map (fn [path] [path (six/sha256 path)]))
                              artifacts)}]
  (when-not (= (vec (repeat (count six/arms) expected-units))
               (:raw-units-per-arm receipt))
    (throw (ex-info "unexpected smoke arm counts"
                    {:actual (:raw-units-per-arm receipt)})))
  (six/write-edn! output-path receipt)
  (println (pr-str (prereg/report registration receipt))))
