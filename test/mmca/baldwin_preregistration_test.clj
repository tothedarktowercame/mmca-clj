(ns mmca.baldwin-preregistration-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-preregistration :as prereg]))

(def registration-path "holes/BALDWIN-SEARCH-PREREGISTRATION.edn")

(deftest committed-registration-is-valid-but-needs-its-exact-receipt
  (let [registration (prereg/read-registration registration-path)]
    (is (empty? (prereg/failures registration)))
    (is (not (prereg/launchable? registration {}))
        "registration status alone is not implementation evidence")))

(deftest committed-smoke-receipt-discharges-the-launch-gate
  (let [registration (prereg/read-registration registration-path)
        receipt (prereg/read-registration
                 "data/baldwin-runs/search-smoke-4d4c7290/smoke.edn")]
    (is (prereg/launchable? registration receipt))))

(deftest replication-seeds-must-be-independent
  (let [registration (prereg/read-registration registration-path)
        reused (assoc registration :confirmation-seeds [20260730])]
    (is (some #{:pilot-reused-for-confirmation} (prereg/failures reused)))))

(deftest fixed-treatment-is-a-value-not-just-a-label
  (let [registration (prereg/read-registration registration-path)]
    (is (some #{:invalid-fixed-p0}
              (prereg/failures (assoc registration :fixed-p0 "chosen-later"))))))

(deftest launch-needs-the-exact-smoke-receipt
  (let [base (prereg/read-registration registration-path)
        registration (assoc base :implementation-status :smoke-passed
                                  :implementation-revision "abc123")
        complete (assoc (zipmap prereg/required-observations (repeat true))
                        :revision "abc123" :fixed-p0 (:fixed-p0 base))]
    (testing "missing observations fail closed"
      (is (not (prereg/launchable? registration
                                    (dissoc complete :shared-random-tape)))))
    (testing "all registered observations permit the later launch gate"
      (is (prereg/launchable? registration complete)))
    (testing "the receipt is bound to code identity and the preregistered p0"
      (is (not (prereg/launchable? registration
                                    (assoc complete :revision "different"))))
      (is (not (prereg/launchable? registration
                                    (assoc complete :fixed-p0 (apply str (repeat 80 "0")))))))))
