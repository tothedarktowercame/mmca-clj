(ns mmca.river-feedback-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.river-feedback :as rf]))

(deftest deterministic-same-seed
  (is (= (rf/divergence 3 80 120) (rf/divergence 3 80 120))
      "matched river/ablation divergence is deterministic"))

(deftest feedback-is-substantial
  (is (> (last (rf/divergence 3 80 120)) 0.5)
      "on the authentic river, cutting X->G feedback diverges the genotype strongly"))
