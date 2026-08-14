(ns mmca.apm-demonstration-preregistration
  "Concrete runtime specification aligned by hand with
  DarkTower.APMDemonstration.round1Registration.  This is not a generator or
  a formal Lean-to-Clojure projection."
  (:require [clojure.edn :as edn]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(def required-lean-revision "d0623df8992ec23c7c647cf30eac014221bbdb2d")
(def required-lean-source "DarkTower/APMDemonstrationPreregistration.lean")
(def required-lean-registration
  "DarkTower.APMDemonstration.round1Registration")

(def required-modules [:R :F :S :A :M :P :X])
(def required-structural-invariants [:F1])
(def required-runtime-invariants [:F2 :F3 :F4 :F5 :F6 :F7 :F8 :F9])

(def required-capabilities
  [:registration-gates-launch :frame-containment-witnessed
   :created-frame-worked :unique-disposition :offer-use-disposition
   :need-retrieval :promotion-importable :promotion-need-taggable
   :measurement-populated])

(def required-measurement-fields
  ["statement defects at review" "terminal disposition"
   "residual executable sorries" "attempts or closer hops"
   "axiom cleanliness" "memories promoted" "review escape rate"
   "promoted then surfaced then used" "contract leaks"
   "duplicate declarations" "locked-lemma exposure" "promotion coverage"
   "unconsumed promotions" "import-only edges" "scribe lane coverage"
   "arc-lane yield" "rewrite rule offered and used"])

(def required-registration-keys
  [:kind :schema :lean-registration :lean-source :lean-revision :modules
   :structural-invariants :runtime-invariants :problem :variation
   :claim :arms :replication-stage :pilot-units :confirmation-units
   :estimated-cost :budget-cap :teardown-deadline :stop-rules :decision-rule
   :required-capabilities :required-measurement-fields])

(def required-trace-keys
  [:problem :frame :launch-gate-refused-without-witness? :cycle-closed?
   :disposition-ids :memory-offer-ids :memory-disposition-offer-ids
   :stratum-frozen-at :assigned-at :comparison-regimes
   :denominator-declared? :denominator-inferred-from-corpus?
   :available-artifact-ids :need-probe-retrieved-ids :containment-claimed?
   :containment-probe-recorded? :containment-probe-passed?
   :capability-probes :required-measurement-fields
   :measurement :promoted-artifact-ids
   :importable-promoted-artifact-ids :need-tagged-promoted-artifact-ids])

(defn read-edn [path]
  (edn/read-string (slurp path)))

(defn nonblank-string? [x]
  (and (string? x) (not (str/blank? x))))

(defn string-vector? [x]
  (and (vector? x) (every? string? x)))

(defn problem? [x]
  (and (map? x)
       (nonblank-string? (:problem-id x))
       (nonblank-string? (:difficulty-stratum x))
       (nonblank-string? (:regime x))
       (string-vector? (:locked-lemma-exposure x))))

(defn probe? [x]
  (and (map? x) (keyword? (:capability x))
       (nonblank-string? (:evidence-id x)) (boolean? (:recorded? x))))

(defn registration-shape-failures [registration]
  (if-not (map? registration)
    [:registration-not-map]
    (cond-> []
      (some #(not (contains? registration %)) required-registration-keys)
      (conj :registration-missing-required-key)
      (not (problem? (:problem registration)))
      (conj :malformed-problem)
      (not (and (map? (:variation registration))
                (#{:controlled :measured} (get-in registration [:variation :kind]))
                (nonblank-string? (get-in registration [:variation :endpoint]))))
      (conj :malformed-variation)
      (not (number? (:estimated-cost registration)))
      (conj :malformed-estimated-cost)
      (not (number? (:budget-cap registration)))
      (conj :malformed-budget-cap)
      (not (or (nil? (:teardown-deadline registration))
               (number? (:teardown-deadline registration))))
      (conj :malformed-teardown-deadline)
      (not (and (vector? (:stop-rules registration))
                (seq (:stop-rules registration))
                (every? keyword? (:stop-rules registration))))
      (conj :malformed-stop-rules)
      (not (and (map? (:decision-rule registration))
                (keyword? (get-in registration [:decision-rule :id]))
                (vector? (get-in registration [:decision-rule :outcomes]))
                (seq (get-in registration [:decision-rule :outcomes]))
                (every? keyword? (get-in registration
                                         [:decision-rule :outcomes]))))
      (conj :malformed-decision-rule))))

(defn trace-shape-failures [trace]
  (if-not (map? trace)
    [:trace-not-map]
    (cond-> []
      (some #(not (contains? trace %)) required-trace-keys)
      (conj :trace-missing-required-key)
      (not (problem? (:problem trace)))
      (conj :malformed-trace-problem)
      (not (and (map? (:frame trace))
                (nonblank-string? (get-in trace [:frame :scaffold-hash]))
                (nonblank-string? (get-in trace [:frame :closing-hash]))))
      (conj :malformed-frame)
      (not (and (vector? (:capability-probes trace))
                (every? probe? (:capability-probes trace))))
      (conj :malformed-capability-probes)
      (not (and (map? (:measurement trace))
                (map? (get-in trace [:measurement :meas/values]))
                (every? string? (keys (get-in trace
                                              [:measurement :meas/values])))
                (map? (get-in trace [:measurement :meas/unset]))
                (every? (fn [[field reason]]
                          (and (string? field) (nonblank-string? reason)))
                        (get-in trace [:measurement :meas/unset]))))
      (conj :malformed-measurement)
      (not (every? #(or (true? (get trace %)) (false? (get trace %)))
                   [:launch-gate-refused-without-witness? :cycle-closed?
                    :denominator-declared? :denominator-inferred-from-corpus?
                    :containment-claimed? :containment-probe-recorded?
                    :containment-probe-passed?]))
      (conj :malformed-trace-boolean))))

(defn lean-source-revision
  "Return the commit which last changed the named Lean source, or nil.  The
  repository location is an invocation input, not part of the registration."
  [lean-repo]
  (let [{:keys [exit out]}
        (shell/sh "git" "-C" lean-repo "log" "-n" "1" "--format=%H" "--"
                  required-lean-source)]
    (when (zero? exit) (not-empty (str/trim out)))))

(defn registration-content-failures [registration actual-lean-revision]
  (cond-> []
    (not= :apm-demonstration-round1-registration (:kind registration))
    (conj :wrong-kind)
    (not= 1 (:schema registration))
    (conj :wrong-schema)
    (not= required-lean-registration (:lean-registration registration))
    (conj :wrong-lean-registration)
    (not= required-lean-source (:lean-source registration))
    (conj :wrong-lean-source)
    (not= required-lean-revision (:lean-revision registration))
    (conj :wrong-lean-revision)
    (nil? actual-lean-revision)
    (conj :lean-source-revision-unavailable)
    (and actual-lean-revision
         (not= required-lean-revision actual-lean-revision))
    (conj :stale-lean-revision)
    (not= required-modules (:modules registration))
    (conj :wrong-modules)
    (not= required-structural-invariants (:structural-invariants registration))
    (conj :wrong-structural-invariants)
    (not= required-runtime-invariants (:runtime-invariants registration))
    (conj :wrong-runtime-invariants)
    (not= :descriptive (:claim registration))
    (conj :wrong-claim)
    (not= [{:name "one problem, one-shot measurement"
            :neutral? false :axes [] :role :treatment}]
          (:arms registration))
    (conj :wrong-arms)
    (not= :pilot (:replication-stage registration))
    (conj :wrong-replication-stage)
    (not= [(:problem registration)] (:pilot-units registration))
    (conj :wrong-pilot-units)
    (not= [] (:confirmation-units registration))
    (conj :pilot-has-confirmation-units)
    (not= required-capabilities (:required-capabilities registration))
    (conj :wrong-required-capabilities)
    (not= required-measurement-fields (:required-measurement-fields registration))
    (conj :wrong-required-measurement-fields)
    (and (number? (:estimated-cost registration))
         (number? (:budget-cap registration))
         (> (:estimated-cost registration) (:budget-cap registration)))
    (conj :over-budget)))

(defn subset? [xs ys]
  (every? (set ys) xs))

(defn exactly-one? [xs]
  (= 1 (count xs)))

(defn measurement-fields [trace]
  (let [measurement (:measurement trace)]
    (concat (keys (:meas/values measurement))
            (keys (:meas/unset measurement)))))

(defn capability-holds? [capability trace]
  (case capability
    :registration-gates-launch (:launch-gate-refused-without-witness? trace)
    :frame-containment-witnessed
    (or (not (:containment-claimed? trace))
        (and (:containment-probe-recorded? trace)
             (:containment-probe-passed? trace)))
    :created-frame-worked
    (not= (get-in trace [:frame :scaffold-hash])
          (get-in trace [:frame :closing-hash]))
    :unique-disposition
    (or (not (:cycle-closed? trace)) (exactly-one? (:disposition-ids trace)))
    :offer-use-disposition
    (subset? (:memory-offer-ids trace) (:memory-disposition-offer-ids trace))
    :need-retrieval
    (subset? (:available-artifact-ids trace) (:need-probe-retrieved-ids trace))
    :promotion-importable
    (subset? (:promoted-artifact-ids trace)
             (:importable-promoted-artifact-ids trace))
    :promotion-need-taggable
    (subset? (:promoted-artifact-ids trace)
             (:need-tagged-promoted-artifact-ids trace))
    :measurement-populated
    (subset? (:required-measurement-fields trace)
             (measurement-fields trace))
    false))

(defn recorded-capability? [capability trace]
  (some #(and (= capability (:capability %)) (:recorded? %)
              (nonblank-string? (:evidence-id %)))
        (:capability-probes trace)))

(defn trace-content-failures [registration trace]
  (let [caps (:required-capabilities registration)]
    (cond-> []
      (not= (:problem registration) (:problem trace))
      (conj :wrong-problem)
      (= (get-in trace [:frame :scaffold-hash])
         (get-in trace [:frame :closing-hash]))
      (conj :f1-scaffold-identical-frame)
      (and (:cycle-closed? trace) (not (exactly-one? (:disposition-ids trace))))
      (conj :f2-non-unique-disposition)
      (not (subset? (:memory-offer-ids trace)
                    (:memory-disposition-offer-ids trace)))
      (conj :f3-undispositioned-offer)
      (not (and (integer? (:stratum-frozen-at trace))
                (integer? (:assigned-at trace))
                (< (:stratum-frozen-at trace) (:assigned-at trace))))
      (conj :f4-stratum-not-frozen-before-assignment)
      (> (count (distinct (:comparison-regimes trace))) 1)
      (conj :f5-multiple-comparison-regimes)
      (or (not (:denominator-declared? trace))
          (:denominator-inferred-from-corpus? trace))
      (conj :f6-denominator-not-preregistered)
      (not (subset? (:available-artifact-ids trace)
                    (:need-probe-retrieved-ids trace)))
      (conj :f7-missed-available-artifact)
      (and (:containment-claimed? trace)
           (not (and (:containment-probe-recorded? trace)
                     (:containment-probe-passed? trace))))
      (conj :f8-unwitnessed-containment)
      (some #(not (capability-holds? % trace)) caps)
      (conj :f9-capability-not-realized)
      (some #(not (recorded-capability? % trace)) caps)
      (conj :f9-capability-probe-missing)
      (not (subset? (:promoted-artifact-ids trace)
                    (:importable-promoted-artifact-ids trace)))
      (conj :promotion-not-importable)
      (not (subset? (:promoted-artifact-ids trace)
                    (:need-tagged-promoted-artifact-ids trace)))
      (conj :promotion-not-need-taggable)
      (not (subset? required-measurement-fields
                    (measurement-fields trace)))
      (conj :measurement-field-claimed-without-value))))

(defn failures
  "Return every distinct diagnostic.  The three-argument arity permits a
  caller or test to supply the observed Lean source revision explicitly."
  ([registration trace lean-repo]
   (failures registration trace (lean-source-revision lean-repo) :observed))
  ([registration trace actual-lean-revision _observed]
   (vec (distinct
         (concat (registration-shape-failures registration)
                 (trace-shape-failures trace)
                 (when (map? registration)
                   (registration-content-failures registration
                                                  actual-lean-revision))
                 (when (and (map? registration) (map? trace))
                   (trace-content-failures registration trace)))))))

(defn report [registration trace lean-repo]
  (let [problems (failures registration trace lean-repo)]
    {:kind :apm-demonstration-round1-validation
     :lean-source-revision (lean-source-revision lean-repo)
     :failures problems
     :launchable? (empty? problems)}))
