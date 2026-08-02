(ns mmca.baldwin-masking-preregistration-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-preregistration :as prereg]))

(def registration
  (prereg/read-edn "holes/BALDWIN-MASKING-INTERVENTION-PREREGISTRATION.edn"))

(def passing-smoke
  (merge {:kind :baldwin-masking-intervention-smoke
          :schema 1
          :implementation-revision (:implementation-revision registration)
          :lean-revision masking/lean-revision
          :map-sha256 masking/discovery-map-sha256
          :record-sha256 (:source-record-sha256 registration)
          :evaluation-seeds masking/pilot-seeds
          :evaluation-sites masking/evaluation-sites
          :raw-units-per-arm 384
          :deadline-exceeded false}
         (zipmap prereg/required-smoke-booleans (repeat true))))

(deftest exact-registration-is-valid-but-not-launchable-without-smoke
  (is (empty? (prereg/registration-failures registration)))
  (is (false? (:launchable? (prereg/report registration nil)))))

(deftest every-smoke-observation-is-gating
  (is (:launchable? (prereg/report registration passing-smoke)))
  (doseq [observation prereg/required-smoke-booleans]
    (is (false? (:launchable?
                 (prereg/report registration (assoc passing-smoke observation false)))))))
