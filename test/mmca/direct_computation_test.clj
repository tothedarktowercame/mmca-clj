(ns mmca.codex-8-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.codex-8 :as e7]))

(def tiny-config
  {:operators [{:id :offset+4 :class :all-even
                :writing [4 5 6 7 0 1 2 3]}]
   :train-seeds [0 1]
   :test-seeds [2 3]
   :width 25
   :steps 4
   :storage-delay 4
   :transmission-delay 4
   :distance 4
   :xor-spacing 2
   :feature-radius 1})

(deftest e7-is-deterministic-and-held-out
  (let [first-run (e7/run-experiment tiny-config)
        second-run (e7/run-experiment tiny-config)]
    (is (= first-run second-run))
    (is (empty? (set (filter (set (:train-seeds tiny-config))
                             (:test-seeds tiny-config)))))
    (is (= 4 (get-in first-run [:rows 0 :layers :X :storage :n-test])))
    (is (= 8 (get-in first-run [:rows 0 :layers :G :modification :n-test])))))
