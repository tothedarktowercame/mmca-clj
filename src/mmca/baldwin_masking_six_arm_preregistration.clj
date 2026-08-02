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
      (not= 2 (:schema r)) (conj :wrong-schema)
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
      (not= :strict-sign-majority-of-paired-environment-tape-site-deltas-per-readout
            (:within-locus-rule r))
      (conj :wrong-within-locus-rule)
      (not= six/readout-fields (:readout-fields r)) (conj :wrong-readout-fields)
      (not= six/behavioral-field (:behavioral-readout r))
      (conj :wrong-behavioral-readout)
      (not= six/score-additive-terms (:score-additive-terms r))
      (conj :wrong-score-additive-terms)
      (not= six/constant-offset-win-fraction-threshold
            (:constant-offset-win-fraction-threshold r))
      (conj :wrong-constant-offset-threshold)
      (not= :paired-unit-mean-delta-with-locus-wlt
            (:secondary-unit-level-analysis r))
      (conj :wrong-unit-level-analysis)
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
   :artifacts-checksummed :score-decomposition-valid :readout-gates-passed])

(defn expected-units-per-arm [environment-seeds]
  (* (count masking/registered-panel) (count environment-seeds)
     (count six/discovery-rewrite-tapes) (count masking/evaluation-sites)))

(defn- complete-contrast-table? [table]
  (and (= (set six/readout-fields) (set (keys table)))
       (every?
        (fn [[_ contrasts]]
          (and (= (set (keys six/contrast-pairs)) (set (keys contrasts)))
               (every? #(= (count masking/registered-panel)
                           (reduce + (vals %)))
                       (vals contrasts))))
        table)))

(defn- complete-unit-level? [table expected]
  (and (= (set six/readout-fields) (set (keys table)))
       (every?
        (fn [[_ estimates]]
          (and (= (set (keys six/contrast-pairs)) (set (keys estimates)))
               (every? #(and (= expected (:units %))
                             (= expected (+ (:wins %) (:losses %) (:ties %))))
                       (vals estimates))))
        table)))

(defn- valid-attribution-result? [term result]
  (and (= (:field term) (:field result))
       (= (:coefficient term) (:coefficient result))
       (number? (:left-mean result))
       (number? (:right-mean result))
       (boolean? (:constant? result))
       (if (:constant? result)
         (number? (:constant-offset result))
         (nil? (:constant-offset result)))
       (nat-int? (:unit-wins result))
       (nat-int? (:wins-within-offset result))
       (<= (:wins-within-offset result) (:unit-wins result))
       (number? (:win-fraction-within-offset result))
       (<= 0.0 (:win-fraction-within-offset result) 1.0)))

(defn- complete-attribution? [table]
  (and (= (set (keys six/contrast-pairs)) (set (keys table)))
       (every?
        (fn [[_ results]]
          (and (= (set (keys six/score-additive-terms)) (set (keys results)))
               (every? (fn [[term-name result]]
                         (valid-attribution-result?
                          (six/score-additive-terms term-name) result))
                       results)))
        table)))

(defn smoke-failures [registration smoke]
  (let [expected (expected-units-per-arm six/pilot-environment-seeds)
        contrast-table-valid? (complete-contrast-table? (:contrasts-by-field smoke))
        attribution-valid? (complete-attribution? (:additive-term-attribution smoke))
        expected-disagreements
        (if contrast-table-valid?
          (six/readout-disagreements (:contrasts-by-field smoke)) [])
        expected-production-disagreements
        (if contrast-table-valid?
          (six/production-bar-disagreements (:contrasts-by-field smoke)) [])
        expected-offset-failures
        (if attribution-valid?
          (six/constant-offset-failures
           (:additive-term-attribution smoke)
           (:constant-offset-win-fraction-threshold registration)) [])]
    (cond-> []
      (not= :baldwin-masking-six-arm-smoke (:kind smoke))
      (conj :wrong-smoke-kind)
      (not= 2 (:schema smoke)) (conj :wrong-smoke-schema)
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
      (not= six/readout-fields (:readout-fields smoke))
      (conj :smoke-wrong-readout-fields)
      (not contrast-table-valid?)
      (conj :smoke-readout-table-incomplete)
      (not (complete-unit-level? (:unit-level-estimates smoke) expected))
      (conj :smoke-unit-level-estimates-incomplete)
      (not attribution-valid?)
      (conj :smoke-additive-attribution-incomplete)
      (not= expected-disagreements (:readout-disagreements smoke))
      (conj :smoke-readout-disagreement-report-mismatch)
      (seq expected-disagreements) (conj :readout-disagreement)
      (not= expected-production-disagreements
            (:production-bar-disagreements smoke))
      (conj :smoke-production-disagreement-report-mismatch)
      (seq expected-production-disagreements)
      (conj :readout-production-bar-disagreement)
      (not= (:constant-offset-win-fraction-threshold registration)
            (:constant-offset-win-fraction-threshold smoke))
      (conj :smoke-constant-offset-threshold-mismatch)
      (not= expected-offset-failures (:constant-offset-failures smoke))
      (conj :smoke-constant-offset-report-mismatch)
      (seq expected-offset-failures)
      (conj :constant-offset-dominance)
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
     :schema 2
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
