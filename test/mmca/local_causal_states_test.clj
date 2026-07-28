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
