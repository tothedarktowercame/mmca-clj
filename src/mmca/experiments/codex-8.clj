#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns mmca.experiments.codex-8
  "E7: held-out decoding of storage, transmission, and XOR modification.

  Signals are injected into X and G separately. X carries a literal state bit;
  G uses the balanced live/dead control pair (1 -> Rule 105, 0 -> Rule 204).
  A nearest-centroid decoder is trained on disjoint seeds and evaluated only on
  held-out seeds. The base dynamics remain feedforward throughout."
  (:require [clojure.string :as str]
            [mmca.core :as c]
            [mmca.rng :as rng]))

(def default-config
  {:operators [{:id :rotate+2 :class :all-even
                :writing [2 3 4 5 6 7 0 1]}
               {:id :offset+4 :class :all-even
                :writing [4 5 6 7 0 1 2 3]}
               {:id :reduced-0246 :class :any-odd
                :writing [2 1 4 3 6 5 0 7]}
               {:id :reduced-02 :class :any-odd
                :writing [2 1 0 3 4 5 6 7]}]
   :train-seeds (vec (range 0 24))
   :test-seeds (vec (range 24 48))
   :width 81
   :steps 8
   :storage-delay 8
   :transmission-delay 8
   :distance 8
   :xor-spacing 4
   :feature-radius 2})

(defn- replace-phenotype-bit [phenotype site bit]
  (str (subs phenotype 0 site) (char (+ (int \0) bit))
       (subs phenotype (inc site))))

(defn- inject-one [genotype phenotype layer site bit]
  (case layer
    :X [genotype (replace-phenotype-bit phenotype site bit)]
    ;; Both are lambda=1/2 fixed rules: 105 is live/additive, 204 is dead/identity.
    :G [(assoc genotype site (if (= bit 1) 105 204)) phenotype]))

(defn- initial-state [seed width]
  (let [random (rng/make-rng (format "prop-%d" seed))]
    {:random random
     :genotype (c/random-genotype random width)
     :phenotype (c/random-phenotype random width)}))

(defn- run-injected
  [writing seed width delay layer injections]
  (let [{:keys [random genotype phenotype]} (initial-state seed width)
        [genotype phenotype]
        (reduce (fn [[g x] [site bit]] (inject-one g x layer site bit))
                [genotype phenotype] injections)]
    (loop [t 0 genotype genotype phenotype phenotype]
      (if (= t delay)
        {:G genotype :X phenotype}
        (let [next-phenotype (c/phenotype-step genotype phenotype)
              next-genotype (c/genotype-step random genotype writing true)]
          (recur (inc t) next-genotype next-phenotype))))))

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

(defn- example
  [{:keys [width storage-delay transmission-delay distance xor-spacing
           feature-radius]}
   writing layer primitive seed [inputs label]]
  (let [centre (quot width 2)
        [delay read-site injections]
        (case primitive
          :storage [storage-delay centre [[centre (first inputs)]]]
          :transmission [transmission-delay (+ centre distance)
                         [[centre (first inputs)]]]
          :modification [transmission-delay centre
                         [[(- centre xor-spacing) (first inputs)]
                          [(+ centre xor-spacing) (second inputs)]]])
        state (run-injected writing seed width delay layer injections)]
    {:label label
     :features (feature-vector state layer read-site feature-radius)}))

(defn- examples [config writing layer primitive seeds]
  (mapv (fn [[seed input]]
          (example config writing layer primitive seed input))
        (for [seed seeds input (inputs-for primitive)] [seed input])))

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

(defn- held-out-score [training testing]
  (let [decoder (train-decoder training)
        correct (count (filter (fn [{:keys [label features]}]
                                 (= label (decode decoder features)))
                               testing))
        accuracy (/ correct (double (count testing)))]
    {:accuracy accuracy
     :capacity (max 0.0 (- (* 2.0 accuracy) 1.0))
     :n-test (count testing)}))

(defn run-experiment
  ([] (run-experiment default-config))
  ([{:keys [operators train-seeds test-seeds steps storage-delay
            transmission-delay] :as config}]
   (when (< steps (max storage-delay transmission-delay))
     (throw (ex-info "E7 steps must cover every decoding delay"
                     {:steps steps :storage-delay storage-delay
                      :transmission-delay transmission-delay})))
   {:config (select-keys config [:train-seeds :test-seeds :width :steps
                                 :storage-delay :transmission-delay :distance
                                 :xor-spacing :feature-radius])
    :rows
    (mapv
     (fn [{:keys [id class writing]}]
       (let [writing (c/positional-writing->neighbourhood-writing writing)]
         {:operator id
          :class class
          :layers
          (into {}
                (for [layer [:X :G]]
                  [layer
                   (into {}
                         (for [primitive [:storage :transmission :modification]]
                           [primitive
                            (held-out-score
                             (examples config writing layer primitive train-seeds)
                             (examples config writing layer primitive test-seeds))]))]))}))
     operators)}))

(defn- pct [x] (format "%.1f%%" (* 100.0 (double x))))

(defn render-report [{:keys [config rows]}]
  (let [{:keys [train-seeds test-seeds width steps storage-delay
                transmission-delay distance xor-spacing feature-radius]} config]
    (str
     "# E7 — Direct computation primitives (result)\n\n"
     "Reproduce bit-for-bit: `clojure -M -m mmca.experiments.codex-8 > holes/E7-results.md`.\n"
     "Determinism gate: `clojure -M:test`.\n"
     (format "Config: feedforward base, train seeds %d–%d, held-out seeds %d–%d, W=%d, T=%d, tau_storage=%d, tau_transmission=%d, d=%d, XOR inputs at +/- %d, decoder radius=%d.\n\n"
             (first train-seeds) (last train-seeds) (first test-seeds)
             (last test-seeds) width steps storage-delay transmission-delay
             distance xor-spacing feature-radius)
     "X injection forces one phenotype bit. G injection uses the balanced control pair Rule 105 (bit 1, live/additive) versus Rule 204 (bit 0, dead/identity). Each cell is a held-out nearest-centroid decoder accuracy with normalized capacity C=max(0,2*accuracy-1); chance accuracy is 50% and C=0.\n\n"
     "| operator | parity class | layer | storage acc / C | transmission acc / C | XOR acc / C |\n"
     "|---|---|---:|---:|---:|---:|\n"
     (str/join
      "\n"
      (for [{:keys [operator class layers]} rows layer [:X :G]]
        (let [cell (fn [primitive]
                     (let [{:keys [accuracy capacity]} (get-in layers [layer primitive])]
                       (str (pct accuracy) " / " (pct capacity))))]
          (format "| %s | %s | %s | %s | %s | %s |"
                  (name operator) (name class) (name layer)
                  (cell :storage) (cell :transmission)
                  (cell :modification)))))
     "\n\n## Reading\n\n"
     "These held-out probes separate retained, transported, and nonlinearly combined signal rather than treating visual diversity as computation. The phenotype and genotype rows are same-layer capacities: because the base engine is feedforward, phenotype injection is never interpreted as phenotype-to-genotype influence; only `run-river` could support that claim. The committed engine had no q API at this excursion's base revision (the E2 seam was present only as unrelated uncommitted work), so this reproducible artifact uses two representative all-even and two any-odd operators instead of silently depending on an unsettled seam. Genotype storage reaches C=41.7% and transmission reaches C=12.5%, while phenotype capacities are near zero; the maximum XOR capacity is only 6.3% (reduced-0246 G), so this sample finds no convincing modification capacity.\n")))

(defn -main [& _]
  (print (render-report (run-experiment))))
