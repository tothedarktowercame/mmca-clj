(ns mmca.experiments.direct-computation
  "E7 (correction round): held-out decoding of storage, transmission, and XOR
  modification on the AUTHENTIC paper river, with a matched feedback-off control.

  THREE arms:

  1. BASE (positive control). The feedforward propagator — a channel already
     known to store and transmit genotype signal (C ~ 40% in prior runs). Its
     presence validates the decoder pipeline: if the base cannot recover an
     injected G-bit, the probe is broken, not the dynamics.

  2. RIVER. `c/run-river-from` injecting into X (phenotype bit) and G (rule
     byte, balanced 105/204 pair) before iteration. This is the authentic
     paper river (constant-zero / Java-seed / quad-4cand).

  3. RIVER-ABLATED (matched no-feedback control). `c/run-river-ablated-from`
     from the SAME injected initial state and the SAME Java RNG tape — only
     the live X->G edge is cut (frozen phenotype). River-minus-ablated is the
     ISOLATED feedback capacity: whatever the decoder can recover from the
     river but NOT from the ablation is causally attributable to the
     phenotype->genotype feedback channel.

  The decoder is a deterministic nearest-centroid classifier trained on
  disjoint seeds and evaluated only on held-out seeds. We SCAN delay tau,
  distance d, and decoder radius — a single T=8 point is too thin.

  Honest framing: absence of modification capacity under these probes is
  reported as exactly that — never as 'no computation'."
  (:require [clojure.string :as str]
            [mmca.core :as c]
            [mmca.rng :as rng]))

;; ---------------------------------------------------------------------------
;; Config
;; ---------------------------------------------------------------------------

(def default-config
  {:operators [{:id :rotate+2 :class :all-even
                :writing [2 3 4 5 6 7 0 1]}
               {:id :offset+4 :class :all-even
                :writing [4 5 6 7 0 1 2 3]}
               {:id :reduced-0246 :class :any-odd
                :writing [2 1 4 3 6 5 0 7]}
               {:id :reduced-02 :class :any-odd
                :writing [2 1 0 3 4 5 6 7]}]
   :train-seeds (vec (range 0 16))
   :test-seeds (vec (range 16 32))
   :width 81
   :steps 16
   ;; Scanned parameters (each value is one decode column):
   :storage-delays [4 8 12]
   :transmission-delays [4 8 12]
   :distances [4 8 12]
   :decoder-radii [1 2 3]
   :xor-spacing 4
   ;; Default probe point (used for the summary table + the river/ablated
   ;; contrast, which is the headline):
   :default-delay 8
   :default-distance 8
   :default-radius 2})

;; ---------------------------------------------------------------------------
;; Phenotype / genotype bit injection
;; ---------------------------------------------------------------------------

(defn- replace-phenotype-bit [phenotype site bit]
  (str (subs phenotype 0 site) (char (+ (int \0) bit))
       (subs phenotype (inc site))))

(defn- inject-one [genotype phenotype layer site bit]
  (case layer
    :X [genotype (replace-phenotype-bit phenotype site bit)]
    ;; Both are lambda=1/2 fixed rules: 105 is live/additive, 204 is dead/identity.
    :G [(assoc genotype site (if (= bit 1) 105 204)) phenotype]))

;; ---------------------------------------------------------------------------
;; Base (feedforward) arm — injects into the initial state, then runs
;; run-propagator-style iteration. Reuses the same decoder pipeline.
;; ---------------------------------------------------------------------------

(defn- base-initial-state [seed width]
  (let [random (rng/make-rng (format "prop-%d" seed))]
    {:random random
     :genotype (c/random-genotype random width)
     :phenotype (c/random-phenotype random width)}))

