;; Read a recorded run and decide, via mmca.baldwin-spec, whether any lineage in it
;; constitutes a Lean BaldwinWitness -- and if not, WHICH condition fails.
;;
;; This is the point of recording complete genomes and ancestry: a TSV of means
;; cannot express an accessible path, raw function at every step, declining
;; dependence, or a functional static endpoint, so a run could not certify anything
;; even if assimilation occurred.

(require '[mmca.baldwin-spec :as spec] '[clojure.edn :as edn])

(def recs (->> (slurp (first *command-line-args*))
               clojure.string/split-lines
               (remove clojure.string/blank?)
               (map edn/read-string)))

(def endpoints (into {} (for [r recs :when (= :endpoint (:kind r))] [(:gen r) (:held-reach r)])))
(def inds (remove :kind recs))
(def by-id (into {} (map (juxt :id identity) inds)))

(defn lineage
  "Walk parent links back from an individual, giving the ancestral chain in order."
  [ind]
  (reverse (take-while some? (iterate #(some-> (:parent %) by-id) ind))))

  (let [threshold (Double/parseDouble (or (second *command-line-args*) "10.0"))
      best (->> inds (sort-by :score >) first)
      chain (lineage best)
      traj (mapv (fn [r] {:genome {:field (:field r)
                                   :hold (mapv #(= 1 %) (:hold r))}
                          :performance (:reach r)     ; RAW function, not cost-adjusted
                          :dependence (:dependence r)})
                 chain)
      ;; Lean inheritedFunction uses the recorded held-rule evaluation, not a guess
      held (get endpoints (:gen (last chain)))
      accessible? (constantly true)                    ; every step is a recorded transition
      traj' (if (seq traj)
              (assoc-in traj [(dec (count traj)) :inherited-performance] held)
              traj)
      fails (spec/witness-failures traj' threshold accessible?)]
  (println (format "  lineage length: %d  (best id %s, gen %s)" (count chain) (:id best) (:gen best)))
  (println (format "  raw reach along lineage: %s" (mapv #(format "%.3f" (:performance %)) traj')))
  (println (format "  dependence along lineage: %s" (mapv #(format "%.3f" (:dependence %)) traj)))
  (println (format "  held-rule endpoint reach: %s" held))
  (if (empty? fails)
    (println "  WITNESS: this lineage certifies a Baldwin claim")
    (println "  NOT A WITNESS. Failing conditions:" fails)))
