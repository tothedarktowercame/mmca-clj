(ns mmca.baldwin-artifacts
  "Validation and treatment-separation checks over banked experiment records."
  (:require [mmca.baldwin-selection :as selection]
            [mmca.baldwin-spec :as spec]))

(defn validate-run-data!
  [tsv-lines records expected-mode generations population-size]
  (let [manifest (first records)
        genomes (filterv #(nil? (:kind %)) records)
        decoded-genomes (mapv selection/decode-record-genome genomes)
        endpoints (filterv #(= :endpoint (:kind %)) records)
        expected-records (+ 1 (* generations (inc population-size)))]
    (when-not (= (inc generations) (count tsv-lines))
      (throw (ex-info "wrong TSV line count"
                      {:expected (inc generations) :actual (count tsv-lines)})))
    (when-not (= expected-records (count records))
      (throw (ex-info "wrong record count"
                      {:expected expected-records :actual (count records)})))
    (when-not (= :manifest (:kind manifest))
      (throw (ex-info "first record is not a manifest" {:first-record manifest})))
    (when-not (= expected-mode (get-in manifest [:configuration :mode]))
      (throw (ex-info "manifest mode mismatch"
                      {:expected expected-mode
                       :actual (get-in manifest [:configuration :mode])})))
    (when-not (= (* generations population-size) (count genomes))
      (throw (ex-info "wrong genome count" {:actual (count genomes)})))
    (when-not (= generations (count endpoints))
      (throw (ex-info "wrong endpoint count" {:actual (count endpoints)})))
    (selection/assert-mode! expected-mode decoded-genomes)
    {:valid? true :mode expected-mode :generations generations
     :population population-size :records (count records)
     :revision (:revision manifest)}))

(defn treatment-separation
  [records start-generation cost-a cost-b]
  (let [genomes (->> records
                     (filter #(and (nil? (:kind %))
                                   (>= (:gen %) start-generation)))
                     vec)
        by-generation (group-by :gen genomes)
        separating-generations
        (->> by-generation
             (keep (fn [[generation members]]
                     (when-not
                      (spec/ranking-equivalent?
                       (mapv #(- (:band %) (* cost-a (:dependence %))) members)
                       (mapv #(- (:band %) (* cost-b (:dependence %))) members))
                       generation)))
             sort
             vec)]
    (when (empty? genomes)
      (throw (ex-info "record has no genomes at or after requested generation"
                      {:start-generation start-generation})))
    {:start-generation start-generation
     :generations-examined (vec (sort (keys by-generation)))
     :cost-a cost-a :cost-b cost-b
     :separating-generations separating-generations
     :ranking-equivalent? (empty? separating-generations)}))
