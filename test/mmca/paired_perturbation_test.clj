(ns mmca.paired-perturbation-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.paired-perturbation :as pp]))

(def W [4 5 6 7 0 1 2 3])

(deftest deterministic-same-seed
  (is (= (pp/diverge W 7 80 120 40 40 pp/flip-phenotype-bit)
         (pp/diverge W 7 80 120 40 40 pp/flip-phenotype-bit))
      "same seed/width/steps/t*/site => identical divergence trace"))

(deftest feedforward-null-x-to-g
  ;; flipping a phenotype bit must never change the genotype on the base engine
  (let [trace (pp/diverge W 3 80 120 40 40 pp/flip-phenotype-bit)]
    (is (every? zero? (map :dG trace))
        "X->G is identically zero on the feedforward base")))

(deftest g-to-x-is-live
  ;; flipping a rule bit must reach the phenotype
  (let [trace (pp/diverge W 3 80 120 40 40 pp/flip-rule-bit)]
    (is (pos? (reduce + (map :dX trace)))
        "G->X expresses a genotype perturbation into the phenotype")))
