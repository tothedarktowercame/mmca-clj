(ns mmca.eoc-sweep-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.core :as c]
            [mmca.figures :as figures]))

(deftest genotype-noise-is-seeded-and-zero-is-a-no-op
  (let [writing figures/eoc-offset1
        noisy-opts {:interrupter-q 0.7 :noise-p 0.2}
        noisy-a (c/run-propagator writing 5 24 20 noisy-opts)
        noisy-b (c/run-propagator writing 5 24 20 noisy-opts)
        baseline (c/run-propagator writing 5 24 20 {:interrupter-q 0.7})
        explicit-zero (c/run-propagator writing 5 24 20
                                        {:interrupter-q 0.7 :noise-p 0.0})]
    (testing "positive noise is deterministic for a fixed seed and parameters"
      (is (= (:gen noisy-a) (:gen noisy-b)))
      (is (not= (:gen noisy-a) (:gen baseline))))
    (testing "noise-p zero preserves the complete legacy run"
      (is (= baseline explicit-zero)))))
