(ns validate-baldwin-run
  "Validate artifact shape and treatment invariants before a success marker exists."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [mmca.baldwin-artifacts :as artifacts]))

(let [[tsv-path record-path expected-mode generations-s population-s]
      *command-line-args*]
  (when-not (every? some? [tsv-path record-path expected-mode generations-s population-s])
    (throw (ex-info
            "usage: validate_baldwin_run.clj TSV RECORD MODE GENERATIONS POPULATION"
            {:args *command-line-args*})))
  (let [generations (Long/parseLong generations-s)
        population-size (Long/parseLong population-s)
        tsv-lines (str/split-lines (slurp tsv-path))
        records (->> (str/split-lines (slurp record-path))
                     (remove str/blank?)
                     (mapv edn/read-string))
        result (artifacts/validate-run-data!
                tsv-lines records expected-mode generations population-size)]
    (println (pr-str result))))
