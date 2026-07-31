(ns mmca.baldwin-preregistration-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-preregistration :as prereg]))

(def registration-path "holes/BALDWIN-SEARCH-PREREGISTRATION.edn")

(deftest committed-registration-is-valid-but-blocked
  (let [registration (prereg/read-registration registration-path)]
    (is (empty? (prereg/failures registration)))
    (is (not (prereg/launchable? registration {}))
        "a valid prose/EDN registration is not implementation evidence")))

(deftest replication-seeds-must-be-independent
  (let [registration (prereg/read-registration registration-path)
        reused (assoc registration :confirmation-seeds [20260730])]
    (is (some #{:pilot-reused-for-confirmation} (prereg/failures reused)))))

(deftest launch-needs-the-exact-smoke-receipt
  (let [registration (assoc (prereg/read-registration registration-path)
                            :implementation-status :smoke-passed)
        complete (zipmap prereg/required-observations (repeat true))]
    (testing "missing observations fail closed"
      (is (not (prereg/launchable? registration
                                    (dissoc complete :shared-random-tape)))))
    (testing "all registered observations permit the later launch gate"
      (is (prereg/launchable? registration complete)))))
