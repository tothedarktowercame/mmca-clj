(ns mmca.figures
  "Seeded, all-Clojure data generators for the paper figures."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as mmca]))

(def width 80)
(def steps 120)
(def rotations [-4 -3 -2 -1 1 2 3 4])
(def rotation-seeds [0 1])
(def figure1-operators
  {"16250374" [1 6 2 5 0 3 7 4]
   "10275364" [1 0 2 7 5 3 6 4]})
(def figure3-operators
  [["rot2" [2 3 4 5 6 7 0 1]]
   ["rot1" [1 2 3 4 5 6 7 0]]
   ["reduced0246" [2 1 4 3 6 5 0 7]]
   ["reduced024" [2 1 4 3 0 5 6 7]]
   ["reduced02" [2 1 0 3 4 5 6 7]]])

(def eoc-offset2 [2 3 4 5 6 7 0 1])
(def eoc-offset4 [4 5 6 7 0 1 2 3])
(def eoc-sigma16250374 [1 6 2 5 0 3 7 4])
(def activity-width 300)
(def activity-steps 300)
(def activity-late-window 150)
(def activity-seed 1)
(def eoc-width 256)
(def eoc-steps 600)

(def ^:private color-overrides
  {29 "#00ff33", 30 "#0033ff", 71 "#00ff33", 90 "#ffcc00"
   110 "#ff3300", 118 "#ff3300", 120 "#0033ff", 135 "#0033ff"
   137 "#ff3300", 145 "#ff3300", 165 "#ffcc00", 184 "#00ff33"
   225 "#0033ff", 226 "#00ff33"})

(defn rule-color [rule]
  (or (color-overrides rule) (format "#%02x%02x%02x" rule rule rule)))

(defn- ensure-output-dirs! []
  (.mkdirs (io/file "data"))
  (.mkdirs (io/file "figures")))

