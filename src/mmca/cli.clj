(ns mmca.cli
  "Small EDN command-line seam for programmatic standalone runs."
  (:require [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [mmca.core :as mmca]))

(defn- run [{:keys [mode writing seed width steps]
             :or {mode :blending seed 0 width 80 steps 120}}]
  (case mode
    :blending (mmca/run-propagator writing seed width steps)
    :alone (mmca/run-propagator-alone writing seed width steps)
    :river (mmca/run-river seed width steps)
    (throw (ex-info "Unknown run mode" {:mode mode}))))

(defn -main [& args]
  (when-not (= 1 (count args))
    (throw (ex-info "Pass one EDN run specification"
                    {:example "{:mode :blending :writing [2 3 4 5 6 7 0 1] :seed 0 :width 80 :steps 120}"})))
  (let [result (run (edn/read-string (first args)))]
    (pprint/pprint result)))
