(ns mmca.baldwin-spec-test
  "Persistent tests for the design invariants.

   Codex's item #7: these were an expensive ad hoc preflight recomputing the dial
   over 3 seeds x 80 sites plus an 8-level probe on EVERY run. As tests with fixed
   fixtures they are seconds, and they run in CI rather than as a tax on each
   experiment.

   Every test names the failure that motivated it. An invariant check written from
   the DESCRIPTION of the invariant rather than from its counterexample is how the
   first preflight came to pass the very defects it existed to catch."
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-spec :as spec]))

(deftest static-and-hold-all
  (testing "Lean Genome.IsStatic / holdAll / holdAll_field"
    (let [g {:field [1 2 3] :hold [false true false]}]
      (is (not (spec/static? g)))
      (is (spec/static? (spec/hold-all g)))
      (is (spec/hold-all-preserves-field? g)
          "holding must change no inherited rule"))))

(deftest linked-hgt-rejects-unlinked-splice
  (testing "Lean linkedHGT_sameDonor — the defect Codex found in the real hgt"
    (let [a {:field [1 2 3 4] :hold [true true false false]}
          b {:field [9 8 7 6] :hold [false false true true]}
          linked   {:field [1 8 7 4] :hold [true false true false]}
          unlinked {:field [1 8 7 4] :hold [true true false false]}]
      (is (spec/linked-hgt? a b linked))
      (is (not (spec/linked-hgt? a b unlinked))
          "field from one donor with hold from the other must FAIL; the first
           preflight tested key presence and passed this"))))

(deftest mutation-only-baseline-is-elitist
  (testing "Lean mutationOnlyHeld_closedForm — survivors are retained UNMUTATED"
    (is (< (Math/abs (- (spec/mutation-only-held 0.02 23) 0.1858)) 1e-3)
        "half(1-(1-mu)^n); the run's empirical null matches this within noise")
    (is (> (Math/abs (- (spec/mutation-only-held 0.02 23) 0.3045)) 0.1)
        "and NOT half(1-(1-2mu)^n), which assumes every member mutates")))

(deftest axis-navigability-rejects-the-spike
  (testing "Lean no_witness_of_degenerate, plus the cliff the naive test admits"
    (is (spec/axis-degenerate? [0 0 0 0]))
    (is (not (spec/axis-degenerate? [0 0 0 0.6])))
    (testing "a spike has two distinct values but only one gradient step"
      (is (= 1 (spec/gradient-steps [0 0 0 0 0 0 0 0.627])))
      (is (not (spec/axis-navigable? [0 0 0 0 0 0 0 0.627]))
          "this profile is the real gamma landscape; the first preflight passed it"))
    (is (spec/axis-navigable? [0 0.1 0.2 0.3]))))

(deftest tape-alignment-must-be-non-vacuous
  (testing "Lean LooseEvaluator.TapeAligned"
    (is (spec/tape-aligned? [[1 2 3] [1 2 3]] true))
    (is (not (spec/tape-aligned? [[1 2 3] [1 2 4]] true))
        "differing draws are a desynchronised tape")
    (is (not (spec/tape-aligned? [[1 2 3] [1 2 3]] false))
        "equal draws prove nothing if the branches never diverged")))

(deftest extension-must-reduce-to-its-reference
  (testing "Lean Extension.agrees — the 11.0083 vs 12.3875 defect"
    (is (spec/extension-agrees?
         [{:neutral? true :performance 12.3875 :reference 12.3875}
          {:neutral? true :performance 1.2833  :reference 1.2833}] 1e-4))
    (is (seq (spec/extension-failures
              [{:neutral? true :performance 11.0083 :reference 12.3875 :label "gamma=1"}] 1e-4))
        "an extension that reproduces byte-for-byte can still be unfaithful")))

(deftest ranking-equivalence-detects-inert-treatments
  (testing "Lean rankingEquivalent_sub_const — why four cost arms coincided"
    (is (spec/ranking-equivalent? [0.6 0.4 0.2] (map #(- % 0.05) [0.6 0.4 0.2]))
        "a constant shift preserves selection order, so the treatment cannot act")
    (is (not (spec/ranking-equivalent? [0.6 0.4 0.2] [0.2 0.4 0.6])))))

(deftest witness-names-the-failing-condition
  (testing "Lean BaldwinWitness — the checker must say WHICH condition fails"
    (let [ok [{:genome {:field [1 1] :hold [false false]} :performance 0.9 :dependence 1.0}
              {:genome {:field [1 1] :hold [true false]}  :performance 0.9 :dependence 0.5}
              {:genome {:field [1 1] :hold [true true]}   :performance 0.9 :dependence 0.0
               :inherited-performance 0.9}]
          valley (assoc-in (vec ok) [1 :performance] 0.1)
          flat [{:genome {:field [1 1] :hold [false false]} :performance 0.9 :dependence 1.0}
                {:genome {:field [1 1] :hold [true true]}   :performance 0.9 :dependence 1.0
                 :inherited-performance 0.9}]]
      (is (spec/baldwin-witness? ok 0.5 (constantly true)))
      (is (some #{:inherited-function}
                (spec/witness-failures (update (vec ok) 2 dissoc :inherited-performance)
                                       0.5 (constantly true)))
          "raw function may not stand in for the measured all-held endpoint")
      (is (some #{:high-function} (spec/witness-failures valley 0.5 (constantly true)))
          "a path crossing a valley must be named as such")
      (is (some #{:strict-assimilation} (spec/witness-failures flat 0.5 (constantly true)))))))
