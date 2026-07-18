(ns mmca.multiscale-spectra-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.multiscale-spectra :as e6]))

(def W [4 5 6 7 0 1 2 3])

(deftest deterministic-same-seed
  (is (= (e6/run-spectra W 0 60 80 :base)
         (e6/run-spectra W 0 60 80 :base))
      "same seed/width/steps => identical spectra data"))

(deftest genotype-activity-nonempty
  (let [{:keys [grid-G T width]} (e6/run-spectra W 0 60 80 :base)]
    (is (= width 60))
    (is (= T 80))
    (is (= (count grid-G) 80))))

(deftest power-spectrum-positive
  (let [{:keys [grid-G T width]} (e6/run-spectra W 0 60 80 :base)
        power (e6/dft-2d-power grid-G width T 0 0)]
    (is (pos? power) "DC power must be positive")))

(deftest overlap-decays-with-tau
  (let [{:keys [grid-G T width]} (e6/run-spectra W 0 60 80 :base)
        c1 (first (e6/spatial-covariance
                    (e6/local-overlap grid-G width T 1) width (- T 1) 0))
        c10 (first (e6/spatial-covariance
                     (e6/local-overlap grid-G width T 10) width (- T 10) 0))]
    (is (> c1 c10) "overlap at tau=1 should exceed tau=10 (temporal decay)")))
