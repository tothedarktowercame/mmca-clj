(ns mmca.baldwin-masking-six-arm-preregistration-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.baldwin-masking-six-arm :as six]
            [mmca.baldwin-masking-six-arm-preregistration :as prereg]))

(def registration
  (prereg/read-edn
   "holes/BALDWIN-MASKING-SIX-ARM-AMENDED-PREREGISTRATION.edn"))

(def neutral-contrast {:wins 8 :losses 8 :ties 0})
(def neutral-unit-estimate
  {:units 1152 :mean-delta 0.0 :wins 576 :losses 576 :ties 0})
(def neutral-attribution
  {:behavior {:field :band :coefficient 1.0
              :left-mean 0.5 :right-mean 0.5 :constant? false
              :constant-offset nil :unit-wins 576 :wins-within-offset 0
              :win-fraction-within-offset 0.0}
   :capacity-cost {:field :dependence :coefficient -0.05
                   :left-mean -0.05 :right-mean -0.05 :constant? true
                   :constant-offset 0.0 :unit-wins 576 :wins-within-offset 0
                   :win-fraction-within-offset 0.0}})

(def neutral-contrasts
  (zipmap (keys six/contrast-pairs) (repeat neutral-contrast)))

(def passing-smoke
  (merge {:kind :baldwin-masking-six-arm-smoke
          :schema 2
          :implementation-revision (:implementation-revision registration)
          :lean-revision six/lean-revision
          :map-sha256 (:discovery-map-sha256 registration)
          :record-sha256 (:source-record-sha256 registration)
          :environment-seeds six/pilot-environment-seeds
          :discovery-rewrite-tapes six/discovery-rewrite-tapes
          :novel-rewrite-tapes six/novel-rewrite-tapes
          :evaluation-sites (:evaluation-sites registration)
          :raw-units-per-arm (vec (repeat 6 1152))
          :readout-fields six/readout-fields
          :contrasts-by-field
          (zipmap six/readout-fields (repeat neutral-contrasts))
          :unit-level-estimates
          (zipmap six/readout-fields
                  (repeat (zipmap (keys six/contrast-pairs)
                                  (repeat neutral-unit-estimate))))
          :additive-term-attribution
          (zipmap (keys six/contrast-pairs) (repeat neutral-attribution))
          :readout-disagreements []
          :production-bar-disagreements []
          :constant-offset-win-fraction-threshold
          six/constant-offset-win-fraction-threshold
          :constant-offset-failures []
          :deadline-exceeded false}
         (zipmap prereg/required-smoke-booleans (repeat true))))

(deftest exact-registration-is-valid-but-awaits-smoke-admission
  (is (empty? (prereg/registration-failures registration)))
  (is (false? (:launchable? (prereg/report registration nil)))))

(deftest reviewed-registration-cannot-launch-on-old-smoke
  (is (= :design-review-hold (:implementation-status registration)))
  (is (false? (:launchable? (prereg/report registration passing-smoke))))
  (is (some #{:implementation-not-admitted}
            (:failures (prereg/report registration passing-smoke)))))

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

(deftest validator-recomputes-readout-disagreement
  (let [bad-table (-> (:contrasts-by-field passing-smoke)
                      (assoc-in [:fitness :good-held-vs-plastic-good]
                                {:wins 14 :losses 2 :ties 0})
                      (assoc-in [:band :good-held-vs-plastic-good]
                                {:wins 6 :losses 7 :ties 3}))
        failures (prereg/smoke-failures
                  registration (assoc passing-smoke :contrasts-by-field bad-table))]
    (is (some #{:smoke-readout-disagreement-report-mismatch} failures))
    (is (some #{:readout-disagreement} failures))
    (is (some #{:readout-production-bar-disagreement} failures))))

(deftest validator-recomputes-constant-offset-dominance
  (let [bad-attribution
        (assoc-in (:additive-term-attribution passing-smoke)
                  [:good-held-vs-plastic-good :capacity-cost]
                  {:field :dependence :coefficient -0.05
                   :left-mean -0.049375 :right-mean -0.05 :constant? true
                   :constant-offset 0.000625 :unit-wins 100
                   :wins-within-offset 21 :win-fraction-within-offset 0.21})
        failures (prereg/smoke-failures
                  registration
                  (assoc passing-smoke :additive-term-attribution bad-attribution))]
    (is (some #{:smoke-constant-offset-report-mismatch} failures))
    (is (some #{:constant-offset-dominance} failures))))
