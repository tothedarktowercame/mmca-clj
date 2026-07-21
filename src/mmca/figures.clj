(ns mmca.figures
  "Seeded, all-Clojure data generators for the paper figures."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [mmca.core :as mmca]))

(def width 80)
(def steps 120)
(def rotations [-4 -3 -2 -1 1 2 3 4])
(def rotation-seeds [0 1])
;; The two search-found operators, expressed in standard Wolfram order (their
;; conjugates). The legacy 2014 truth-table integers still name them, but the
;; writing is the Wolfram-order permutation -- 16250374 = [4 0 3 2 7 5 1 6],
;; which concentrates the field on Rule 110 (~44%) as the legacy figure did.
(def figure1-operators
  {"16250374" (mmca/positional-writing->neighbourhood-writing [1 6 2 5 0 3 7 4])
   "10275364" (mmca/positional-writing->neighbourhood-writing [1 0 2 7 5 3 6 4])})
(def figure3-operators
  [["rot2" [2 3 4 5 6 7 0 1]]
   ["rot1" [1 2 3 4 5 6 7 0]]
   ["reduced0246" [2 1 4 3 6 5 0 7]]
   ["reduced024" [2 1 4 3 0 5 6 7]]
   ["reduced02" [2 1 0 3 4 5 6 7]]])

(def eoc-offset1 [1 2 3 4 5 6 7 0])   ; Wolfram-order survivor (8-cycle): EoC exemplar
(def eoc-offset2 [2 3 4 5 6 7 0 1])
(def eoc-offset4 [4 5 6 7 0 1 2 3])
(def eoc-sigma16250374 (mmca/positional-writing->neighbourhood-writing [1 6 2 5 0 3 7 4]))  ; Wolfram form (Rule-110-dominated foil)

;; Two operators with identical cycle type (two 4-cycles) and OPPOSITE fate --
;; cycle structure does not determine aliveness. The sustaining one is the
;; Wolfram-order conjugate of draft3's offset+2 "edge of chaos" exemplar.
(def two-4cycle-sustain [6 7 0 2 1 4 3 5])   ; sustains (live/live)
(def two-4cycle-collapse [2 3 4 5 6 7 0 1])  ; offset+2 rotation; collapses (dead/dead)
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
  (doseq [[id writing] [["offset1" eoc-offset1]
                        ["two4cyc" two-4cycle-sustain]
                        ["sigma16250374" eoc-sigma16250374]]]
    (write-genotype-field!
     (format "data/eoc_tint_%s.txt" id)
     (mmca/run-propagator writing 1 eoc-width eoc-steps)))
  (println "wrote EoC tint fields"))

(defn generate-eoc-phase! []
  (doseq [q [0.0 0.05 0.25 1.0]]
    (write-genotype-field!
     (format "data/eoc_phase_q%03d.txt" (long (* 1000 q)))
     (mmca/run-propagator eoc-offset1 1 240 300 {:interrupter-q q})))
  (println "wrote EoC phase example fields"))

(defn- run-interface [kind writing seed width steps]
  (if (= kind :river)
    (mmca/run-river seed width steps)
    (mmca/run-propagator writing seed width steps)))

