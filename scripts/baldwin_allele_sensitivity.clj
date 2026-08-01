(ns baldwin-allele-sensitivity
  "Paired inherited-field sensitivity map while every probed locus stays plastic."
  (:require [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]))

(def probe-rules [0 30 54 90 110 154 170 204])

(defn -main [& [record-path output-path seed-count-s site-count-s]]
  (when-not (and record-path output-path)
    (throw (ex-info
            "usage: baldwin_allele_sensitivity.clj RECORD OUTPUT [SEEDS] [SITES]"
            {})))
  (let [seed-count (Long/parseLong (or seed-count-s "3"))
        site-count (Long/parseLong (or site-count-s "8"))
        _ (when-not (and (<= 1 seed-count 64) (<= 1 site-count selection/W))
            (throw (ex-info "seed/site count outside range"
                            {:seeds seed-count :sites site-count})))
        seeds (vec (range 1 (inc seed-count)))
        sites (vec (take site-count
                         (range 0 selection/W (max 1 (quot selection/W site-count)))))
        genome (selection/best-genome-from-record record-path)
        options {}
        baseline (mechanism/paired-reach genome seeds sites options)
        probes (for [locus (range selection/W)
                     :when (not (get (:hold genome) locus))
                     rule probe-rules]
                 [locus rule])
        rows (doall (pmap (fn [[locus rule]]
                            (mechanism/allele-sensitivity
                             genome locus rule baseline seeds sites options))
                          probes))
        report {:kind :baldwin-inherited-allele-sensitivity
                :schema 1
                :record record-path
                :seeds seeds
                :sites sites
                :probe-rules probe-rules
                :baseline baseline
                :rows (vec rows)}]
    (spit output-path (str (pr-str report) "\n"))
    (println
     (pr-str
      {:kind (:kind report)
       :n-probes (count rows)
       :positive-majorities
       (count (filter #(> (:positive %) (:negative %)) rows))
       :negative-majorities
       (count (filter #(< (:positive %) (:negative %)) rows))
       :all-tied (count (filter #(zero? (+ (:positive %) (:negative %))) rows))
       :mean-absolute-delta
       (mechanism/mean (map #(Math/abs (double (:mean-delta %))) rows))}))))

(apply -main *command-line-args*)
