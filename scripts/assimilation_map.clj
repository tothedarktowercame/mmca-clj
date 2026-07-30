;; EXHAUSTIVE ONE-LOCUS ASSIMILATION MAP (codex-1's proposal).
;;
;; For the best evolved genome, hold EACH locus at EACH of the 256 rules and measure
;; function. This separates three obstacles that a selection run cannot tell apart:
;;
;;   REPRESENTATION -- no (locus, rule) held version maintains function at all, so
;;                     the representation cannot express even ONE assimilated locus.
;;   ACCESSIBILITY  -- such pairs exist but are far from the current allele in the
;;                     mutation operator's metric, so no lineage could reach them.
;;   SEARCH         -- they exist and are near, and selection simply is not finding
;;                     them.
;;
;; Two-stage by cost: a cheap screen over all 20480 pairs, then a full-precision
;; confirmation of the survivors. A single-precision pass over everything would be
;; about eight hours on 32 cores; this is under two.

(require '[mmca.core :as c] '[clojure.edn] '[clojure.string])
(load-file "scripts/baldwin_selection_lib.clj")

(def SCREEN-SEEDS [1])
(def SCREEN-SITES (range 0 80 20))
(def FULL-SEEDS [1 2 3])
(def FULL-SITES (range 0 80 8))

(defn best-genome [rec-path]
  (let [recs (->> (slurp rec-path) clojure.string/split-lines
                  (remove clojure.string/blank?)
                  (map clojure.edn/read-string) (remove :kind))
        b (->> recs (sort-by #(or (:band %) -1) >) first)]
    {:gamma (:gamma b) :update-prob (:update-prob b)
     :field (vec (:field b))
     :mask (mapv #(= 1 %) (:mask b))
     :hold (mapv #(= 1 %) (:hold b))}))

(defn hold-one [g i rule]
  (-> g (assoc :field (assoc (:field g) i rule))
        (assoc :hold (assoc (:hold g) i true))))

(defn band-of [g seeds sites]
  (band-score (:mean (reach g seeds sites))))

(let [[rec-path] *command-line-args*
      g0 (best-genome rec-path)
      base-screen (band-of g0 SCREEN-SEEDS SCREEN-SITES)
      base-full (band-of g0 FULL-SEEDS FULL-SITES)
      W* (count (:field g0))]
  (binding [*out* *err*]
    (println (format "  baseline: screen %.4f  full %.4f  (held %d/%d)"
                     base-screen base-full (count (filter true? (:hold g0))) W*)))
  (println "stage\tlocus\trule\tcurrent\tband\tbase")
  ;; stage 1: cheap screen over every (locus, rule)
  (let [screened (doall
                  (pmap (fn [[i r]]
                          [i r (band-of (hold-one g0 i r) SCREEN-SEEDS SCREEN-SITES)])
                        (for [i (range W*) r (range 256)] [i r])))]
    (doseq [[i r b] screened]
      (println (format "screen\t%d\t%d\t%d\t%.4f\t%.4f" i r (nth (:field g0) i) b base-screen)))
    ;; stage 2: confirm everything that survived the screen at full precision
    (let [survivors (filter (fn [[_ _ b]] (>= b (* 0.9 base-screen))) screened)]
      (binding [*out* *err*]
        (println (format "  screen kept %d of %d" (count survivors) (count screened))))
      (doseq [[i r b] (doall (pmap (fn [[i r _]]
                                     [i r (band-of (hold-one g0 i r) FULL-SEEDS FULL-SITES)])
                                   survivors))]
        (println (format "full\t%d\t%d\t%d\t%.4f\t%.4f" i r (nth (:field g0) i) b base-full))))))
