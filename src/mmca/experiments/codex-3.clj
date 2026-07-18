#!/usr/bin/env clojure

(require '[mmca.experiments.codex-3 :as e3])

(defn -main [& args]
  (apply e3/-main args))

(apply -main *command-line-args*)
