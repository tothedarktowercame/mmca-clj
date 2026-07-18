(ns mmca.test-runner
  (:require [clojure.test :as test]
            [mmca.codex-3-test]
            [mmca.codex-8-test]
            [mmca.codex-4-test]
            [mmca.core-test]
            [mmca.codex-5-test]
            [mmca.rng-test]
            [mmca.paired-perturbation-test]
            [mmca.zai-5-test]))

(defn -main [& _]
  (let [{:keys [fail error]}
        (test/run-tests 'mmca.rng-test 'mmca.core-test 'mmca.codex-3-test
                        'mmca.codex-8-test
                        'mmca.codex-4-test
                        'mmca.paired-perturbation-test 'mmca.codex-5-test
                        'mmca.zai-5-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
