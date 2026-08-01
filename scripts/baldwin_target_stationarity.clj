(ns baldwin-target-stationarity
  "Measure whether rewriting converges on a cross-lifetime field target."
  (:require [clojure.java.io :as io]
            [mmca.baldwin-mechanism :as mechanism]
            [mmca.baldwin-selection :as selection]))

(def fixed-p0
  "01011111101100010000000111010011001010110101011111011001100001010010111111100101")

(defn -main [& [record-path output-path seed-count-s common-rewrite-seed-s]]
  (when-not (and record-path output-path)
    (throw (ex-info
            "usage: baldwin_target_stationarity.clj RECORD OUTPUT [SEEDS] [COMMON-REWRITE-SEED]"
            {})))
  (let [seed-count (Long/parseLong (or seed-count-s "8"))
        common-rewrite-seed (Long/parseLong (or common-rewrite-seed-s "20260802"))
        _ (when-not (<= 3 seed-count 64)
            (throw (ex-info "seed count outside [3,64]" {:seed-count seed-count})))
        genome (selection/best-genome-from-record record-path)
        seeds (range 1 (inc seed-count))
        grid (mechanism/stationarity-grid genome seeds common-rewrite-seed fixed-p0)
        report {:kind :baldwin-target-stationarity
                :schema 1
                :record (.getCanonicalPath (io/file record-path))
                :field-width selection/W
                :chance-agreement (/ 1.0 256.0)
                :environment-seeds (vec seeds)
                :common-rewrite-seed common-rewrite-seed
                :fixed-p0 fixed-p0
                :apparatus-valid? (:apparatus-valid? grid)
                :cells (dissoc grid :apparatus-valid?)}]
    (when-not (:apparatus-valid? report)
      (throw (ex-info "fully stationary apparatus control failed" {:report report})))
    (spit output-path (str (pr-str report) "\n"))
    (println (pr-str (update report :cells
                             #(update-vals % (fn [cell]
                                               (dissoc cell :fields :pairwise))))))))

(apply -main *command-line-args*)
