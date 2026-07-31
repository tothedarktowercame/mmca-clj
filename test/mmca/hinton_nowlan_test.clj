(ns mmca.hinton-nowlan-test
  (:require [clojure.test :refer [deftest is]]
            [mmca.hinton-nowlan :as hn]))

(deftest planted-target-has-the-intended-baldwin-path
  (let [all-plastic (vec (repeat (count hn/target) :?))
        one-fixed (assoc all-plastic 0 (first hn/target))
        wrong (assoc all-plastic 0 (- 1 (first hn/target)))]
    (is (= 1.0 (hn/learned-function all-plastic)))
    (is (= 1.0 (hn/learned-function one-fixed)))
    (is (> (hn/expected-learning-score one-fixed)
           (hn/expected-learning-score all-plastic)))
    (is (zero? (hn/learned-function wrong)))))

(deftest evolutionary-apparatus-passes-positive-control
  (let [trajectory (hn/run {})]
    (is (hn/positive-control-passes? trajectory))
    (is (zero? (:best-plastic (last trajectory))))))
