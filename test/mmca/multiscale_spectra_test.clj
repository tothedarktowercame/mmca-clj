(ns mmca.multiscale-spectra-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.experiments.multiscale-spectra :as e6]))

(def W [4 5 6 7 0 1 2 3])

(deftest deterministic-same-seed
  (is (= (e6/run-spectra W 0 60 80 :base)
         (e6/run-spectra W 0 60 80 :base))
      "same seed/width/steps => identical spectra data"))

(deftest deterministic-river-same-seed
  (is (= (e6/run-spectra nil 0 60 80 :river)
         (e6/run-spectra nil 0 60 80 :river))
      "same seed/width/steps => identical river spectra data"))

(deftest deterministic-river-ablated-same-seed
  (is (= (e6/run-spectra nil 0 60 80 :river-ablated)
         (e6/run-spectra nil 0 60 80 :river-ablated))
      "same seed/width/steps => identical river-ablated spectra data"))

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

(deftest river-and-ablated-produce-different-genotypes
  "The authentic river and its matched ablation must diverge (feedback is live)."
  (let [river (e6/run-spectra nil 0 60 80 :river)
        ablated (e6/run-spectra nil 0 60 80 :river-ablated)]
    (is (not= (:grid-G river) (:grid-G ablated))
        "river and ablated must produce different genotype activity grids")))

(deftest ensemble-cross-spectrum-deterministic
  "Ensemble cross-spectrum is deterministic across repeated calls."
  (let [k-list [0 15 30]
        om-list [-5 0 5]
        r1 (e6/ensemble-averaged-cross [0 1 2] 60 80 :river k-list om-list)
        r2 (e6/ensemble-averaged-cross [0 1 2] 60 80 :river k-list om-list)]
    (is (= r1 r2) "ensemble cross-spectrum must be deterministic")))

(deftest ensemble-demeaning-removes-dc
  "Demeaned cross-spectrum at omega=0 (temporal DC) must be ~0 for all k."
  (let [result (e6/ensemble-averaged-cross [0 1] 60 80 :river [0 15 30] [0])]
    (doseq [k [0 15 30]]
      (is (< (Math/abs (:mean (get result [k 0]))) 1e-6)
          (str "demeaned DC at k=" k " must be ~0")))))
