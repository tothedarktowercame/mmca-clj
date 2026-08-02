(ns mmca.experiments.lcs-overlays
  "Held-out LCS reconstructions for the exact Figure 13 and river display fields."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as c]
            [mmca.experiments.local-causal-states :as lcs]
            [mmca.figures :as figures]))

(def analysis-seed 20260802)
(def training-seeds (vec (range 6)))

(def tint-training-config
  (assoc lcs/default-config
         :seeds training-seeds
         :width 64
         :steps 120
         :burn-in 20
         :folds 3))

(def tint-target-config
  (assoc tint-training-config
         :seeds [1]
         :width figures/eoc-width
         :steps figures/eoc-steps))

(def river-training-config lcs/default-config)

(def river-target-config
  (assoc river-training-config
         :seeds [1]
         :width figures/river-plate-width
         :steps figures/river-plate-steps))

(def tint-operators
  [[:offset1 figures/eoc-offset1]
   [:two4cyc figures/two-4cycle-sustain]
   [:sigma16250374 figures/eoc-sigma16250374]])

(defn- write-grid! [path rows]
  (io/make-parents path)
  (spit path (str (str/join "\n" (map #(str/join " " %) rows)) "\n")))

(defn- mask-rows [points width steps]
  (let [points (set points)]
    (for [t (range (inc steps))]
      (for [i (range width)]
        (if (contains? points [t i]) 1 0)))))

(defn- result-row [dataset layer future-layer training-config target-config result]
  (let [selected (:selected result)
        target (get-in result [:per-target 1])]
    {:dataset dataset
     :layer (name layer)
     :future-layer (name future-layer)
     :training-seeds (str/join "," (:seeds training-config))
     :training-width (:width training-config)
     :training-steps (:steps training-config)
     :training-burn-in (:burn-in training-config)
     :target-seed 1
     :target-width (:width target-config)
     :target-steps (:steps target-config)
     :selected-depth (:depth selected)
     :selected-tolerance (:tolerance selected)
     :held-out-loss (:loss selected)
     :held-out-n (:held-out-n selected)
     :state-count (:state-count result)
     :background-state-count (:background-state-count result)
     :candidate-points (:candidate-point-count target)
     :coherent-points (:coherent-point-count target)
     :structures (:structure-count target)}))

(defn- run-tint! [id writing]
  (let [training-runs
        (into {}
              (for [seed training-seeds]
                [seed (c/run-propagator writing seed
                                        (:width tint-training-config)
                                        (:steps tint-training-config))]))
        target-run (c/run-propagator writing 1
                                     (:width tint-target-config)
                                     (:steps tint-target-config))
        result (lcs/reconstruct-target-fields
                training-runs {1 target-run} :genotype :genotype
                tint-training-config tint-target-config)
        points (get-in result [:per-target 1 :coherent-points])
        prefix (format "data/lcs_overlay_tint_%s" (name id))]
    (write-grid! (str prefix "_gen.txt") (:gen target-run))
    (write-grid! (str prefix "_mask.txt")
                 (mask-rows points (:width tint-target-config)
                            (:steps tint-target-config)))
    (println (format "%s: d=%d tau=%.2f loss=%.6f coherent=%d structures=%d"
                     (name id) (get-in result [:selected :depth])
                     (get-in result [:selected :tolerance])
                     (get-in result [:selected :loss]) (count points)
                     (get-in result [:per-target 1 :structure-count])))
    {:row (result-row (name id) :genotype :genotype
                      tint-training-config tint-target-config result)
     :candidates (map #(assoc % :dataset (name id)) (:candidates result))}))

(defn- run-river! []
  (let [training-runs
        (into {}
              (for [seed training-seeds]
                [seed (c/run-river seed (:width river-training-config)
                                   (:steps river-training-config))]))
        target-run (c/run-river 1 (:width river-target-config)
                                (:steps river-target-config))
        result (lcs/reconstruct-target-fields
                training-runs {1 target-run} :joint :joint
                river-training-config river-target-config)
        points (get-in result [:per-target 1 :coherent-points])]
    (write-grid! "data/lcs_overlay_river_gen.txt" (:gen target-run))
    (write-grid! "data/lcs_overlay_river_phe.txt" (:phe target-run))
    (write-grid! "data/lcs_overlay_river_mask.txt"
                 (mask-rows points (:width river-target-config)
                            (:steps river-target-config)))
    (println (format "river: d=%d tau=%.2f loss=%.6f coherent=%d structures=%d"
                     (get-in result [:selected :depth])
                     (get-in result [:selected :tolerance])
                     (get-in result [:selected :loss]) (count points)
                     (get-in result [:per-target 1 :structure-count])))
    {:row (result-row "river" :joint :joint
                      river-training-config river-target-config result)
     :candidates (map #(assoc % :dataset "river") (:candidates result))}))

(def config-fields
  [:analysis-seed :dataset :layer :future-layer :training-seeds
   :training-width :training-steps :training-burn-in :target-seed
   :target-width :target-steps :selected-depth :selected-tolerance
   :held-out-loss :held-out-n :state-count :background-state-count
   :candidate-points :coherent-points :structures])

(defn- tsv-line [fields row]
  (str/join "\t" (map #(get row % "") fields)))

(defn run-analysis! []
  (let [results (conj (mapv (fn [[id writing]] (run-tint! id writing))
                            tint-operators)
                      (run-river!))
        rows (map #(assoc (:row %) :analysis-seed analysis-seed) results)
        candidates (mapcat :candidates results)
        candidate-fields [:dataset :depth :tolerance :loss :held-out-n]]
    (spit "data/lcs_overlay_config.tsv"
          (str (tsv-line config-fields (zipmap config-fields (map name config-fields)))
               "\n" (str/join "\n" (map #(tsv-line config-fields %) rows)) "\n"))
    (spit "data/lcs_overlay_candidates.tsv"
          (str (tsv-line candidate-fields
                         (zipmap candidate-fields (map name candidate-fields)))
               "\n" (str/join "\n"
                               (map #(tsv-line candidate-fields %)
                                    candidates)) "\n"))
    (println "wrote Part C LCS fields and configuration")
    rows))

(defn -main [& _]
  (run-analysis!))
