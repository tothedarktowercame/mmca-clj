(ns mmca.test-runner
  (:require [clojure.test :as test]
            [mmca.rng-test]
            [mmca.core-test]
            [mmca.eoc-sweep-test]
            [mmca.figures-test]
            [mmca.paired-perturbation-test]
            [mmca.control-param-scan-test]
            [mmca.transient-scaling-test]
            [mmca.directed-predictive-info-test]
            [mmca.local-causal-states-test]
            [mmca.multiscale-spectra-test]
            [mmca.direct-computation-test]
            [mmca.river-feedback-test]
            [mmca.baldwin-spec-test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests 'mmca.rng-test 'mmca.core-test 'mmca.eoc-sweep-test
                        'mmca.figures-test
                        'mmca.paired-perturbation-test
                        'mmca.control-param-scan-test
                        'mmca.transient-scaling-test
                        'mmca.directed-predictive-info-test
                        'mmca.local-causal-states-test
                        'mmca.multiscale-spectra-test
                        'mmca.baldwin-spec-test
                        'mmca.direct-computation-test
                        'mmca.river-feedback-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
