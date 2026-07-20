(ns mmca.experiments.offset1-finite-size
  "RESUMABLE finite-size scan of the standard-Wolfram-order EoC survivor offset+1
  [1 2 3 4 5 6 7 0] -- the single 8-cycle rotation that sustains a high-diversity
  field in Wolfram order (offset+2 collapses there). This is the Wolfram-order
  counterpart of `offset2-finite-size`; it runs the same finite-size-scaling test:
  as the system size L grows, does the interrupter-q susceptibility peak GROW and
  its peak-q CONVERGE (a critical transition), or shrink (off-critical)?

  Each expensive per-(L,q) cell is checkpointed to disk the moment it finishes, so
  a crash/restart resumes from the last completed cell. Re-running -main (or a
  systemd Restart=on-failure) picks up where it stopped; when every cell is
  present it writes the final report. Deterministic: each cell result depends only
  on (config, width, q, seeds)."
  (:require [mmca.experiments.control-param-scan :as e2]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def offset1-full-config
  {:writing [1 2 3 4 5 6 7 0]
   :main-ns "mmca.experiments.offset1-finite-size"
   :seed-start 0 :seed-count 32
   :widths [30 60 120 240]
   :qs [0.0 0.025 0.05 0.075 0.1 0.125 0.15 0.2 0.3 0.4 0.5 0.75 1.0]
   :steps 300 :late-window 80 :collapse-window 8})

(def checkpoint-path "holes/E2b-offset1-checkpoint.edn")
(def results-path "holes/E2b-offset1-finite-size-results.md")

(defn- load-checkpoint
  "Map of [width q] -> row for every cell already computed (empty if none)."
  []
  (if (.exists (io/file checkpoint-path))
    (with-open [r (io/reader checkpoint-path)]
      (reduce (fn [m line]
                (if (str/blank? line)
                  m
                  (let [row (edn/read-string line)]
                    (assoc m [(:width row) (:q row)] row))))
              {} (line-seq r)))
    {}))

(defn -main [& _]
  (let [config offset1-full-config
        seeds (range (:seed-start config) (+ (:seed-start config) (:seed-count config)))
        cells (vec (for [width (:widths config) q (:qs config)] [width q]))
        done0 (load-checkpoint)]
    (println (format "resumable offset+1 finite-size scan | %d/%d cells already checkpointed"
                     (count done0) (count cells)))
    (flush)
    ;; compute any missing cell, checkpointing each immediately (append + flush)
    (doseq [[width q] cells :when (not (contains? done0 [width q]))]
      (let [row (e2/summarize-cell config width q seeds)]
        (spit checkpoint-path (str (pr-str row) "\n") :append true)
        (println (format "  checkpointed L=%-3d q=%.3f" width q))
        (flush)))
    ;; reassemble in canonical order, post-process, write the report
    (let [all (load-checkpoint)
          rows (vec (for [width (:widths config) q (:qs config)] (all [width q])))]
      (if (every? some? rows)
        (do (spit results-path (e2/markdown-report (e2/summarize-rows config rows)))
            (println (str "COMPLETE -> " results-path)))
        (println "INCOMPLETE: some cells still missing; re-run to resume."))
      (flush))))
