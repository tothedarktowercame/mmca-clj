(ns mmca.local-causal-states-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.core :as c]
            [mmca.experiments.local-causal-states :as lcs]))

(def test-config
  (assoc lcs/default-config
         :seeds [0 1 2]
         :width 10
         :steps 7
         :burn-in 2
         :folds 3
         :depths [1]
         :tolerances [0.2]))

(deftest local-causal-state-reconstruction-is-deterministic
  (is (= (lcs/experiment test-config)
         (lcs/experiment test-config))))

(deftest genotype-field-reconstruction-is-deterministic
  (let [runs (into {}
                   (for [seed (:seeds test-config)]
                     [seed (c/run-propagator (:writing test-config) seed
                                             (:width test-config)
                                             (:steps test-config))]))
        once (lcs/reconstruct-genotype-fields runs test-config)]
    (is (= once (lcs/reconstruct-genotype-fields runs test-config)))
    (is (= (set (:seeds test-config))
           (set (keys (:per-seed once)))))
    (is (every? #(every? (fn [[t i]]
                           (and (<= 0 t (dec (:steps test-config)))
                                (<= 0 i (dec (:width test-config)))))
                         (:coherent-points %))
                (vals (:per-seed once))))))

(deftest batched-tolerance-selection-preserves-held-out-result
  ;; Regression values from the pre-batching selector.  The optimized path may
  ;; share tolerance-independent fits/predictions, but must not change losses.
  (let [config (assoc test-config
                      :depths [1 2]
                      :tolerances [0.1 0.2 0.4])
        runs (into {}
                   (for [seed (:seeds config)]
                     [seed (c/run-propagator (:writing config) seed
                                             (:width config)
                                             (:steps config))]))
        result (lcs/reconstruct-genotype-fields runs config)]
    (is (= {:loss 1.0118580712803833
            :held-out-n 120
            :depth 1
            :tolerance 0.1}
           (:selected result)))
    (is (= [1.0118580712803833 1.0742177148577066 1.22249969943066
            1.9844235184525272 1.9969901320483827 2.03263068003497]
           (mapv :loss (:candidates result))))))

(deftest held-out-model-classifies-separate-target-deterministically
  (let [training-runs
        (into {}
              (for [seed (:seeds test-config)]
                [seed (c/run-propagator (:writing test-config) seed
                                        (:width test-config)
                                        (:steps test-config))]))
        target-config (assoc test-config :seeds [7] :width 12 :steps 8)
        target-runs
        {7 (c/run-propagator (:writing test-config) 7
                             (:width target-config) (:steps target-config))}
        reconstruct
        #(lcs/reconstruct-target-fields
          training-runs target-runs :genotype :genotype
          test-config target-config)
        once (reconstruct)]
    (is (= once (reconstruct)))
    (is (= #{7} (set (keys (:per-target once)))))
    (is (every? (fn [[t i]]
                  (and (<= 0 t (dec (:steps target-config)))
                       (<= 0 i (dec (:width target-config)))))
                (get-in once [:per-target 7 :coherent-points])))))
