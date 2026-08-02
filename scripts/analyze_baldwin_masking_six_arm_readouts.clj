(ns analyze-baldwin-masking-six-arm-readouts
  (:require [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-masking-six-arm :as six]))

(defn contrasts-for [rows field]
  (let [scored (mapv #(assoc % :fitness (field %)) rows)]
    (into (sorted-map)
          (for [[name [left right]] six/contrast-pairs]
            [name (six/contrast scored left right)]))))

(defn constant-by-arm [rows field]
  (into (sorted-map)
        (for [[arm arm-rows] (group-by :arm rows)
              :let [values (set (map field arm-rows))]]
          (do
            (when-not (= 1 (count values))
              (throw (ex-info "arm field is not constant"
                              {:arm arm :field field :values values})))
            [arm (first values)]))))

(let [[raw-path map-path] *command-line-args*]
  (when-not (and raw-path map-path)
    (throw (ex-info "usage: analyze_readouts RAW_EDN ASSIMILATION_MAP_TSV" {})))
  (let [rows (six/read-edn-lines raw-path)
        map-rows (masking/read-map map-path)
        selectable (filter :selectable map-rows)
        band-useful (filter #(>= (:band %) (:base-band %)) map-rows)
        result
        {:kind :baldwin-masking-six-arm-readout-reanalysis
         :schema 1
         :source-raw-sha256 (six/sha256 raw-path)
         :classification :capacity-refund-confound
         :capacity-cost masking/capacity-cost
         :held-locus-fraction (/ 1.0 80.0)
         :constant-held-fitness-refund (/ masking/capacity-cost 80.0)
         :mean-dependence (constant-by-arm rows :dependence)
         :contrasts (into (sorted-map)
                          (for [field [:fitness :band :reach]]
                            [field (contrasts-for rows field)]))
         :endpoint-map-check
         {:fitness-selectable (count selectable)
          :band-at-least-baseline (count band-useful)
          :fitness-only (count (filter #(< (:band %) (:base-band %)) selectable))
          :exact-band-ties (count (filter #(= (:band %) (:base-band %)) selectable))}
         :recommendation :hold-and-preregister-behavior-or-realized-work-readout}]
    (println (pr-str result))))
