(ns mmca.codex-3-test
  (:require [clojure.test :refer [deftest is testing]]
            [mmca.core :as c]
            [mmca.experiments.codex-3 :as e3]))

(def tiny-config
  (assoc e3/default-config
         :seeds [0 1 2 3]
         :widths [12 18]
         :qs [0.25 1.0]
         :steps 30
         :checkpoints [5 10 20 30]))

(defn- trajectory-collapse-time [writing seed width q steps threshold]
  (let [run (c/run-propagator writing seed width steps {:interrupter-q q})]
    (first (keep-indexed (fn [t genotype]
                           (when (<= (c/distinct-rules genotype) threshold) t))
                         (:gen run)))))

(deftest deterministic-same-config
  (is (= (e3/run-experiment tiny-config)
         (e3/run-experiment tiny-config))
      "same q/seed/width/horizon produces identical census and fits"))

(deftest streaming-collapse-matches-engine-trajectory
  (doseq [q [0.25 1.0]]
    (testing (str "q=" q)
      (is (= (trajectory-collapse-time (:writing tiny-config) 7 18 q 40 8)
             (e3/collapse-time (:writing tiny-config) 7 18 q 40 8))
          "streaming census preserves the engine RNG tapes and trajectory"))))

(deftest collapse-definition-includes-initial-state
  (is (= 0 (e3/collapse-time (:writing tiny-config) 0 2 1.0 10 3))))

(deftest invalid-q-is-typed
  (let [error (try
                (e3/run-experiment (assoc tiny-config :qs [1.1]))
                nil
                (catch clojure.lang.ExceptionInfo e e))]
    (is (= :e3/invalid-q (:type (ex-data error))))))
