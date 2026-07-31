(ns treatment-separation
  "Refuse a second cost arm when it induces the same selection ordering."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [mmca.baldwin-artifacts :as artifacts]))

(let [[record-path start-generation-s cost-a-s cost-b-s] *command-line-args*]
  (when-not (every? some? [record-path start-generation-s cost-a-s cost-b-s])
    (throw (ex-info
            "usage: treatment_separation.clj RECORD START-GENERATION COST-A COST-B"
            {:args *command-line-args*})))
  (let [start-generation (Long/parseLong start-generation-s)
        cost-a (Double/parseDouble cost-a-s)
        cost-b (Double/parseDouble cost-b-s)
        records (->> (str/split-lines (slurp record-path))
                     (remove str/blank?)
                     (map edn/read-string))
        result (artifacts/treatment-separation
                records start-generation cost-a cost-b)]
    (println (pr-str result))
    (when (:ranking-equivalent? result)
      (throw (ex-info "treatment is inert throughout the observed trajectory"
                      {:start-generation start-generation
                       :cost-a cost-a :cost-b cost-b})))))
