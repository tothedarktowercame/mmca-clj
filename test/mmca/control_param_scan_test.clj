(ns mmca.codex-5-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.codex-5 :as e2]))

(def tiny-config
  (assoc e2/default-config
         :seed-count 4
         :widths [12 18]
         :qs [0.0 0.5 1.0]
         :steps 30
         :late-window 10
         :collapse-window 4))

(deftest seeded-e2-scan-is-deterministic
  (is (= (e2/scan tiny-config) (e2/scan tiny-config))
      "same seed range, widths, q grid, and steps reproduce exactly"))
