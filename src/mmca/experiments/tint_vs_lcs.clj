(ns mmca.experiments.tint-vs-lcs
  "Retarget E5 local causal-state reconstruction at the offset+1 genotype
  fields used for the tint comparison.

  The default W64/T120 ensemble is deliberately smaller than the paper's
  W256/T600 display field. Both tint and LCS analyses consume these exact
  generated fields; six seeds preserve E5's seed-held-out selection and supply
  independent masks for the cross-seed transport null."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as c]
            [mmca.experiments.local-causal-states :as lcs]))

(def analysis-config
  (assoc lcs/default-config
         :writing [1 2 3 4 5 6 7 0]
         :seeds (vec (range 6))
         :width 64
         :steps 120
         :burn-in 20
         :folds 3))

(defn- write-grid! [path rows]
  (io/make-parents path)
  (spit path
        (str (str/join "\n" (map #(str/join " " %) rows)) "\n")))

(defn- mask-rows [points width steps]
  (let [points (set points)]
    (for [t (range steps)]
      (for [i (range width)]
        (if (contains? points [t i]) 1 0)))))

(defn- output-path [seed suffix]
  (format "data/tint_vs_lcs_offset1_s%d_%s.txt" seed suffix))

(defn run-analysis!
  ([] (run-analysis! analysis-config))
  ([config]
   (let [runs (into {}
                    (for [seed (:seeds config)]
                      [seed (c/run-propagator (:writing config) seed
                                              (:width config)
                                              (:steps config))]))
         result (lcs/reconstruct-genotype-fields runs config)
         selected (:selected result)]
     (doseq [seed (:seeds config)
             :let [run (get runs seed)
                   points (get-in result [:per-seed seed :coherent-points])]]
       (write-grid! (output-path seed "field") (:gen run))
       (write-grid! (output-path seed "lcs_mask")
                    (mask-rows points (:width config) (:steps config))))
     (spit "data/tint_vs_lcs_lcs_config.tsv"
           (str "analysis_seed\t20260727\n"
                "operator\toffset1\n"
                "width\t" (:width config) "\n"
                "steps\t" (:steps config) "\n"
                "burn_in\t" (:burn-in config) "\n"
                "seeds\t" (str/join "," (:seeds config)) "\n"
                "folds\t" (:folds config) "\n"
                "depth\t" (:depth selected) "\n"
                "tolerance\t" (:tolerance selected) "\n"
                "held_out_loss\t" (:loss selected) "\n"
                "held_out_n\t" (:held-out-n selected) "\n"
                "background_mass\t" (:background-mass config) "\n"
                "minimum_structure_size\t"
                (:minimum-structure-size config) "\n"
                "state_count\t" (:state-count result) "\n"
                "background_state_count\t"
                (:background-state-count result) "\n"))
     (println (format
               "offset1 LCS: W%d T%d seeds=%s d=%d tau=%.2f loss=%.6f"
               (:width config) (:steps config) (pr-str (:seeds config))
               (:depth selected) (:tolerance selected) (:loss selected)))
     (doseq [seed (:seeds config)]
       (let [seed-result (get-in result [:per-seed seed])]
         (println
          (format "seed %d: candidates=%d coherent=%d structures=%d"
                  seed (:candidate-point-count seed-result)
                  (:coherent-point-count seed-result)
                  (:structure-count seed-result)))))
     result)))

(defn -main [& _]
  (run-analysis!))
