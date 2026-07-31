(ns mmca.baldwin-artifacts-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-artifacts :as artifacts]
            [mmca.baldwin-selection :as selection]))

(defn recorded-genome [id band dependence]
  (selection/genome-record
   0 {:id id :gamma 1.0 :update-prob 1.0 :band band
      :dependence dependence :score band :reach 12.0
      :field (vec (repeat selection/W 0))
      :mask (vec (repeat selection/W true))
      :hold (vec (repeat selection/W false))}))

(deftest validates-shape-manifest-and-mode
  (let [manifest {:kind :manifest :revision "abc"
                  :configuration {:mode "hold-only"}}
        records [manifest (recorded-genome 1 0.9 1.0)
                 (recorded-genome 2 0.8 1.0)
                 {:kind :endpoint :gen 0 :id 1 :held-reach 2.0}]
        result (artifacts/validate-run-data!
                ["header" "generation"] records "hold-only" 1 2)]
    (is (:valid? result))
    (is (= "abc" (:revision result)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo #"wrong TSV"
         (artifacts/validate-run-data! ["header"] records "hold-only" 1 2)))))

(deftest treatment-separation-is-about-rank-not-score-shift
  (testing "constant dependence makes cost a rank-inert subtraction"
    (let [records [(recorded-genome 1 0.9 1.0)
                   (recorded-genome 2 0.8 1.0)]]
      (is (:ranking-equivalent?
           (artifacts/treatment-separation records 0 0.0 2.0)))))
  (testing "varying dependence can reverse the ranking"
    (let [records [(recorded-genome 1 0.9 1.0)
                   (recorded-genome 2 0.8 0.0)]
          result (artifacts/treatment-separation records 0 0.0 2.0)]
      (is (not (:ranking-equivalent? result)))
      (is (= [0] (:separating-generations result))))))