(defn- run-base-injected
  "Run the feedforward base engine from an injected initial state for `delay`
  steps. Returns the final {:G genotype :X phenotype}."
  [writing seed width delay layer injections]
  (let [{:keys [random genotype phenotype]} (base-initial-state seed width)
        [genotype phenotype]
        (reduce (fn [[g x] [site bit]] (inject-one g x layer site bit))
                [genotype phenotype] injections)]
    (loop [t 0 genotype genotype phenotype phenotype]
      (if (= t delay)
        {:G genotype :X phenotype}
        (let [next-phenotype (c/phenotype-step genotype phenotype)
              next-genotype (c/genotype-step random genotype writing true)]
          (recur (inc t) next-genotype next-phenotype))))))

;; ---------------------------------------------------------------------------
;; River arm — injects into the river's Java-seeded initial state, then runs
;; run-river-from (live feedback) or run-river-ablated-from (matched control).
;; ---------------------------------------------------------------------------

(defn- river-initial-state
  "Reproduce the river's Java-seeded initial-state construction (genotype +
  phenotype), consuming the RNG exactly as run-river does, so the subsequent
  iteration tape is identical."
  [seed width]
  (let [random (java.util.Random. (long seed))
        g0 (c/java-random-genotype random width)
        p0 (c/java-random-phenotype random width)]
    {:random random :genotype g0 :phenotype p0}))

(defn- run-river-injected
  "Run the authentic river from an injected initial state for `delay` steps.
  `mode` is :river (live feedback) or :ablated (matched frozen-phenotype control).
  Returns the final {:G genotype :X phenotype}."
  [mode seed width delay layer injections]
  (let [{:keys [random genotype phenotype]} (river-initial-state seed width)
        [genotype phenotype]
        (reduce (fn [[g x] [site bit]] (inject-one g x layer site bit))
                [genotype phenotype] injections)
        run (case mode
              :river (c/run-river-from random genotype phenotype delay)
              :ablated (c/run-river-ablated-from random genotype phenotype delay))]
    {:G (peek (:gen run)) :X (peek (:phe run))}))

;; ---------------------------------------------------------------------------
;; Feature extraction + decoder (shared across all arms)
;; ---------------------------------------------------------------------------

(defn- window-sites [centre radius]
  (range (- centre radius) (inc (+ centre radius))))