(defn generate-eoc-interface! []
  (let [operators [[:offset1 eoc-offset1 [1 2 3]]
                   [:two4cyc two-4cycle-sustain [1 2 3]]
                   [:sigma16250374 eoc-sigma16250374 [1 2 3]]
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

;; Figure 2, two terminal-diversity solutions of the same historical operator.
;; draft4: [0 0 1 2 3 4 5 6] Wolfram-direct -> genotype stays diverse (~30 rules).
;; draft3: its conjugate [1 2 4 5 3 6 7 7] -> genotype collapses to 2 rules (76,77).
(def fig2-draft4 [0 0 1 2 3 4 5 6])
(def fig2-draft3 [1 2 4 5 3 6 7 7])

(defn generate-fig2pair! []
  (spit "data/fig2pair_draft3.txt"
        (render-run (mmca/run-propagator fig2-draft3 4 width steps)))
  (spit "data/fig2pair_draft4.txt"
        (render-run (mmca/run-propagator fig2-draft4 4 width steps)))
  (println "wrote Figure 2 pair (2-genotype vs many-genotype) data"))

(defn generate-figshell! []
  ;; Critical-shell example: sigma=[2 3 0 1 5 4 7 6] is an all-even involution
  ;; that fixes the lambda=1/2 shell (Rule 105, LIVE). All-even => collapses:
  ;; genotype dies onto Rule 105 while the phenotype stays complex. Native
  ;; Wolfram ordering (no shim), so run-propagator applies it directly.
  (spit "data/figshell.txt"
        (render-run (mmca/run-propagator [2 3 0 1 5 4 7 6] 1 width steps)))
  (println "wrote Figure 7 (critical-shell) data"))

(defn generate-two-4cycle! []
  (spit "data/two4cycle_sustain.txt"
        (render-run (mmca/run-propagator two-4cycle-sustain 0 width steps)))
  (spit "data/two4cycle_collapse.txt"
        (render-run (mmca/run-propagator two-4cycle-collapse 0 width steps)))
  (println "wrote two-4-cycle (opposite-fate) data"))

;; Braiding: two operators that each collapse alone (dead genotype). Alternating
;; offset+2 with offset+4 (different block systems) revives; alternating it with
;; offset-2 (same evens/odds partition) does not.
(def braid-off2 [2 3 4 5 6 7 0 1])   ; two 4-cycles
(def braid-off4 [4 5 6 7 0 1 2 3])   ; four 2-cycles (complementary block system)
(def braid-offm2 [6 7 0 1 2 3 4 5])  ; two 4-cycles, SAME evens/odds partition

(defn generate-braid! []
  (doseq [[id w] [["off2" braid-off2] ["off4" braid-off4] ["offm2" braid-offm2]]]
    (spit (format "data/braid_%s.txt" id)
          (render-run (mmca/run-propagator w 0 width steps))))
  (spit "data/braid_complementary.txt"
        (render-run (mmca/run-braid braid-off2 braid-off4 0 width steps)))
  (spit "data/braid_samefamily.txt"
        (render-run (mmca/run-braid braid-off2 braid-offm2 0 width steps)))
  (println "wrote braiding data"))

;; Complexity knob: braid the dead sigma^2 (two 4-cycles) with the mixing sigma^1
;; (8-cycle) at ratio num/12. Sustained diversity rises with the sigma^1 fraction.
(def knob-sigma1 [1 2 3 4 5 6 7 0])
(def knob-sigma2 [2 3 4 5 6 7 0 1])

(defn generate-knob! []
  (let [den 12
        sched (fn [num] (vec (concat (repeat (- den num) knob-sigma2)
                                     (repeat num knob-sigma1))))
        gdiv (fn [num seed]
               (let [lg (take-last 40 (:gen (mmca/run-schedule (sched num) seed width steps)))]
                 (double (/ (reduce + (map mmca/distinct-rules lg)) (count lg)))))]
    (spit "data/knob_curve.txt"
          (apply str (for [num (range 0 (inc den))]
                       (format "%.4f %.3f\n" (/ (double num) den)
                               (/ (reduce + (map #(gdiv num %) (range 5))) 5.0)))))
    (doseq [[id num] [["low" 3] ["mid" 6] ["high" 12]]]
      (spit (format "data/knob_%s.txt" id)
            (render-run (mmca/run-schedule (sched num) 0 width steps))))
    (println "wrote complexity-knob data")))

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
  (generate-fig2pair!)
  (generate-figshell!)
  (generate-two-4cycle!)
  (generate-braid!)
  (generate-knob!)
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
    "fig2pair" (generate-fig2pair!)
    "figshell" (generate-figshell!)
    "two4cycle" (generate-two-4cycle!)
    "braid" (generate-braid!)
    "knob" (generate-knob!)
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
                                "fig8" "fig2pair" "figshell" "two4cycle" "braid" "knob"
                                "stats" "activity-scores"
                                "eoc-tint" "eoc-phase" "eoc-interface"
                                "eoc"]}))))
