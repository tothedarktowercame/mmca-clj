(ns baldwin-context-stationarity
  "Re-index learned-rule stability by phenotype context rather than cell."
  (:require [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]))

(def fixed-p0
  "01011111101100010000000111010011001010110101011111011001100001010010111111100101")

(defn -main [& [record-path output-path seed-count-s]]
  (when-not (and record-path output-path)
    (throw (ex-info
            "usage: baldwin_context_stationarity.clj RECORD OUTPUT [SEEDS]" {})))
  (let [seed-count (Long/parseLong (or seed-count-s "8"))
        _ (when-not (<= 3 seed-count 64)
            (throw (ex-info "seed count outside [3,64]" {:seeds seed-count})))
        genome (selection/best-genome-from-record record-path)
        seeds (vec (range 1 (inc seed-count)))
        profiles
        (mapv (fn [seed]
                {:seed seed
                 :variable
                 (mapv (fn [[k v]] (mechanism/summarize-context k v))
                       (sort-by key
                                (mechanism/context-profile genome seed seed nil)))
                 :fixed-p0
                 (mapv (fn [[k v]] (mechanism/summarize-context k v))
                       (sort-by key
                                (mechanism/context-profile genome seed seed fixed-p0)))})
              seeds)
        contexts
        (mapv
         (fn [k]
           (let [rows (mapv #(nth (:variable %) k) profiles)
                 fixed-rows (mapv #(nth (:fixed-p0 %) k) profiles)]
             {:context k
              :variable-modal-rules (mapv :modal-rule rows)
              :fixed-p0-modal-rules (mapv :modal-rule fixed-rows)
              :variable-mean-modal-share
              (mechanism/mean (map :modal-share rows))
              :fixed-p0-mean-modal-share
              (mechanism/mean (map :modal-share fixed-rows))
              :variable-all-seeds-same-modal?
              (apply = (map :modal-rule rows))
              :fixed-p0-all-seeds-same-modal?
              (apply = (map :modal-rule fixed-rows))}))
         (range 16))
        report {:kind :baldwin-context-stationarity
                :schema 1 :record record-path :seeds seeds :fixed-p0 fixed-p0
                :profiles profiles :contexts contexts}]
    (spit output-path (str (pr-str report) "\n"))
    (println
     (pr-str
      {:kind (:kind report)
       :variable-stable-contexts
       (count (filter :variable-all-seeds-same-modal? contexts))
       :fixed-p0-stable-contexts
       (count (filter :fixed-p0-all-seeds-same-modal? contexts))
       :variable-mean-modal-share
       (mechanism/mean (map :variable-mean-modal-share contexts))
       :fixed-p0-mean-modal-share
       (mechanism/mean (map :fixed-p0-mean-modal-share contexts))}))))

(apply -main *command-line-args*)
