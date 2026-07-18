(ns mmca.experiments.river-feedback
  "Correction round (M-metaca-eoc v2): isolate the ORIGINAL paper river's X->G
  feedback with a MATCHED ablation. `run-original-paper-river` vs
  `run-original-paper-river-ablated` share the exact Java seed, initial state,
  RNG tape, and constant-zero quad-4cand construction; they differ ONLY in
  whether the genotype step reads the live (evolving) or the frozen (initial)
  phenotype. The genotype-trajectory divergence between them is therefore the
  causal effect of the live phenotype->genotype feedback -- the clean control
  Codex asked for, in place of comparing against the unrelated feedforward base.
  Deterministic: same seeds/width/steps => identical output."
  (:require [mmca.core :as c]))

(defn divergence
  "Per-step fraction of genotype cells where the live-feedback river differs from
  the frozen-phenotype ablation (same seed, same tape)."
  [seed width steps]
  (let [rv (:gen (c/run-original-paper-river seed width steps))
        ab (:gen (c/run-original-paper-river-ablated seed width steps))]
    (mapv (fn [t] (/ (c/changed-count (nth rv t) (nth ab t)) (double width)))
          (range (count rv)))))

(defn -main [& _]
  (let [seeds (range 1 21) width 80 steps 120
        curves (mapv #(divergence % width steps) seeds)
        at (fn [t] (let [xs (mapv #(nth % t) curves)]
                     {:mean (/ (reduce + xs) (double (count xs)))
                      :lo (apply min xs) :hi (apply max xs)}))]
    (println "River X->G feedback via matched frozen-phenotype ablation")
    (println "run-original-paper-river vs -ablated | seeds 1-20 | W=80 T=120")
    (doseq [t [1 5 20 60 119]]
      (let [{:keys [mean lo hi]} (at t)]
        (println (format "  t=%-4d genotype divergence: mean=%.3f  seed-range [%.3f, %.3f]"
                         t mean lo hi))))))
