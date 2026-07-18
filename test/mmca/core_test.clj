(ns mmca.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.core :as mmca])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(defn- sha256 [value]
  (format "%064x"
          (BigInteger. 1
                       (.digest (MessageDigest/getInstance "SHA-256")
                                (.getBytes (pr-str value) "UTF-8")))))

(deftest rule-conversion-and-wolfram-semantics
  (is (= 110 (mmca/bits-rule (mmca/rule-bits 110))))
  (is (= [0 1 1 0 1 1 1 0] (mmca/rule-bits 110)))
  (is (= [0 1 1 0 1 1 1 0]
         (mapv (fn [[l c r]] (mmca/rule-output 110 l c r))
               [[1 1 1] [1 1 0] [1 0 1] [1 0 0]
                [0 1 1] [0 1 0] [0 0 1] [0 0 0]]))))

(deftest propagation-fixed-points
  (let [rot2 [2 3 4 5 6 7 0 1]
        fixed (filter (fn [rule]
                        (every? #(= rule (mmca/propagate-at rule rot2 %))
                                (range 8)))
                      (range 256))]
    (is (= 4 (count fixed)))
    (is (every? #(= 4 (reduce + (mmca/rule-bits %))) fixed))))

(deftest short-runs-match-elisp-golden-trajectories
  (testing "blending engine, including head/tail/interior RNG order"
    (let [run (mmca/run-propagator [2 3 4 5 6 7 0 1] 0 5 2)]
      (is (= [[245 78 238 9 143]
              [2 236 79 159 16]
              [236 78 222 64 143]]
             (:gen run)))
      (is (= ["10100" "10101" "01100"] (:phe run)))))
  (testing "phenotype-reading river"
    (let [run (mmca/run-river 1 5 2)]
      (is (= [[67 42 203 255 31]
              [188 203 222 63 225]
              [188 71 58 225 33]]
             (:gen run)))
      (is (= ["00011" "10110" "10100"] (:phe run))))))

(deftest representative-full-run-is-grid-identical
  (let [run (mmca/run-propagator [1 6 2 5 0 3 7 4] 0 80 120)]
    (is (= "ded7725367721c43596ff7daf7c2374b7af83c9d957df621c8cd005d7d7bcc0a"
           (sha256 (select-keys run [:gen :phe]))))
    (is (= {:death 120 :rules 31 :activity 2816}
           (select-keys run [:death :rules :activity])))))
