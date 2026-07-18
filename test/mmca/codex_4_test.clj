(ns mmca.codex-4-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.codex-4 :as e4]))

(def test-config
  (assoc e4/default-config
         :seeds [0 1]
         :width 12
         :steps 10
         :burn-in 3
         :folds 2
         :surrogate-seed "e4-test-surrogate"))

(deftest e4-is-deterministic-for-identical-seeds-and-config
  (is (= (e4/experiment test-config)
         (e4/experiment test-config))))
