(ns assimilation-path
  "Greedy test for a selectable, function-preserving path through current alleles.

   The one-locus probe found two individually selectable holds. This script asks
   the missing question: after taking the best available step, is another step
   still available, or do individually harmless loci interact to form a valley?"
  (:require [mmca.baldwin-selection :as bs]))

(defn evaluate [genome cost seeds sites]
  (let [reach (:mean (bs/reach genome seeds sites))
        band (bs/band-score reach)
        dependence (bs/plastic-dependence genome)]
    {:reach reach :band band :dependence dependence
     :fitness (- band (* cost dependence))}))

(defn hold-current [genome locus]
  (assoc genome :hold (assoc (:hold genome) locus true)))

(let [[record-path cost-s max-steps-s seeds-s sites-s preserve-s] *command-line-args*]
  (when-not record-path
    (throw (ex-info
            "usage: assimilation_path.clj RECORD [COST] [MAX-STEPS] [SEEDS] [SITES] [PRESERVE-FRACTION]"
            {:args *command-line-args*})))
  (let [cost (Double/parseDouble (or cost-s "0.05"))
        max-steps (Long/parseLong (or max-steps-s "80"))
        seed-count (Long/parseLong (or seeds-s "3"))
        site-count (Long/parseLong (or sites-s "10"))
        preserve (Double/parseDouble (or preserve-s "0.9"))
        seeds (range 1 (inc seed-count))
        sites (take site-count (range 0 bs/W (max 1 (quot bs/W site-count))))
        genome (bs/best-genome-from-record record-path)
        initial (evaluate genome cost seeds sites)
        initial-held (count (filter true? (:hold genome)))]
    (binding [*out* *err*]
      (println (pr-str {:kind :assimilation-path-manifest
                        :record record-path :cost cost :max-steps max-steps
                        :seeds (vec seeds) :sites (vec sites)
                        :preserve-fraction preserve :baseline initial})))
    (println "step\tlocus\trule\treach\tband\tdependence\tfitness\theld\tstatus")
    (println (format "0\t-1\t-1\t%.6f\t%.6f\t%.6f\t%.6f\t%d\tbaseline"
                     (:reach initial) (:band initial) (:dependence initial)
                     (:fitness initial) initial-held))
    (loop [step 1 genome genome current initial]
      (when (<= step max-steps)
        (let [unheld (remove #(nth (:hold genome) %) (range bs/W))
              evaluated (doall
                         (pmap (fn [locus]
                                 [locus (evaluate (hold-current genome locus)
                                                  cost seeds sites)])
                               unheld))
              acceptable (filter
                          (fn [[_ result]]
                            (and (>= (:band result) (* preserve (:band initial)))
                                 (>= (:fitness result) (:fitness current))))
                          evaluated)]
          (if-let [[locus result] (first (sort-by
                                         (fn [[i r]] [(- (:fitness r)) (- (:band r)) i])
                                         acceptable))]
            (let [next-genome (hold-current genome locus)
                  held (count (filter true? (:hold next-genome)))]
              (println (format "%d\t%d\t%d\t%.6f\t%.6f\t%.6f\t%.6f\t%d\tselected"
                               step locus (nth (:field genome) locus)
                               (:reach result) (:band result) (:dependence result)
                               (:fitness result) held))
              (flush)
              (recur (inc step) next-genome result))
            (binding [*out* *err*]
              (println (pr-str {:kind :assimilation-path-stop
                                :step step :reason :no-selectable-step
                                :held (count (filter true? (:hold genome)))
                                :remaining (count unheld)
                                :current current})))))))))
