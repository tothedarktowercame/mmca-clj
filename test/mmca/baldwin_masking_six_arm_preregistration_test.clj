(ns mmca.baldwin-masking-six-arm-preregistration-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.baldwin-masking-six-arm :as six]
            [mmca.baldwin-masking-six-arm-preregistration :as prereg]))

(def registration
  (prereg/read-edn
   "holes/BALDWIN-MASKING-SIX-ARM-AMENDED-PREREGISTRATION.edn"))

(def passing-smoke
  (merge {:kind :baldwin-masking-six-arm-smoke
          :schema 1
          :implementation-revision (:implementation-revision registration)
          :lean-revision six/lean-revision
          :map-sha256 (:discovery-map-sha256 registration)
          :record-sha256 (:source-record-sha256 registration)
          :environment-seeds six/pilot-environment-seeds
          :discovery-rewrite-tapes six/discovery-rewrite-tapes
          :novel-rewrite-tapes six/novel-rewrite-tapes
          :evaluation-sites (:evaluation-sites registration)
          :raw-units-per-arm (vec (repeat 6 1152))
          :deadline-exceeded false}
         (zipmap prereg/required-smoke-booleans (repeat true))))

(deftest exact-registration-is-valid-but-awaits-smoke-admission
  (is (empty? (prereg/registration-failures registration)))
  (is (false? (:launchable? (prereg/report registration nil)))))

(deftest every-smoke-observation-is-gating
  (let [admitted (assoc registration :implementation-status :smoke-passed)]
    (is (:launchable? (prereg/report admitted passing-smoke)))
    (doseq [observation prereg/required-smoke-booleans]
      (is (false? (:launchable?
                   (prereg/report admitted
                                  (assoc passing-smoke observation false))))))))

(deftest wrong-tape-or-context-amendment-fails-closed
  (is (some #{:rewrite-tape-reuse}
            (prereg/registration-failures
             (assoc registration :novel-rewrite-tapes [1 1002 1003]))))
  (is (some #{:wrong-context-amendment}
            (prereg/registration-failures
             (assoc registration :shared-tape-context-result :passed)))))
