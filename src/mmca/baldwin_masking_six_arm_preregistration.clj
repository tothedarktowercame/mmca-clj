(ns mmca.baldwin-masking-six-arm-preregistration
  "Fail-closed validator for the amended six-arm registration and smoke."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-six-arm :as six]))

(defn read-edn [path] (edn/read-string (slurp path)))

(defn registration-failures [r]
  (let [pilot (set (:pilot-environment-seeds r))
        confirmation (set (:confirmation-environment-seeds r))
        discovery (set (:discovery-rewrite-tapes r))
        novel (set (:novel-rewrite-tapes r))]
    (cond-> []
      (not= :baldwin-masking-six-arm-amended-preregistration (:kind r))
      (conj :wrong-kind)
      (not= 1 (:schema r)) (conj :wrong-schema)
      (not= six/lean-revision (:lean-revision r)) (conj :wrong-lean-revision)
      (str/blank? (:implementation-revision r))
      (conj :missing-implementation-revision)
      (not= masking/discovery-revision (:discovery-revision r))
      (conj :wrong-discovery-revision)
      (not= masking/discovery-map-sha256 (:discovery-map-sha256 r))
      (conj :wrong-map-sha)
      (not= masking/registered-panel (:panel r)) (conj :wrong-panel)
      (not= six/arms (:arms r)) (conj :wrong-arms)
      (not= six/pilot-environment-seeds (:pilot-environment-seeds r))
      (conj :wrong-pilot-seeds)
      (not= six/confirmation-environment-seeds
            (:confirmation-environment-seeds r))
      (conj :wrong-confirmation-seeds)
      (seq (set/intersection pilot confirmation)) (conj :environment-seed-reuse)
      (not= six/discovery-rewrite-tapes (:discovery-rewrite-tapes r))
      (conj :wrong-discovery-tapes)
      (not= six/novel-rewrite-tapes (:novel-rewrite-tapes r))
      (conj :wrong-novel-tapes)
      (seq (set/intersection discovery novel)) (conj :rewrite-tape-reuse)
      (not= masking/evaluation-sites (:evaluation-sites r)) (conj :wrong-sites)
      (not= masking/capacity-cost-basis-points
            (:capacity-cost-basis-points r))
      (conj :wrong-capacity-cost)
      (not= six/family-size (:primary-contrast-family-size r))
      (conj :wrong-family-size)
      (not= :locus (:inferential-unit r)) (conj :wrong-inferential-unit)
      (not= :failed-recorded-not-gating (:shared-tape-context-result r))
      (conj :wrong-context-amendment)
      (> (:estimated-minutes r) (:budget-minutes r)) (conj :over-budget)
      (not= (:budget-minutes r) (:teardown-deadline-minutes r))
      (conj :deadline-does-not-match-budget))))

(def required-smoke-booleans
  [:panel-rederived :base-genome-matched :all-arms-observed
   :treatment-separated :paired-environment-tape-site-schedule
   :within-locus-aggregation-valid :context-failure-recorded
   :deterministic-rerun :positive-control-passed :artifacts-complete
   :artifacts-checksummed])

(defn expected-units-per-arm [environment-seeds]
  (* (count masking/registered-panel) (count environment-seeds)
     (count six/discovery-rewrite-tapes) (count masking/evaluation-sites)))

(defn smoke-failures [registration smoke]
  (let [expected (expected-units-per-arm six/pilot-environment-seeds)]
    (cond-> []
      (not= :baldwin-masking-six-arm-smoke (:kind smoke))
      (conj :wrong-smoke-kind)
      (not= 1 (:schema smoke)) (conj :wrong-smoke-schema)
      (not= (:implementation-revision registration)
            (:implementation-revision smoke))
      (conj :smoke-revision-mismatch)
      (not= (:lean-revision registration) (:lean-revision smoke))
      (conj :smoke-lean-mismatch)
      (not= (:discovery-map-sha256 registration) (:map-sha256 smoke))
      (conj :smoke-map-mismatch)
      (not= (:source-record-sha256 registration) (:record-sha256 smoke))
      (conj :smoke-record-mismatch)
      (not= six/pilot-environment-seeds (:environment-seeds smoke))
      (conj :smoke-wrong-environment-seeds)
      (not= six/discovery-rewrite-tapes (:discovery-rewrite-tapes smoke))
      (conj :smoke-wrong-discovery-tapes)
      (not= six/novel-rewrite-tapes (:novel-rewrite-tapes smoke))
      (conj :smoke-wrong-novel-tapes)
      (not= masking/evaluation-sites (:evaluation-sites smoke))
      (conj :smoke-wrong-sites)
      (not= (vec (repeat (count six/arms) expected)) (:raw-units-per-arm smoke))
      (conj :smoke-wrong-unit-count)
      (some #(not= true (get smoke %)) required-smoke-booleans)
      (conj :smoke-observation-failed)
      (= true (:deadline-exceeded smoke)) (conj :smoke-deadline-exceeded))))

(defn report [registration smoke]
  (let [registration-problems (registration-failures registration)
        smoke-problems (if smoke (smoke-failures registration smoke)
                           [:smoke-absent])
        status-problems (if (= :smoke-passed (:implementation-status registration))
                          [] [:implementation-not-admitted])
        failures (into (into registration-problems smoke-problems) status-problems)]
    {:kind :baldwin-masking-six-arm-validation
     :schema 1
     :valid-registration? (empty? registration-problems)
     :smoke-passed? (empty? smoke-problems)
     :implementation-status (:implementation-status registration)
     :launchable? (empty? failures)
     :failures failures}))

(defn -main [& [registration-path smoke-path]]
  (when-not registration-path
    (throw (ex-info "usage: validate SIX_ARM_REGISTRATION [SMOKE]" {})))
  (let [registration (read-edn registration-path)
        smoke (when smoke-path (read-edn smoke-path))
        result (report registration smoke)]
    (println (pr-str result))
    (when-not (:launchable? result) (System/exit 1))))
