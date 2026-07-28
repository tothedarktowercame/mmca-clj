(ns mmca.experiments.figshell-ground-truth
  "Held-out LCS reconstruction for the exact Figure 4 stripe ground truth."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as c]
            [mmca.experiments.local-causal-states :as lcs]))

(def writing [2 3 0 1 5 4 7 6])

(def config
  (assoc lcs/default-config
         :writing writing
         :seeds (vec (range 6))
         :width 80
         :steps 120
         :burn-in 12
         :folds 3
         :depths [1 2 3 4]
         :tolerances [0.02 0.05 0.1 0.2 0.4]))

(defn- write-grid! [path rows]
  (io/make-parents path)
  (spit path
        (str (str/join "\n"
                       (map (fn [row]
                              (str/join " " (if (string? row) (seq row) row)))
                            rows))
             "\n")))

(defn- mask-rows [points width steps]
  (let [points (set points)]
    (for [t (range steps)]
      (for [i (range width)]
        (if (contains? points [t i]) 1 0)))))

(defn run! []
  (let [runs (into {}
                   (for [seed (:seeds config)]
                     [seed (c/run-propagator writing seed
                                             (:width config)
                                             (:steps config))]))
        result (lcs/reconstruct-genotype-fields runs config)
        run (get runs 1)
        points (get-in result [:per-seed 1 :coherent-points])]
    (write-grid! "data/figshell_ground_truth_gen.txt" (:gen run))
    (write-grid! "data/figshell_ground_truth_phe.txt" (:phe run))
    (write-grid! "data/figshell_ground_truth_lcs_mask.txt"
                 (mask-rows points (:width config) (:steps config)))
    (spit "data/figshell_ground_truth_lcs.edn"
          (pr-str (select-keys result [:config :selected :candidates
                                      :state-count :background-state-count])))
    (println "selected LCS" (:selected result))
    (println "seed 1 reconstruction" (dissoc (get-in result [:per-seed 1])
                                              :coherent-points))
    result))

(defn -main [& _]
  (run!))
