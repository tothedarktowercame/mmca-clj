;; FAST assimilation probe -- codex-1's step 1, which is decisive on its own if it
;; fires. Hold each locus at ITS CURRENT inherited rule and measure both raw function
;; and cost-adjusted fitness. 80 evaluations, not 20480.
;;
;;   any locus selectable (fitness does not fall)  -> assimilation was AVAILABLE and
;;                                                    SEARCH failed. Decisive.
;;   none selectable, some function-preserving     -> the cost term is too weak;
;;                                                    Lean fitness_step_iff_loss_le_saving
;;                                                    says raise c above loss/saving.
;;   none function-preserving                      -> the current alleles are not
;;                                                    prepared; escalate to all 256.

(require '[mmca.core :as c] '[clojure.edn] '[clojure.string])
(load-file "scripts/baldwin_selection_lib.clj")

(def SEEDS [1 2 3])
(def SITES (range 0 80 8))

(defn best-genome [rec-path]
  (let [recs (->> (slurp rec-path) clojure.string/split-lines
                  (remove clojure.string/blank?)
                  (map clojure.edn/read-string) (remove :kind))]
    (let [b (->> recs (sort-by #(or (:band %) -1) >) first)]
      {:gamma (:gamma b) :update-prob (:update-prob b)
       :field (vec (:field b))
       :mask (mapv #(= 1 %) (:mask b))
       :hold (mapv #(= 1 %) (:hold b))})))

(let [[rec-path cost-s] *command-line-args*
      cost (Double/parseDouble (or cost-s "0.05"))
      g0 (best-genome rec-path)
      W* (count (:field g0))
      ev (fn [g] (let [b (band-score (:mean (reach g SEEDS SITES)))
                       d (plastic-dependence g)]
                   {:band b :dep d :fit (- b (* cost d))}))
      base (ev g0)]
  (binding [*out* *err*]
    (println (format "  baseline band=%.4f dep=%.4f fitness=%.4f  (held %d/%d, c=%.2f)"
                     (:band base) (:dep base) (:fit base)
                     (count (filter true? (:hold g0))) W* cost)))
  (println "locus\trule\tband\tdep\tfitness\tbase-band\tbase-fit\tfn-preserved\tselectable")
  (doseq [[i r] (doall
                 (pmap (fn [i]
                         [i (ev (assoc g0 :hold (assoc (:hold g0) i true)))])
                       (remove #(nth (:hold g0) %) (range W*))))]
    (println (format "%d\t%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%s\t%s"
                     i (nth (:field g0) i) (:band r) (:dep r) (:fit r)
                     (:band base) (:fit base)
                     (>= (:band r) (* 0.9 (:band base)))
                     (>= (:fit r) (:fit base))))))
