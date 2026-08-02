(ns mmca.baldwin-masking-six-arm-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-six-arm :as six]
            [mmca.baldwin-selection :as selection]))

(def base-genome
  {:gamma 1.0
   :update-prob 1.0
   :field (vec (range 80))
   :mask (vec (repeat 80 true))
   :hold (vec (repeat 80 false))})

(def entry
  {:stratum :test :locus 3 :current-rule 3 :good-rule 99 :bad-rule 12
   :hamming-distance 0})

(deftest sixth-arm-holds-good-rule-on-novel-tapes
  (is (= [99 true]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (six/intervene base-genome entry :held-good-novel-tape))))
  (is (= six/novel-rewrite-tapes
         (six/tapes-for-arm :held-good-novel-tape)))
  (doseq [arm (remove #{:held-good-novel-tape} six/arms)]
    (is (= six/discovery-rewrite-tapes (six/tapes-for-arm arm)))))

(deftest amended-schedule-is-complete-and-disjoint
  (is (empty? (set/intersection (set six/discovery-rewrite-tapes)
                                (set six/novel-rewrite-tapes))))
  (is (= (* (count masking/registered-panel) (count six/arms)
            (count six/pilot-environment-seeds)
            (count six/discovery-rewrite-tapes)
            (count masking/evaluation-sites))
         (count (six/schedule six/pilot-environment-seeds))))
  (is (= 1152
         (/ (count (six/schedule six/pilot-environment-seeds))
            (count six/arms)))))

(deftest amended-familywise-boundaries
  (is (six/familywise-win? {:wins 14 :losses 2 :ties 0}))
  (is (not (six/familywise-win? {:wins 13 :losses 3 :ties 0}))))

(deftest batched-sites-equal-independent-evaluator
  (let [genome (six/intervene base-genome entry :held-good)
        sites [4 12]
        batched (six/reach-by-site genome 7 11 sites)]
    (doseq [site sites]
      (is (= (:mean (selection/reach-separated genome [[7 11]] [site]))
             (batched site))))))

(deftest tape-degradation-has-classification-precedence
  (let [pass {:wins 14 :losses 2 :ties 0}
        fail {:wins 13 :losses 3 :ties 0}
        contrasts {:good-held-vs-current-held pass
                   :good-held-vs-bad-held pass
                   :good-held-vs-plastic-good pass
                   :plastic-good-vs-plastic-current fail
                   :discovery-held-good-vs-novel-held-good pass
                   :held-current-vs-plastic-current fail}]
    (is (= :tape-degradation-detected (six/classify contrasts)))))

(deftest row-validation-rejects-absence
  (testing "an absent cell cannot become negative evidence"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"evaluation cells differ"
         (six/validate-rows! [] six/pilot-environment-seeds)))))

(def banked-readout-analysis
  (delay
    (six/readout-analysis
     (six/read-edn-lines
      "data/baldwin-runs/masking-six-arm-smoke-fc8bfca/a/raw.edn"))))

(deftest every-readout-and-unit-estimate-is-always-emitted
  (let [analysis @banked-readout-analysis
        audited (:contrasts
                 (read-string
                  (slurp
                   "data/baldwin-runs/masking-six-arm-smoke-fc8bfca/readout-reanalysis.edn")))]
    (is (= audited (:contrasts-by-field analysis)))
    (is (= (set six/readout-fields)
           (set (keys (:unit-level-estimates analysis)))))
    (doseq [[_ estimates] (:unit-level-estimates analysis)
            [_ estimate] estimates]
      (is (= 1152 (:units estimate)))
      (is (= 1152 (+ (:wins estimate) (:losses estimate) (:ties estimate)))))
    (is (:score-decomposition-valid analysis))))

(deftest banked-capacity-refund-fires-all-readout-gates
  (let [analysis @banked-readout-analysis
        good-plastic
        (get-in analysis
                [:additive-term-attribution
                 :good-held-vs-plastic-good :capacity-cost])
        current-plastic
        (get-in analysis
                [:additive-term-attribution
                 :held-current-vs-plastic-current :capacity-cost])
        held-control
        (get-in analysis
                [:additive-term-attribution
                 :good-held-vs-current-held :capacity-cost])]
    (is (= #{:good-held-vs-plastic-good :held-current-vs-plastic-current}
           (set (map :contrast (:readout-disagreements analysis)))))
    (is (= #{:good-held-vs-plastic-good :held-current-vs-plastic-current}
           (set (map :contrast (:production-bar-disagreements analysis)))))
    (is (= [859 562] ((juxt :unit-wins :wins-within-offset) good-plastic)))
    (is (= [860 575] ((juxt :unit-wins :wins-within-offset) current-plastic)))
    (is (= [323 0 0.0]
           ((juxt :unit-wins :wins-within-offset :constant-offset)
            held-control)))
    (is (= #{:good-held-vs-plastic-good :held-current-vs-plastic-current}
           (set (map :contrast (:constant-offset-failures analysis)))))))

(deftest score-decomposition-is-gating-evidence
  (is (not (six/score-decomposition-valid?
            [{:fitness 1.0 :band 1.0 :dependence 1.0}]))))
