(ns mmca.experiments.eoc-sweep
  "Generate deterministic genotype fields for a synthetic order-edge-chaos sweep."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as c]
            [mmca.figures :as figures]))

(def width 240)
(def steps 300)
(def seeds (range 8))
(def order-qs (mapv #(/ % 10.0) (range 11)))
(def chaos-ps [0.0 0.01 0.02 0.05 0.1 0.2 0.4 0.7 1.0])

(defn- parameter-id [value]
  (long (Math/round (* 1000.0 (double value)))))

(defn- write-genotype-field! [path run]
  (spit path
        (str (str/join "\n" (map #(str/join " " %) (:gen run))) "\n")))

(defn generate! []
  (.mkdirs (io/file "data"))
  (let [order-paths
        (for [q order-qs
              seed seeds]
          (let [path (format "data/eoc_sweep_order_q%03d_s%d.txt"
                             (parameter-id q) seed)]
            (write-genotype-field!
             path
             (c/run-propagator figures/eoc-offset1 seed width steps
                               {:interrupter-q q :noise-p 0.0}))
            path))
        chaos-paths
        (for [p chaos-ps
              seed seeds]
          (let [path (format "data/eoc_sweep_chaos_p%03d_s%d.txt"
                             (parameter-id p) seed)]
            (write-genotype-field!
             path
             (c/run-propagator figures/eoc-offset1 seed width steps
                               {:interrupter-q 1.0 :noise-p p}))
            path))]
    (vec (concat order-paths chaos-paths))))

(defn -main [& _]
  (let [paths (generate!)]
    (println (format "wrote %d EoC sweep genotype fields" (count paths)))))
