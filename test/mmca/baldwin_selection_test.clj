(ns mmca.baldwin-selection-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-selection :as selection]))

(def base-genome
  {:id 1
   :gamma 1.0
   :update-prob 1.0
   :field (vec (repeat selection/W 0))
   :mask (vec (repeat selection/W true))
   :hold (vec (repeat selection/W false))})

(deftest arguments-are-strict
  (testing "a misspelled treatment cannot silently become the control"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown arguments"
                          (selection/parse-arguments ["--mod" "hold-only"])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"duplicate arguments"
                          (selection/parse-arguments ["--mode" "hold-only"
                                                      "--mode" "standard"])))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"pairs"
                          (selection/parse-arguments ["--mode"])))
    (is (= {"mode" "hold-only" "c" "2"}
           (selection/parse-arguments ["--mode" "hold-only" "--c" "2"])))))

(deftest numeric-configuration-is-strict
  (let [valid {:cost 0.05 :generations 30 :population 24
               :evaluation-seed-count 3 :evaluation-site-count 10
               :field-rate 0.02 :warmup 8 :pin nil}]
    (is (selection/validate-config! valid))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid"
                          (selection/validate-config!
                           (assoc valid :population 23))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"gamma levels"
                          (selection/validate-config!
                           (assoc valid :pin 0.123))))))

(deftest hold-only-mutation-contract
  (testing "gamma/update/mask stay fixed while field and hold can evolve"
    (let [child (selection/mutate (java.util.Random. 7) base-genome 1.0
                                  true true false)]
      (is (= 1.0 (:gamma child)))
      (is (= 1.0 (:update-prob child)))
      (is (every? true? (:mask child)))
      (is (every? true? (:hold child)))
      (is (not= (:field base-genome) (:field child)))
      (is (selection/assert-mode! "hold-only" [child])))))

(deftest static-search-mutation-contract
  (testing "a static-search locus can never become plastic"
    (let [static (assoc base-genome :hold (vec (repeat selection/W true)))
          child (selection/mutate (java.util.Random. 7) static 1.0
                                  true true true)]
      (is (every? true? (:hold child)))
      (is (not= (:field static) (:field child)))
      (is (selection/assert-mode! "static-search" [child])))))

(deftest recorded-genomes-round-trip-to-mode-checker
  (let [recorded (-> base-genome
                     (update :mask #(mapv (fn [bit] (if bit 1 0)) %))
                     (update :hold #(mapv (fn [bit] (if bit 1 0)) %)))
        decoded (selection/decode-record-genome recorded)]
    (is (= base-genome decoded))
    (is (selection/assert-mode! "hold-only" [decoded]))))

(deftest mislabeled-treatment-fails-fast
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"gamma is not pinned"
                        (selection/assert-mode! "hold-only"
                                                [(assoc base-genome :gamma 0.99)])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"mask is not all live"
                        (selection/assert-mode! "hold-only"
                                                [(assoc-in base-genome [:mask 3] false)])))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"became plastic"
                        (selection/assert-mode! "static-search" [base-genome]))))

(deftest preflight-certificate-is-bound-to-the-design
  (let [file (java.io.File/createTempFile "baldwin-preflight-" ".edn")]
    (try
      (with-redefs [selection/preflight (fn [_seeds _sites] [])]
        (selection/ensure-preflight! "right" [1] [0]
                                     (.getAbsolutePath file) true))
      (is (:passed? (selection/ensure-preflight!
                     "right" [1] [0] (.getAbsolutePath file) false)))
      (spit file
            (pr-str {:kind :baldwin-preflight :schema 1 :passed? true :failures []
                     :key {:revision "wrong" :evaluation-seeds [1]
                           :evaluation-sites [0] :protocol {}}}))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo #"does not match"
           (selection/ensure-preflight! "right" [1] [0]
                                        (.getAbsolutePath file) false)))
      (finally
        (.delete file)))))
