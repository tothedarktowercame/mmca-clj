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

(deftest positional-writing-is-order-independent
  (is (= [6 7 0 2 1 4 3 5]
         (mmca/positional-writing->neighbourhood-writing
          [2 3 4 5 6 7 0 1])))
  (is (= [1 2 4 5 3 6 7 7]
         (mmca/positional-writing->neighbourhood-writing
          [0 0 1 2 3 4 5 6]))))

(deftest short-runs-are-deterministic-wolfram-golden
  (testing "blending engine, including head/tail/interior RNG order"
    (let [run (mmca/run-propagator [2 3 4 5 6 7 0 1] 0 5 2)]
      (is (= [[245 78 238 9 143]
              [2 236 79 159 16]
              [236 78 222 64 143]]
             (:gen run)))
      (is (= ["10100" "10101" "01100"] (:phe run)))))
  (testing "phenotype-reading river"
    (let [run (mmca/run-river-reconstruction 1 5 2)]
      (is (= [[68 42 204 255 31]
              [68 204 9 63 225]
              [2 76 196 246 33]]
             (:gen run)))
      (is (= ["00011" "00010" "00010"] (:phe run))))))

(deftest representative-full-run-wolfram-golden
  (let [run (mmca/run-propagator [1 6 2 5 0 3 7 4] 0 80 120)]
    (is (= "792e88112b0f45a175b6d86b432bfd77c74d14bf09b7c313b36cf0a08642e8ff"
           (sha256 (select-keys run [:gen :phe]))))
    (is (= {:death 120 :rules 30 :activity 2719}
           (select-keys run [:death :rules :activity])))))

(deftest continuous-interrupter-seam
  (let [writing [4 5 6 7 0 1 2 3]
        legacy (mmca/run-propagator writing 7 30 40)
        explicit-one (mmca/run-propagator writing 7 30 40
                                          {:interrupter-q 1.0})
        q-half-a (mmca/run-propagator writing 7 30 40
                                      {:interrupter-q 0.5})
        q-half-b (mmca/run-propagator writing 7 30 40
                                      {:interrupter-q 0.5})]
    (is (= legacy explicit-one) "q=1 is the exact legacy engine")
    (is (= q-half-a q-half-b) "intermediate q is seeded and deterministic")
    (is (not= (:gen legacy) (:gen q-half-a))
        "q changes genotype dynamics")))

(deftest figure6-river-run-wolfram-golden
  (let [run (mmca/run-river-reconstruction 1 80 120)]
    (is (= "baffa03a7eda023d52aa289bdca370d4e15efee51bc014ac401386a02c8882ae"
           (sha256 (select-keys run [:gen :phe]))))
    (is (= {:death 31 :rules 2 :activity 436}
           (select-keys run [:death :rules :activity])))))

(deftest original-paper-river-wolfram-golden
  (let [run (mmca/run-river 1 80 120)]
    (is (= "2442124f667007b46d5f2b36a0044ad316d00328eb4be1b595e3fe092b915135"
           (sha256 (select-keys run [:gen :phe]))))
    (is (= {:death 120 :rules 31 :activity 3940}
           (select-keys run [:death :rules :activity])))))
