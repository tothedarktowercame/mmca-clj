(ns mmca.baldwin-masking-preregistration
  "Fail-closed registration and smoke validator for the masking intervention."
  (:require [clojure.edn :as edn]
            [clojure.set :as set]
            [clojure.string :as str]
            [mmca.baldwin-masking-intervention :as masking]))

(defn read-edn [path] (edn/read-string (slurp path)))

(defn registration-failures [r]
  (let [pilot (set (:pilot-seeds r))
        confirmation (set (:confirmation-seeds r))]
    (cond-> []
      (not= :baldwin-masking-intervention-preregistration (:kind r)) (conj :wrong-kind)
      (not= 1 (:schema r)) (conj :wrong-schema)
      (not= masking/lean-revision (:lean-revision r)) (conj :wrong-lean-revision)
      (str/blank? (:implementation-revision r)) (conj :missing-implementation-revision)
      (not= masking/discovery-revision (:discovery-revision r)) (conj :wrong-discovery-revision)
      (not= masking/discovery-map-sha256 (:discovery-map-sha256 r)) (conj :wrong-map-sha)
      (not= masking/registered-panel (:panel r)) (conj :wrong-panel)
      (not= masking/arms (:arms r)) (conj :wrong-arms)
      (not= masking/pilot-seeds (:pilot-seeds r)) (conj :wrong-pilot-seeds)
      (not= masking/confirmation-seeds (:confirmation-seeds r)) (conj :wrong-confirmation-seeds)
      (seq (set/intersection pilot confirmation)) (conj :seed-reuse)
      (not= masking/evaluation-sites (:evaluation-sites r)) (conj :wrong-sites)
      (not= masking/capacity-cost-basis-points (:capacity-cost-basis-points r))
      (conj :wrong-capacity-cost)
      (not= masking/family-size (:primary-contrast-family-size r)) (conj :wrong-family-size)
      (not= :locus (:inferential-unit r)) (conj :wrong-inferential-unit)
      (> (:estimated-minutes r) (:budget-minutes r)) (conj :over-budget)
      (not= (:budget-minutes r) (:teardown-deadline-minutes r))
      (conj :deadline-does-not-match-budget))))

(def required-smoke-booleans
  [:panel-rederived :base-genome-matched :all-arms-observed
   :treatment-separated :paired-schedule :deterministic-rerun
   :positive-control-passed :artifacts-complete :artifacts-checksummed])

(defn smoke-failures [registration smoke]
  (cond-> []
    (not= :baldwin-masking-intervention-smoke (:kind smoke)) (conj :wrong-smoke-kind)
    (not= 1 (:schema smoke)) (conj :wrong-smoke-schema)
    (not= (:implementation-revision registration) (:implementation-revision smoke))
    (conj :smoke-revision-mismatch)
    (not= (:lean-revision registration) (:lean-revision smoke)) (conj :smoke-lean-mismatch)
    (not= (:discovery-map-sha256 registration) (:map-sha256 smoke)) (conj :smoke-map-mismatch)
    (not= (:source-record-sha256 registration) (:record-sha256 smoke))
    (conj :smoke-record-mismatch)
    (not= masking/pilot-seeds (:evaluation-seeds smoke)) (conj :smoke-wrong-seeds)
    (not= masking/evaluation-sites (:evaluation-sites smoke)) (conj :smoke-wrong-sites)
    (not= (* (count masking/registered-panel) (count masking/pilot-seeds)
             (count masking/evaluation-sites))
          (:raw-units-per-arm smoke))
    (conj :smoke-wrong-unit-count)
    (some #(not= true (get smoke %)) required-smoke-booleans)
    (conj :smoke-observation-failed)
    (= true (:deadline-exceeded smoke)) (conj :smoke-deadline-exceeded)))

(defn report [registration smoke]
  (let [registration-problems (registration-failures registration)
        smoke-problems (if smoke (smoke-failures registration smoke) [:smoke-absent])
        status-problems (if (= :smoke-passed (:implementation-status registration))
                          [] [:implementation-not-admitted])
        failures (into (into registration-problems smoke-problems) status-problems)]
    {:kind :baldwin-masking-preregistration-validation
     :schema 1
     :valid-registration? (empty? registration-problems)
     :smoke-passed? (empty? smoke-problems)
     :implementation-status (:implementation-status registration)
     :launchable? (empty? failures)
     :failures failures}))

(defn -main [& [registration-path smoke-path]]
  (when-not registration-path
    (throw (ex-info "usage: validate REGISTRATION [SMOKE]" {})))
  (let [registration (read-edn registration-path)
        smoke (when smoke-path (read-edn smoke-path))
        result (report registration smoke)]
    (println (pr-str result))
    (when-not (:valid-registration? result) (System/exit 1))))
