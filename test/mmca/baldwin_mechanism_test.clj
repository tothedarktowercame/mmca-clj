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

(deftest allele-sensitivity-preserves-paired-units
  (let [small (assoc genome :field [1 2] :hold [false false])
        baseline [{:seed 1 :site 0 :reach 2.0}
                  {:seed 1 :site 8 :reach 4.0}]]
    (with-redefs [mechanism/paired-reach
                  (fn [_ _ _ _]
                    [{:seed 1 :site 0 :reach 3.0}
                     {:seed 1 :site 8 :reach 2.0}])]
      (is (= {:locus 0 :rule 90 :current-rule 1 :n 2 :mean-delta -0.5
              :positive 1 :negative 1 :ties 0 :deltas [1.0 -2.0]}
             (mechanism/allele-sensitivity
              small 0 90 baseline [1] [0 8] {}))))))

(deftest allele-sensitivity-refuses-held-loci
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unheld locus"
                        (mechanism/replace-unheld-allele
                         (assoc genome :hold (assoc (:hold genome) 3 true))
                         3 90))))

(deftest content-response-sign-test-is-exact-and-familywise
  (testing "24 paired units require at least 22 concordant signs across 640 probes"
    (is (mechanism/familywise-significant-direction? 22 2 640))
    (is (not (mechanism/familywise-significant-direction? 21 3 640)))
    (is (mechanism/familywise-significant-direction? 2 22 640)))
  (testing "ties never manufacture evidence"
    (is (not (mechanism/familywise-significant-direction? 0 0 640)))
    (is (= {:non-tied 0 :dominant-sign :tie :familywise-significant? false}
           (mechanism/paired-sign-summary {:positive 0 :negative 0} 640)))))

(deftest context-instrument-preserves-the-river-step
  (let [g (:field genome)
        p (selection/sampled-initial-phenotype 3)
        np (c/phenotype-step g p)
        frozen (selection/sampled-initial-phenotype 4)
        r1 (java.util.Random. 17) q1 (java.util.Random. 18) u1 (java.util.Random. 19)
        r2 (java.util.Random. 17) q2 (java.util.Random. 18) u2 (java.util.Random. 19)
        tally (volatile! {})
        expected (selection/gain-genotype-step
                  r1 q1 u1 g p np frozen 1.0 1.0
                  (:mask genome) (:hold genome))
        observed (mechanism/context-step r2 q2 u2 g p np frozen tally)]
    (is (= expected observed))
    (is (= (- selection/W 2)
           (reduce + (map :count (vals @tally)))))))

(deftest context-profile-covers-every-interior-cell-step
  (let [profile (mechanism/context-profile genome 1 1 nil)]
    (is (= (* (- selection/W 2) selection/TSTAR)
           (reduce + (map :count (vals profile)))))
    (is (= (set (range 16)) (set (keys profile))))))
