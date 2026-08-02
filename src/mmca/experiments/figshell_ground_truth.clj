(ns mmca.experiments.figshell-ground-truth
  "Held-out LCS reconstruction for the exact Figure 4 stripe ground truth."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as c]
            [mmca.experiments.local-causal-states :as lcs]))

(def writing [2 3 0 1 5 4 7 6])

(def analysis-config
  (assoc lcs/default-config
         :writing writing
         :seeds (vec (range 6))
         :width 80
         :steps 120
         :burn-in 20
         :folds 3
         :depths [1 2 3 4]
         :tolerances [0.02 0.05 0.1 0.2 0.4]))

(defn- write-grid! [path rows]
  (io/make-parents path)
  (spit path (str (str/join "\n" (map #(str/join " " %) rows)) "\n")))

(defn- mask-rows [points width steps]
  (let [points (set points)]
    (for [t (range (inc steps))]
      (for [i (range width)]
        (if (contains? points [t i]) 1 0)))))

(defn run-analysis!
  ([] (run-analysis! analysis-config))
  ([config]
   (let [runs (into {}
                    (for [seed (:seeds config)]
                      [seed (c/run-propagator (:writing config) seed
                                              (:width config) (:steps config))]))
         result (lcs/reconstruct-genotype-fields runs config)
         selected (:selected result)
         seed 1
         points (get-in result [:per-seed seed :coherent-points])]
     (write-grid! "data/figshell_lcs_mask.txt"
                  (mask-rows points (:width config) (:steps config)))
     (spit "data/figshell_lcs_candidates.tsv"
           (str "depth\ttolerance\theld_out_loss\theld_out_n\n"
                (str/join
                 "\n"
                 (for [{:keys [depth tolerance loss held-out-n]}
                       (sort-by (juxt :depth :tolerance) (:candidates result))]
                   (str depth "\t" tolerance "\t" loss "\t" held-out-n)))
                "\n"))
     (spit "data/figshell_lcs_config.tsv"
           (str "analysis_seed\t20260802\n"
                "operator\tfigshell-all-even-involution\n"
                "evaluation_seed\t1\n"
                "selection_seeds\t" (str/join "," (:seeds config)) "\n"
                "width\t" (:width config) "\n"
                "steps\t" (:steps config) "\n"
                "burn_in\t" (:burn-in config) "\n"
                "folds\t" (:folds config) "\n"
                "depths\t" (str/join "," (:depths config)) "\n"
                "tolerances\t" (str/join "," (:tolerances config)) "\n"
                "selected_depth\t" (:depth selected) "\n"
                "selected_tolerance\t" (:tolerance selected) "\n"
                "held_out_loss\t" (:loss selected) "\n"
                "held_out_n\t" (:held-out-n selected) "\n"
                "background_mass\t" (:background-mass config) "\n"
                "minimum_structure_size\t" (:minimum-structure-size config) "\n"
                "selected_at_depth_edge\t"
                (contains? #{(apply min (:depths config))
                             (apply max (:depths config))} (:depth selected)) "\n"
                "selected_at_tolerance_edge\t"
                (contains? #{(apply min (:tolerances config))
                             (apply max (:tolerances config))} (:tolerance selected)) "\n"))
     (println (format "figshell LCS: d=%d tau=%.3f loss=%.6f n=%d coherent=%d"
                      (:depth selected) (:tolerance selected) (:loss selected)
                      (:held-out-n selected) (count points)))
     result)))

(defn -main [& _]
  (run-analysis!))
