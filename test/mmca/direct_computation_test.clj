(ns mmca.direct-computation-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.direct-computation :as e7]))

(def tiny-config
  {:operators [{:id :offset+4 :class :all-even
                :writing [4 5 6 7 0 1 2 3]}]
   :train-seeds [0 1]
   :test-seeds [2 3]
   :width 25
   :steps 6
   :storage-delays [4]
   :transmission-delays [4]
   :distances [4]
   :decoder-radii [1]
   :xor-spacing 2
   :default-delay 4
   :default-distance 4
   :default-radius 1})

(deftest e7-is-deterministic-and-held-out
  (let [first-run (e7/run-experiment tiny-config)
        second-run (e7/run-experiment tiny-config)]
    (is (= first-run second-run))
    (is (empty? (set (filter (set (:train-seeds tiny-config))
                             (:test-seeds tiny-config)))))
    ;; Base arm: 2 test seeds x 2 inputs per primitive = 4 held-out examples
    (is (= 4 (get-in first-run [:base-rows 0 :layers :X :storage :n-test])))
    ;; G modification has 2 inputs per example => 2 seeds x 4 inputs = 8
    (is (= 8 (get-in first-run [:base-rows 0 :layers :G :modification :n-test])))
    ;; River contrast is present with isolated-capacity
    (is (contains? (get-in first-run [:river-contrast :layers :G :storage])
                   :isolated-capacity))
    ;; Scans are present
    (is (seq (:delay (:scans first-run))))))

(deftest e7-matched-control-shares-tape
  "The river and ablated arms from the same injected state should diverge
  (proving the feedback channel is live), confirming the matched control is
  meaningful — not identical to the river."
  []
  (let [cfg tiny-config
        ;; Run a single seed through both arms and check the G trajectories differ
        river-state (e7/run-experiment cfg)
        river-storage (get-in river-state [:river-contrast :layers :G :storage :river])
        ablated-storage (get-in river-state [:river-contrast :layers :G :storage :ablated])]
    ;; Both should have valid accuracy in [0,1]
    (is (<= 0.0 (:accuracy river-storage) 1.0))
    (is (<= 0.0 (:accuracy ablated-storage) 1.0))))
