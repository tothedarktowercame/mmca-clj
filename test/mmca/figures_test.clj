(ns mmca.figures-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.figures :as figures]))

(deftest periodic-rule-activity-reference-values
  (testing "fixed and alternating controls are exact"
    (doseq [rule [0 8 204]]
      (is (zero? (figures/isolated-rule-activity rule))))
    (is (= 1.0 (figures/isolated-rule-activity 51))))
  (testing "complex and chaotic reference rules retain the paper calibration"
    (doseq [[rule expected] [[110 0.421]
                             [90 0.501]
                             [30 0.496]
                             [45 0.495]
                             [150 0.501]]]
      (is (< (abs (- (figures/isolated-rule-activity rule) expected))
             0.006)
          (str "rule " rule " activity moved from " expected)))))