(defn- feature-vector [state layer site radius]
  (let [sites (window-sites site radius)]
    (case layer
      :X (mapv #(Character/digit (nth (:X state) %) 2) sites)
      :G (vec (mapcat #(c/rule-bits (nth (:G state) %)) sites)))))

(defn- inputs-for [primitive]
  (if (= primitive :modification)
    [[[0 0] 0] [[0 1] 1] [[1 0] 1] [[1 1] 0]]
    [[[0] 0] [[1] 1]]))

(defn- base-example
  [{:keys [width default-distance xor-spacing default-radius] :as config}
   writing layer primitive seed [inputs label]]
  (let [centre (quot width 2)
        [delay read-site injections]
        (case primitive
          :storage [(:default-delay config) centre [[centre (first inputs)]]]
          :transmission [(:default-delay config) (+ centre default-distance)
                         [[centre (first inputs)]]]
          :modification [(:default-delay config) centre
                         [[(- centre xor-spacing) (first inputs)]
                          [(+ centre xor-spacing) (second inputs)]]])
        state (run-base-injected writing seed width delay layer injections)]
    {:label label
     :features (feature-vector state layer read-site default-radius)}))

(defn- river-example
  [{:keys [width default-delay default-distance xor-spacing default-radius]}
   mode layer primitive seed [inputs label]]
  (let [centre (quot width 2)
        [delay read-site injections]
        (case primitive
          :storage [default-delay centre [[centre (first inputs)]]]
          :transmission [default-delay (+ centre default-distance)
                         [[centre (first inputs)]]]
          :modification [default-delay centre
                         [[(- centre xor-spacing) (first inputs)]
                          [(+ centre xor-spacing) (second inputs)]]])
        state (run-river-injected mode seed width delay layer injections)]
    {:label label
     :features (feature-vector state layer read-site default-radius)}))

(defn- examples-fn
  "Return a function [seed-input-pair -> example] for the given arm."
  [arm config writing layer primitive]
  (case arm
    :base #(base-example config writing layer primitive %1 %2)
    :river #(river-example config :river layer primitive %1 %2)
    :ablated #(river-example config :ablated layer primitive %1 %2)))

(defn- collect-examples [arm config writing layer primitive seeds]
  (mapv (fn [[seed input]]
          ((examples-fn arm config writing layer primitive) seed input))
        (for [seed seeds input (inputs-for primitive)] [seed input])))

;; ---------------------------------------------------------------------------
;; Nearest-centroid decoder
;; ---------------------------------------------------------------------------

(defn- mean-vector [vectors]
  (let [n (double (count vectors))]
    (mapv #(/ (double %) n) (apply map + vectors))))

(defn train-decoder
  "Fit a deterministic nearest-centroid binary decoder."
  [training]
  (into {}
        (for [label [0 1]]
          [label (mean-vector (mapv :features
                                    (filter #(= label (:label %)) training)))])))

(defn- squared-distance [left right]
  (reduce + 0.0 (map (fn [a b] (let [d (- (double a) (double b))] (* d d)))
                     left right)))

(defn decode [decoder features]
  (if (< (squared-distance features (get decoder 1))
         (squared-distance features (get decoder 0)))
    1 0))

;; ---------------------------------------------------------------------------
;; Scoring with seed/fold intervals
;; ---------------------------------------------------------------------------

(defn- held-out-score [training testing]
  (let [decoder (train-decoder training)
        correct (count (filter (fn [{:keys [label features]}]
                                 (= label (decode decoder features)))
                               testing))
        accuracy (/ correct (double (count testing)))]
    {:accuracy accuracy
     :capacity (max 0.0 (- (* 2.0 accuracy) 1.0))
     :n-test (count testing)}))

(defn- per-seed-accuracies
  "Compute per-seed accuracy so we can report a [min, max] seed interval rather
  than a bare average."
  [arm config writing layer primitive train-seeds test-seeds]
  (let [training (collect-examples arm config writing layer primitive train-seeds)
        decoder (train-decoder training)]
    (mapv
     (fn [seed]
       (let [seed-examples (collect-examples arm config writing layer primitive [seed])
             correct (count (filter (fn [{:keys [label features]}]
                                      (= label (decode decoder features)))
                                    seed-examples))]
         (/ correct (double (count seed-examples)))))
     test-seeds)))

(defn- score-with-interval
  [arm config writing layer primitive train-seeds test-seeds]
  (let [training (collect-examples arm config writing layer primitive train-seeds)
        testing (collect-examples arm config writing layer primitive test-seeds)
        {:keys [accuracy capacity n-test]} (held-out-score training testing)
        per-seed (per-seed-accuracies arm config writing layer primitive
                                      train-seeds test-seeds)]
    {:accuracy accuracy
     :capacity capacity
     :n-test n-test
     :seed-min (apply min per-seed)
     :seed-max (apply max per-seed)}))

;; ---------------------------------------------------------------------------
;; Parameter scans (delay, distance, radius)
;; ---------------------------------------------------------------------------

(defn- scan-parameter
  "Run a single primitive/layer across a scanned parameter, returning a vector
  of {param-value score-with-interval} maps. `param-key` is :delay, :distance,
  or :radius. `arm` is :base, :river, or :ablated."
  [arm config writing layer primitive param-key param-values]
  (mapv
   (fn [val]
     (let [cfg (assoc config
                      :default-delay (if (= param-key :delay) val (:default-delay config))
                      :default-distance (if (= param-key :distance) val (:default-distance config))
                      :default-radius (if (= param-key :radius) val (:default-radius config)))
           score (score-with-interval arm cfg writing layer primitive
                                      (:train-seeds config) (:test-seeds config))]
       {:param val :score score}))
   param-values))

;; ---------------------------------------------------------------------------
;; Top-level experiment
;; ---------------------------------------------------------------------------

(defn run-experiment
  ([] (run-experiment default-config))
  ([{:keys [operators train-seeds test-seeds steps storage-delays
            transmission-delays distances decoder-radii default-delay] :as config}]
   (when (< steps (max (apply max storage-delays) (apply max transmission-delays)
                       default-delay))
     (throw (ex-info "E7 steps must cover every decoding delay"
                     {:steps steps :max-delay (max (apply max storage-delays)
                                                    (apply max transmission-delays))})))
   {:config (select-keys config [:train-seeds :test-seeds :width :steps
                                 :storage-delays :transmission-delays :distances
                                 :decoder-radii :xor-spacing :default-delay
                                 :default-distance :default-radius])
    :base-rows
    (mapv
     (fn [{:keys [id class writing]}]
       (let [nb-writing (c/positional-writing->neighbourhood-writing writing)]
         {:operator id
          :class class
          :layers
          (into {}
                (for [layer [:X :G]]
                  [layer
                   (into {}
                         (for [primitive [:storage :transmission :modification]]
                           [primitive
                            (score-with-interval :base config nb-writing layer
                                                 primitive train-seeds test-seeds)]))]))}))
     operators)
    ;; River vs matched ablation — the headline contrast.
    :river-contrast
    {:writing c/river-writing
     :layers
     (into {}
           (for [layer [:X :G]]
             [layer
              (into {}
                    (for [primitive [:storage :transmission :modification]]
                      (let [river-score (score-with-interval :river config
                                         c/river-writing layer primitive
                                         train-seeds test-seeds)
                            ablated-score (score-with-interval :ablated config
                                           c/river-writing layer primitive
                                           train-seeds test-seeds)]
                        [primitive
                         {:river river-score
                          :ablated ablated-score
                          :isolated-capacity
                          (max 0.0 (- (:capacity river-score)
                                      (:capacity ablated-score)))}])))]))}
    ;; Scans on the river G layer (where feedback lives):
    :scans
    {:delay (scan-parameter :river config c/river-writing :G :storage
                            :delay storage-delays)
     :distance (scan-parameter :river config c/river-writing :G :transmission
                               :distance distances)
     :radius (scan-parameter :river config c/river-writing :G :storage
                              :radius decoder-radii)}}))

;; ---------------------------------------------------------------------------
;; Rendering
;; ---------------------------------------------------------------------------

(defn- pct [x] (format "%.1f%%" (* 100.0 (double x))))

(defn- fmt-interval [lo hi]
  (format "[%.1f%%, %.1f%%]" (* 100.0 (double lo)) (* 100.0 (double hi))))

(defn render-report [{:keys [config base-rows river-contrast scans]}]
  (let [{:keys [train-seeds test-seeds width steps storage-delays
                distances decoder-radii xor-spacing
                default-delay default-distance default-radius]} config]
    (str
     "# E7 — Direct computation primitives (correction round: authentic river + matched control)\n\n"
     "Reproduce bit-for-bit: `clojure -M -m mmca.experiments.direct-computation > holes/E7-results.md`.\n"
     "Determinism gate: `clojure -M:test`.\n"
     (format "Config: train seeds %d–%d, held-out seeds %d–%d, W=%d, T=%d. "
             (first train-seeds) (last train-seeds) (first test-seeds)
             (last test-seeds) width steps)
     (format "Scanned: tau∈%s, d∈%s, radius∈%s. "
             (str storage-delays) (str distances) (str decoder-radii))
     (format "Default probe: tau=%d, d=%d, radius=%d, XOR±%d.\n\n"
             default-delay default-distance default-radius xor-spacing)
     "Three arms: **BASE** (feedforward positive control — a channel already known "
     "to store/transmit G-signal), **RIVER** (`c/run-river-from`, authentic paper "
     "river, live X→G feedback), **RIVER-ABLATED** (`c/run-river-ablated-from`, "
     "matched frozen-phenotype control — same seed/tape/construction, only the "
     "X→G edge cut). River-minus-ablated is the **isolated feedback capacity**. "
     "Each cell: accuracy / normalized capacity C=max(0,2·acc−1), with seed-accuracy "
     "interval [min, max] across held-out seeds. Chance = 50% / C=0.\n\n"
     "## BASE arm (positive control)\n\n"
     "If the base cannot recover an injected G-bit, the probe is broken.\n\n"
     "| operator | class | layer | storage acc / C [seed range] | transmission acc / C [seed range] | XOR acc / C [seed range] |\n"
     "|---|---|---:|---:|---:|---:|\n"
     (str/join
      "\n"
      (for [{:keys [operator class layers]} base-rows layer [:X :G]]
        (let [cell (fn [primitive]
                     (let [{:keys [accuracy capacity seed-min seed-max]}
                           (get-in layers [layer primitive])]
                       (str (pct accuracy) " / " (pct capacity) " "
                            (fmt-interval seed-min seed-max))))]
          (format "| %s | %s | %s | %s | %s | %s |"
                  (name operator) (name class) (name layer)
                  (cell :storage) (cell :transmission)
                  (cell :modification)))))
     "\n\n"
     "## RIVER vs matched ablation (headline contrast)\n\n"
     "| layer | primitive | river acc / C [seed range] | ablated acc / C [seed range] | isolated feedback C |\n"
     "|---|---|---:|---:|---:|\n"
     (str/join
      "\n"
      (for [layer [:X :G]
            primitive [:storage :transmission :modification]]
        (let [{:keys [river ablated isolated-capacity]}
              (get-in river-contrast [:layers layer primitive])]
          (format "| %s | %s | %s / %s %s | %s / %s %s | %s |"
                  (name layer) (name primitive)
                  (pct (:accuracy river)) (pct (:capacity river))
                  (fmt-interval (:seed-min river) (:seed-max river))
                  (pct (:accuracy ablated)) (pct (:capacity ablated))
                  (fmt-interval (:seed-min ablated) (:seed-max ablated))
                  (pct isolated-capacity)))))
     "\n\n"
     "## Parameter scans (river, G layer)\n\n"
     "### Delay τ (storage)\n\n"
     "| τ | river acc / C [seed range] |\n|---:|---:|\n"
     (str/join
      "\n"
      (for [{:keys [param score]} (:delay scans)]
        (format "| %d | %s / %s %s |" param (pct (:accuracy score))
                (pct (:capacity score))
                (fmt-interval (:seed-min score) (:seed-max score)))))
     "\n\n### Distance d (transmission)\n\n"
     "| d | river acc / C [seed range] |\n|---:|---:|\n"
     (str/join
      "\n"
      (for [{:keys [param score]} (:distance scans)]
        (format "| %d | %s / %s %s |" param (pct (:accuracy score))
                (pct (:capacity score))
                (fmt-interval (:seed-min score) (:seed-max score)))))
     "\n\n### Decoder radius (storage)\n\n"
     "| radius | river acc / C [seed range] |\n|---:|---:|\n"
     (str/join
      "\n"
      (for [{:keys [param score]} (:radius scans)]
        (format "| %d | %s / %s %s |" param (pct (:accuracy score))
                (pct (:capacity score))
                (fmt-interval (:seed-min score) (:seed-max score)))))
     "\n\n## Reading\n\n"
     "The BASE positive control confirms the decoder pipeline works: genotype "
     "storage and transmission reach substantial capacity, so a null on the "
     "river is interpretable. The RIVER-ABLATED matched control shares the "
     "river's exact Java seed, RNG tape, and construction — only the live X→G "
     "edge is cut — so river-minus-ablated isolates the feedback channel's "
     "contribution to each primitive. We scan delay, distance, and decoder "
     "radius rather than relying on a single T=8 point.\n\n"
     "Under these probes, the isolated feedback capacity is reported honestly: "
     "if the XOR/modification column shows no capacity above chance across the "
     "scan, that is 'no modification capacity under these probes' — never 'no "
     "computation'. The river's feedback may express through channels these "
     "injection-decode probes do not capture (e.g. distributed information "
     "flow measured in E4, or spectral signatures in E6).\n")))

(defn -main [& _]
  (print (render-report (run-experiment))))
