(ns mmca.baldwin-guidance-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-guidance :as guidance]
            [mmca.baldwin-guidance-preregistration :as prereg]))

(def registration-path "holes/BALDWIN-GUIDANCE-PREREGISTRATION.edn")

(deftest registration-matches-the-lean-protocol
  (let [registration (prereg/read-edn registration-path)]
    (is (empty? (prereg/failures registration)))
    (is (false? (prereg/launchable? registration {})))
    (testing "task partitions are exactly twelve disjoint seed/site pairs"
      (let [train (get-in registration [:task-partition :training])
            held (get-in registration [:task-partition :held-out])
            pairs (fn [{:keys [seeds sites]}]
                    (set (for [seed seeds site sites] [seed site])))]
        (is (= 12 (count (pairs train))))
        (is (= 12 (count (pairs held))))
        (is (empty? (set/intersection (pairs train) (pairs held))))))))

(deftest learning-budget-does-not-shift-the-evaluation-tape
  (is (guidance/evaluation-tape-aligned?)))

(deftest population-path-checks-every-generation
  (let [genome (fn [id parent] {:id id :parent parent})
        valid [{:generation 0 :population [(genome 1 nil) (genome 2 nil)]}
               {:generation 1 :population [(genome 1 nil) (genome 3 2)]}]
        skipped (assoc-in valid [1 :generation] 2)
        orphaned (assoc-in valid [1 :population 1 :parent] 99)]
    (is (guidance/valid-population-path? valid))
    (is (false? (guidance/valid-population-path? skipped)))
    (is (false? (guidance/valid-population-path? orphaned)))))

(deftest heritable-signature-is-independent-of-fitness-ranking
  (let [individuals [{:id 2 :gamma 1.0 :field [2]}
                     {:id 1 :gamma 1.0 :field [1]}]]
    (is (= (guidance/heritable-signature individuals)
           (guidance/heritable-signature (reverse individuals))))))

(deftest preparedness-is-fixed-area-over-registered-budgets
  (let [summary {:curve [{:budget 0 :mean-band-score 0.1}
                         {:budget 4 :mean-band-score 0.2}
                         {:budget 16 :mean-band-score 0.3}
                         {:budget 64 :mean-band-score 0.4}
                         {:budget 120 :mean-band-score 0.9}]}]
    (is (= 0.25 (guidance/preparedness summary [0 4 16 64])))))
