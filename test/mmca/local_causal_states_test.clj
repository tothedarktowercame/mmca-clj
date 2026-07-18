(ns mmca.local-causal-states-test
  (:require [clojure.test :refer [deftest is]]
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