(defn- render-run
  ([run] (render-run run false))
  ([run include-rules?]
   (str "GEN\n"
        (str/join "\n"
                  (for [row (:gen run)]
                    (str/join " " (map rule-color row))))
        "\n"
        (when include-rules?
          (str "RULES\n"
               (str/join "\n" (map #(str/join " " %) (:gen run)))
               "\n"))
        "PHE\n"
        (str/join "\n" (:phe run))
        "\n")))

(defn- rotation [offset]
  (mapv #(mod (+ % offset) 8) (range 8)))

(defn periodic-rule-step
  "Evolve a binary state under one ECA rule with periodic boundaries."
  [rule state]
  (let [w (count state)]
    (mapv (fn [i]
            (mmca/rule-output rule
                              (nth state (mod (dec i) w))
                              (nth state i)
                              (nth state (mod (inc i) w))))
          (range w))))

(defn isolated-rule-activity
  "Asymptotic per-step cell-change rate for RULE run alone as a periodic ECA.
   Parameters are pinned to the calibration used by the EoC paper figures."
  [rule]
  (let [random (java.util.Random. activity-seed)
        initial (vec (repeatedly activity-width #(.nextInt random 2)))
        rates (loop [t 0 state initial rates []]
                (if (= t activity-steps)
                  rates
                  (let [next-state (periodic-rule-step rule state)]
                    (recur (inc t) next-state
                           (conj rates
                                 (/ (double (mmca/changed-count state next-state))
                                    activity-width))))))]
    (/ (reduce + 0.0 (take-last activity-late-window rates))
       activity-late-window)))

(defn- write-genotype-field! [path run]
  (spit path
        (str (str/join "\n" (map #(str/join " " %) (:gen run))) "\n")))

(defn generate-activity-scores! []
  (spit "data/rule_activity_scores.txt"
        (apply str
               (for [rule (range mmca/rule-count)]
                 (format "%d %.5f\n" rule (isolated-rule-activity rule)))))
  (println "wrote isolated-rule activity scores"))

(defn generate-eoc-tint! []
  (doseq [[id writing] [["offset2" eoc-offset2]
                        ["sigma16250374" eoc-sigma16250374]]]
    (write-genotype-field!
     (format "data/eoc_tint_%s.txt" id)
     (mmca/run-propagator writing 1 eoc-width eoc-steps)))
  (println "wrote EoC tint fields"))

(defn generate-eoc-phase! []
  (doseq [q [0.0 0.05 0.25 0.75]]
    (write-genotype-field!
     (format "data/eoc_phase_q%03d.txt" (long (* 1000 q)))
     (mmca/run-propagator eoc-offset2 1 240 300 {:interrupter-q q})))
  (println "wrote EoC phase example fields"))

(defn- run-interface [kind writing seed width steps]
  (if (= kind :river)
    (mmca/run-river seed width steps)
    (mmca/run-propagator writing seed width steps)))

(defn generate-eoc-interface! []
  (let [operators [[:offset2 eoc-offset2 [1 2 3]]
                   [:offset4 eoc-offset4 [1 2]]
                   [:river nil [1 2]]]]
    ;; Full-height representative panels at the paper's W=256, T=600 setting.
    (doseq [[kind writing _] operators]
      (write-genotype-field!
       (format "data/eoc_interface_top_%s.txt" (name kind))
       (run-interface kind writing 1 eoc-width eoc-steps)))
    ;; Square late-time fields for finite-size box counting.
    (doseq [width [128 256 512 768]
            [kind writing seeds] operators
            seed seeds
            :let [run (run-interface kind writing seed width (+ width 200))
                  square (assoc run :gen (vec (take-last width (:gen run))))]]
      (write-genotype-field!
       (format "data/eoc_interface_%s_L%d_s%d.txt" (name kind) width seed)
       square)))
  (println "wrote EoC interface fields"))

(defn generate-eoc! []
  (generate-activity-scores!)
  (generate-eoc-tint!)
  (generate-eoc-phase!)
  (generate-eoc-interface!))

(defn generate-figure1! []
  (doseq [[name writing] figure1-operators]
    (spit (format "data/fig1_%s.txt" name)
          (render-run (mmca/run-propagator writing 0 width steps))))
  (println "wrote Figure 1 data"))

(defn generate-figure3! []
  (doseq [[id writing] figure3-operators]
    (spit (format "data/fig3_%s.txt" id)
          (render-run (mmca/run-propagator-alone writing 0 width steps))))
  (println "wrote Figure 3 data"))

(defn generate-figure4! []
  (doseq [offset rotations
          seed rotation-seeds]
    (spit (format "data/fig4_off%+d_s%d.txt" offset seed)
          (render-run (mmca/run-propagator (rotation offset) seed width 100))))
  (println "wrote Figure 4 data"))

(defn generate-figure5! []
  (doseq [offset rotations
          seed rotation-seeds]
    (let [run (mmca/run-propagator (rotation offset) seed width steps)]
      (spit (format "data/fig5_off%+d_s%d.txt" offset seed)
            (str (str/join "\n" (map mmca/distinct-rules (:gen run))) "\n"))))
  (println "wrote Figure 5 data"))

(defn generate-figure6! []
  (doseq [seed (range 1 7)]
    (spit (format "data/fig6_s%d.txt" seed)
          (render-run (mmca/run-river seed width steps))))
  (println "wrote Figure 6 data"))

(defn generate-river-grid! []
  (doseq [seed (range 1 37)]
    (spit (format "data/river_grid_s%d.txt" seed)
          (render-run (mmca/run-river seed width steps))))
  (println "wrote 36 river-grid runs"))

(defn generate-original-river! []
  (doseq [seed (range 1 7)]
    (spit (format "data/original_river_s%d.txt" seed)
          (render-run
           (mmca/run-river seed width steps))))
  (println "wrote original-paper river runs"))

(defn generate-figure8! []
  (spit "data/fig8_raw.txt"
        (render-run
         (mmca/run-propagator [0 0 1 2 3 4 5 6] 4 width steps)
         true))
  (println "wrote Figure 8 data"))

(defn- mean [xs]
  (/ (reduce + 0.0 xs) (count xs)))

(defn- stats [xs]
  [(mean xs) (apply min xs) (apply max xs)])

(defn- conclusion-run [writing seed]
  (let [run (mmca/run-propagator writing seed width steps)
        cells (mapcat identity (:gen run))]
    {:diversity (mean (map mmca/distinct-rules (take-last 40 (:gen run))))
     :rule110 (* 100.0 (/ (count (filter #{110} cells)) (count cells)))}))

(defn generate-stats! []
  (let [text
        (apply str
               (for [[name writing] figure1-operators
                     :let [runs (mapv #(conclusion-run writing %) (range 6))
                           [dm dlo dhi] (stats (map :diversity runs))
                           [rm rlo rhi] (stats (map :rule110 runs))
                           representative (first runs)]]
                 (format (str "%s: rep(seed0) r110=%.1f%% div=%.1f | "
                              "sample r110=%.1f%% (%.1f-%.1f) "
                              "div=%.1f (%.0f-%.0f)\n")
                         name (:rule110 representative)
                         (:diversity representative) rm rlo rhi dm dlo dhi)))]
    (spit "data/conclusion_stats.txt" text)
    (print text)))

(defn generate-all! []
  (ensure-output-dirs!)
  (generate-figure1!)
  (generate-figure3!)
  (generate-figure4!)
  (generate-figure5!)
  (generate-figure6!)
  (generate-figure8!)
  (generate-stats!)
  (generate-eoc!))

(defn -main [& [command]]
  (ensure-output-dirs!)
  (case (or command "all")
    "all" (generate-all!)
    "fig1" (generate-figure1!)
    "fig3" (generate-figure3!)
    "fig4" (generate-figure4!)
    "fig5" (generate-figure5!)
    "fig6" (generate-figure6!)
    "river-grid" (generate-river-grid!)
    "original-river" (generate-original-river!)
    "fig8" (generate-figure8!)
    "stats" (generate-stats!)
    "activity-scores" (generate-activity-scores!)
    "eoc-tint" (generate-eoc-tint!)
    "eoc-phase" (generate-eoc-phase!)
    "eoc-interface" (generate-eoc-interface!)
    "eoc" (generate-eoc!)
    (throw (ex-info "Unknown command"
                    {:command command
                     :expected ["all" "fig1" "fig3" "fig4" "fig5"
                                "fig6" "river-grid" "original-river"
                                "fig8" "stats" "activity-scores"
                                "eoc-tint" "eoc-phase" "eoc-interface"
                                "eoc"]}))))
