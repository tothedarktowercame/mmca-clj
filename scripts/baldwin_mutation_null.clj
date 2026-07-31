(ns baldwin-mutation-null
  "Cheap empirical hold baseline using the production breeding operator.

   Reach cannot influence neutral survival, so evaluating it for every genome in
   every generation is pure cost. This script preserves initialization, random
   ranking, survivor retention, parent choice, mutation order, and RNG streams,
   and reports the held trajectory without running the CA."
  (:require [mmca.baldwin-selection :as selection]
            [mmca.core :as core]))

(let [[gens-s pop-s field-rate-s evolution-seed-s] *command-line-args*
      generations (Long/parseLong (or gens-s "30"))
      population-size (Long/parseLong (or pop-s "24"))
      field-rate (Double/parseDouble (or field-rate-s "0.02"))
      evolution-seed (Long/parseLong (or evolution-seed-s "20260730"))
      rng (java.util.Random. evolution-seed)
      hrng (java.util.Random. (+ evolution-seed 555000001))
      nrng (java.util.Random. (+ evolution-seed 555000002))
      next-id (atom 0)
      fresh-id #(swap! next-id inc)
      initial (vec
               (repeatedly
                population-size
                #(hash-map :gamma 1.0 :update-prob 1.0
                           :mask (vec (repeat selection/W true))
                           :hold (vec (repeat selection/W false))
                           :field (core/java-random-genotype rng selection/W)
                           :id (fresh-id))))]
  (binding [*out* *err*]
    (println (pr-str {:kind :mutation-null-manifest :schema 1
                      :generations generations :population population-size
                      :field-rate field-rate :evolution-seed evolution-seed
                      :mode :hold-only})))
  (println "gen\tmean-held")
  (loop [generation 0 population initial]
    (when (< generation generations)
      (selection/assert-mode! "hold-only" population)
      (let [mean-held (/ (reduce +
                                (map (fn [genome]
                                       (/ (count (filter true? (:hold genome)))
                                          (double selection/W)))
                                     population))
                         (double population-size))
            ranked (->> population
                        (mapv #(assoc % :score (.nextDouble nrng)))
                        (sort-by :score >)
                        vec)
            next-population
            (:population
             (selection/breed
              ranked rng hrng
              {:hgt? false :field-rate field-rate :gamma-pinned? true
               :plasticity-pinned? true :hold-pinned? false
               :fresh-id fresh-id}))]
        (println (format "%d\t%.6f" generation mean-held))
        (recur (inc generation) next-population)))))
