(ns mmca.baldwin-masking-intervention-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.baldwin-masking-intervention :as masking]))

(def base-genome
  {:gamma 1.0
   :update-prob 1.0
   :field (vec (range 80))
   :mask (vec (repeat 80 true))
   :hold (vec (repeat 80 false))})

(def entry
  {:stratum :test :locus 3 :current-rule 3 :good-rule 99 :bad-rule 12
   :hamming-distance 0})

(deftest interventions-are-exact
  (is (= [3 false]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (masking/intervene base-genome entry :plastic-current))))
  (is (= [3 true]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (masking/intervene base-genome entry :held-current))))
  (is (= [99 false]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (masking/intervene base-genome entry :plastic-good))))
  (is (= [99 true]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (masking/intervene base-genome entry :held-good))))
  (is (= [12 true]
         ((juxt #(get (:field %) 3) #(get (:hold %) 3))
          (masking/intervene base-genome entry :held-bad)))))

(deftest exact-familywise-boundaries
  (is (masking/familywise-win? {:wins 13 :losses 3 :ties 0}))
  (is (not (masking/familywise-win? {:wins 12 :losses 4 :ties 0})))
  (is (not (masking/familywise-win? {:wins 0 :losses 0 :ties 16}))))

(deftest classifier-does-not-convert-null-to-equality
  (let [pass {:wins 13 :losses 3 :ties 0}
        fail {:wins 12 :losses 4 :ties 0}
        contrasts {:good-held-vs-current-held pass
                   :good-held-vs-bad-held pass
                   :good-held-vs-plastic-good pass
                   :plastic-good-vs-plastic-current fail
                   :held-current-vs-plastic-current fail}]
    (is (= :joint-only-detected (masking/classify contrasts)))))

(deftest row-validation-rejects-absence
  (testing "an absent cell cannot become negative evidence"
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"evaluation cells differ"
         (masking/validate-rows! [] masking/pilot-seeds)))))
