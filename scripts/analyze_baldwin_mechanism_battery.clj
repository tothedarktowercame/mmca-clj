(ns analyze-baldwin-mechanism-battery
  "Validate and classify the paid Baldwin mechanism diagnostics."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [mmca.baldwin-mechanism :as mechanism]))

(def expected-probes 640)
(def expected-map-rows (* 80 256))

(defn read-edn [path]
  (edn/read-string (slurp path)))

(defn tsv-rows [path]
  (let [[header & rows] (str/split-lines (slurp path))
        columns (str/split header #"\t")]
    (mapv #(zipmap columns (str/split % #"\t" -1)) rows)))

(defn -main [& [allele-path map-path output-path]]
  (when-not (and allele-path map-path output-path)
    (throw (ex-info
            "usage: analyze_baldwin_mechanism_battery.clj ALLELES MAP OUTPUT" {})))
  (let [alleles (read-edn allele-path)
        rows (:rows alleles)
        family-size (:family-size alleles)
        expected-probe-keys
        (set (for [locus (range 80) rule (:probe-rules alleles)] [locus rule]))
        observed-probe-keys (set (map (juxt :locus :rule) rows))
        expected-units (* (count (:seeds alleles)) (count (:sites alleles)))
        response-valid?
        (and (= expected-probes family-size (count rows))
             (= expected-probe-keys observed-probe-keys)
             (= expected-units (count (:baseline alleles)))
             (every? (fn [row]
                       (and (= expected-units (:n row) (count (:deltas row)))
                            (= (:n row) (+ (:positive row) (:negative row)
                                           (:ties row)))
                            (= (:familywise-significant? row)
                               (mechanism/familywise-significant-direction?
                                (:positive row) (:negative row) expected-probes))))
                     rows))
        map-rows (tsv-rows map-path)
        expected-map-keys (set (for [locus (range 80) rule (range 256)]
                                 [locus rule]))
        observed-map-keys
        (set (map #(vector (Long/parseLong (get % "locus"))
                           (Long/parseLong (get % "rule"))) map-rows))
        map-valid? (and (= expected-map-rows (count map-rows))
                        (= expected-map-keys observed-map-keys))
        useful (count (filter #(and (= "true" (get % "function-preserved"))
                                    (= "true" (get % "selectable")))
                              map-rows))
        summary {:valid? (and response-valid? map-valid?)
                 :responsive-alleles
                 (count (filter :familywise-significant? rows))
                 :useful-held-endpoints useful}
        report {:kind :baldwin-mechanism-battery-result
                :schema 1
                :allele-probes-observed (count rows)
                :paired-units-per-probe expected-units
                :family-size family-size
                :familywise-responsive-alleles (:responsive-alleles summary)
                :map-rows-observed (count map-rows)
                :useful-held-endpoints useful
                :valid? (:valid? summary)
                :outcome (mechanism/classify-battery summary)}]
    (spit output-path (str (pr-str report) "\n"))
    (println (pr-str report))
    (when-not (:valid? report)
      (throw (ex-info "mechanism battery artifact validation failed" report)))))

(apply -main *command-line-args*)
