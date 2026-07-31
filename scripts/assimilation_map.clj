(ns assimilation-map
  "Full-precision, resumable one-locus assimilation map.

   Each invocation evaluates a half-open range of loci. Run ranges sequentially
   so one JVM's pmap owns the machine; completed chunk files are the checkpoints.
   There is no low-precision screen: such a screen can discard a real positive
   and therefore cannot establish that no useful inherited rule exists."
  (:require [mmca.baldwin-selection :as bs]))

(defn hold-one [genome locus rule]
  (-> genome
      (assoc :field (assoc (:field genome) locus rule))
      (assoc :hold (assoc (:hold genome) locus true))))

(defn evaluate [genome cost seeds sites]
  (let [reach (:mean (bs/reach genome seeds sites))
        band (bs/band-score reach)
        dependence (bs/plastic-dependence genome)]
    {:reach reach
     :band band
     :dependence dependence
     :fitness (- band (* cost dependence))}))

(defn parse-long-in-range [label value lower upper]
  (let [n (Long/parseLong value)]
    (when-not (<= lower n upper)
      (throw (ex-info (str label " outside allowed range")
                      {:label label :value n :lower lower :upper upper})))
    n))

(let [[record-path start-s end-s cost-s seeds-s sites-s] *command-line-args*]
  (when-not (and record-path start-s end-s)
    (throw (ex-info
            (str "usage: assimilation_map.clj RECORD START END [COST] [SEEDS] [SITES]; "
                 "START/END are a half-open locus range")
            {:args *command-line-args*})))
  (let [start (parse-long-in-range "start" start-s 0 bs/W)
        end (parse-long-in-range "end" end-s 0 bs/W)
        _ (when-not (< start end)
            (throw (ex-info "start must be less than end" {:start start :end end})))
        cost (Double/parseDouble (or cost-s "0.05"))
        seed-count (parse-long-in-range "seeds" (or seeds-s "3") 1 1000)
        site-count (parse-long-in-range "sites" (or sites-s "10") 1 bs/W)
        seeds (range 1 (inc seed-count))
        sites (take site-count (range 0 bs/W (max 1 (quot bs/W site-count))))
        genome (bs/best-genome-from-record record-path)
        baseline (evaluate genome cost seeds sites)
        candidates (for [locus (range start end) rule (range 256)] [locus rule])
        rows (doall
              (pmap (fn [[locus rule]]
                      [locus rule (evaluate (hold-one genome locus rule)
                                            cost seeds sites)])
                    candidates))]
    (binding [*out* *err*]
      (println (pr-str {:kind :assimilation-map-manifest
                        :record record-path :locus-start start :locus-end end
                        :cost cost :seeds (vec seeds) :sites (vec sites)
                        :expected-rows (* (- end start) 256)
                        :baseline baseline})))
    (println
     "locus\trule\tcurrent-rule\treach\tband\tdependence\tfitness\tbase-band\tbase-fitness\tfunction-preserved\tselectable")
    (doseq [[locus rule result] rows]
      (println
       (format "%d\t%d\t%d\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%.6f\t%s\t%s"
               locus rule (nth (:field genome) locus)
               (:reach result) (:band result) (:dependence result) (:fitness result)
               (:band baseline) (:fitness baseline)
               (>= (:band result) (* 0.9 (:band baseline)))
               (>= (:fitness result) (:fitness baseline)))))))
