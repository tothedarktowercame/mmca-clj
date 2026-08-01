(ns mmca.baldwin-mechanism-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]
            [mmca.core :as c]))

(def genome
  {:gamma 1.0
   :update-prob 1.0
   :field (c/java-random-genotype (java.util.Random. 29) selection/W)
   :mask (vec (repeat selection/W true))
   :hold (vec (repeat selection/W false))})

(def fixed-p0 (apply str (take selection/W (cycle "0011"))))

(deftest separated-seed-interface-preserves-the-historical-first-stage
  (let [seed 7
        random (java.util.Random. seed)
        gate (java.util.Random. (+ 987654321 seed))
        upd (java.util.Random. (+ 123456789 seed))
        _ (c/java-random-genotype random selection/W)
        p0 (c/java-random-phenotype random selection/W)
        historical (:gen (selection/run-from
                          random gate upd (:field genome) p0 selection/TSTAR
                          1.0 1.0 (:mask genome) (:hold genome) p0))]
    (is (= historical (mechanism/learned-field-at-tstar genome seed seed nil)))))

(deftest stationarity-grid-has-a-positive-apparatus-control
  (let [grid (mechanism/stationarity-grid genome [1 2 3] 99 fixed-p0)]
    (testing "fixing both sources of lifetime variation is exactly reproducible"
      (is (:apparatus-valid? grid))
      (is (= 1.0 (get-in grid
                         [:fixed-p0/shared-rewrite
                          :mean-pairwise-agreement]))))
    (testing "all four preregistered cells are present"
      (is (= #{:variable-p0/variable-rewrite :fixed-p0/variable-rewrite
               :variable-p0/shared-rewrite :fixed-p0/shared-rewrite}
             (disj (set (keys grid)) :apparatus-valid?))))))

(deftest field-agreement-rejects-mismatched-widths
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"widths differ"
                        (mechanism/field-agreement [1] [1 2]))))
