(ns mmca.test-runner
  (:require [clojure.test :as test]
            [mmca.core-test]
            [mmca.rng-test]
            [mmca.paired-perturbation-test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests 'mmca.rng-test 'mmca.core-test 'mmca.paired-perturbation-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
