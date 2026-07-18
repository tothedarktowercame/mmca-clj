(ns mmca.experiments.control-param-scan
  "E2: deterministic finite-size scan of continuous interrupter strength q.

  q is Pr(apply propagator write); 1-q holds the neighbour-blended rule. The
  experiment uses the feedforward engine only (X->G remains exactly zero).

  Operational observables:
  - genotype/phenotype innovation density = late-time changed-cell fraction;
  - effective diversity = exp(Shannon entropy of the genotype rule census);
  - collapse = eight consecutive steps with zero innovation in both layers;
  - susceptibility = L * sample-variance(late genotype innovation);
  - Binder-like cumulant = 1 - <a_G^4> / (3 <a_G^2>^2).

  No timestamps or ambient randomness enter the report."
  (:require [clojure.string :as str]
            [mmca.core :as c]))

;; NOTE (integration, claude-2): the original scan (widths to 240, 13 q, 32
;; seeds, 300 steps ~= 1664 heavy runs) does not finish under the 30-min Agency
;; job cap -- why codex-5's job died before committing. Reduced to a config that
;; completes and is reproducible; the full L=240/32-seed sweep is kept as
;; `full-config` and flagged as follow-up in holes/E2-results.md.
(def full-config
  {:writing [4 5 6 7 0 1 2 3] :seed-start 0 :seed-count 32
   :widths [30 60 120 240]
   :qs [0.0 0.025 0.05 0.075 0.1 0.125 0.15 0.2 0.3 0.4 0.5 0.75 1.0]
   :steps 300 :late-window 80 :collapse-window 8})

(def default-config
  {:writing [4 5 6 7 0 1 2 3]
   :seed-start 0
   :seed-count 16
   :widths [30 60 120]
   :qs [0.0 0.05 0.1 0.15 0.2 0.3 0.5 0.75 1.0]
   :steps 200
   :late-window 60
   :collapse-window 8})

(defn- mean [xs]
  (if (seq xs) (/ (reduce + 0.0 xs) (double (count xs))) 0.0))

(defn- sample-variance [xs]
  (if (< (count xs) 2)
    0.0
    (let [m (mean xs)]
      (/ (reduce + 0.0 (map #(let [d (- (double %) m)] (* d d)) xs))
         (double (dec (count xs)))))))

(defn- median [xs]
  (when (seq xs)
    (let [v (vec (sort xs)) n (count v) h (quot n 2)]
      (if (odd? n) (double (nth v h))
          (/ (+ (double (nth v (dec h))) (double (nth v h))) 2.0)))))

(defn effective-diversity [genotype]
  (let [n (double (count genotype))
        probabilities (map #(/ (double %) n) (vals (frequencies genotype)))
        entropy (- (reduce + 0.0 (map #(* % (Math/log %)) probabilities)))]
    (Math/exp entropy)))

(defn- innovation-series [rows width]
  (mapv (fn [[before after]]
          (/ (double (c/changed-count before after)) (double width)))
        (partition 2 1 rows)))

(defn- first-collapse-time [g-innovation x-innovation window]
  (let [quiet? (mapv #(and (zero? %1) (zero? %2))
                     g-innovation x-innovation)]
    (some (fn [i]
            (when (every? true? (subvec quiet? i (+ i window)))
              (inc i)))
          (range 0 (inc (- (count quiet?) window))))))

(defn run-one
  [{:keys [writing steps late-window collapse-window]} width q seed]
  (let [run (c/run-propagator writing seed width steps {:interrupter-q q})
        g-series (innovation-series (:gen run) width)
        x-series (innovation-series (:phe run) width)
        late-g (take-last late-window g-series)
        late-x (take-last late-window x-series)
        late-genotypes (take-last late-window (:gen run))]
    {:seed seed
     :a-G (mean late-g)
     :a-X (mean late-x)
     :effective-diversity (mean (map effective-diversity late-genotypes))
     :collapse-time (first-collapse-time g-series x-series collapse-window)}))

(defn summarize-cell [config width q seeds]
  (let [runs (mapv #(run-one config width q %) seeds)
        activities (mapv :a-G runs)
        second-moment (mean (map #(* % %) activities))
        fourth-moment (mean (map #(let [s (* % %)] (* s s)) activities))
        collapsed (keep :collapse-time runs)
        collapse-probability (/ (double (count collapsed)) (double (count runs)))]
    {:width width
     :q q
     :a-G (mean activities)
     :a-X (mean (map :a-X runs))
     :effective-diversity (mean (map :effective-diversity runs))
     :susceptibility (* width (sample-variance activities))
     :binder (if (pos? second-moment)
               (- 1.0 (/ fourth-moment (* 3.0 second-moment second-moment)))
               0.0)
     :collapse-probability collapse-probability
     :survival (- 1.0 collapse-probability)
     :median-collapse-time (median collapsed)}))

(defn- peak-by-width [rows]
  (mapv (fn [[width rs]]
          (let [peak (apply max-key :susceptibility rs)]
            {:width width :q (:q peak) :susceptibility (:susceptibility peak)}))
        (sort-by key (group-by :width rows))))

(defn- normalized-points [rows field]
  (mapcat (fn [[_width rs]]
            (let [maximum (apply max (map field rs))]
              (map #(assoc % :normalized
                           (if (pos? maximum) (/ (double (field %)) maximum) 0.0))
                   rs)))
          (group-by :width rows)))

(defn- collapse-score
  "Exploratory horizontal finite-size-collapse score for a candidate nu.
  Lower is better; :bins reports how many rescaled-x bins contain >=2 widths."
  [rows field q-critical nu]
  (let [points (normalized-points rows field)
        binned (group-by
                (fn [{:keys [width q]}]
                  (long (Math/round
                         (/ (* (- q q-critical)
                               (Math/pow (double width) (/ 1.0 nu)))
                            0.5))))
                points)
        comparable (filter (fn [[_ ps]]
                             (>= (count (distinct (map :width ps))) 2))
                           binned)
        variances (map (fn [[_ ps]] (sample-variance (map :normalized ps)))
                       comparable)]
    {:nu nu
     :rmse (Math/sqrt (mean variances))
     :bins (count comparable)}))

(defn scan
  ([] (scan default-config))
  ([{:keys [seed-start seed-count widths qs] :as config}]
   (let [seeds (range seed-start (+ seed-start seed-count))
         rows (vec (for [width widths q qs]
                     (summarize-cell config width q seeds)))
         peaks (peak-by-width rows)
         q-critical (median (map :q peaks))
         collapse-candidates
         (mapv (fn [nu]
                 {:nu nu
                  :activity (collapse-score rows :a-G q-critical nu)
                  :survival (collapse-score rows :survival q-critical nu)})
               [0.5 1.0 2.0])
         peak-q-span (- (apply max (map :q peaks)) (apply min (map :q peaks)))
         peak-growth (let [ps (sort-by :width peaks)
                           lo (:susceptibility (first ps))
                           hi (:susceptibility (last ps))]
                       (if (pos? lo) (/ hi lo) 0.0))
         intermediate-collapse?
         (some #(< 0.1 (:collapse-probability %) 0.9) rows)
         classification (cond
                          (and (<= peak-q-span 0.05) (> peak-growth 1.5))
                          :sharp-transition
                          intermediate-collapse? :metastability
                          :else :long-lived-crossover)]
     {:config config
      :rows rows
      :susceptibility-peaks peaks
      :peak-q-span peak-q-span
      :peak-growth peak-growth
      :collapse-candidates collapse-candidates
      :classification classification})))

(defn- f [x] (format "%.4f" (double x)))
(defn- maybe-time [x] (if x (format "%.1f" (double x)) ">T"))

(defn markdown-report [{:keys [config rows susceptibility-peaks peak-q-span
                               peak-growth collapse-candidates classification]}]
  (let [{:keys [writing seed-start seed-count widths steps late-window
                collapse-window]} config
        seed-end (dec (+ seed-start seed-count))
        line (fn [{:keys [width q a-G a-X effective-diversity susceptibility
                          binder collapse-probability median-collapse-time]}]
               (str "| " width " | " (f q) " | " (f a-G) " | " (f a-X)
                    " | " (f effective-diversity) " | " (f susceptibility)
                    " | " (f binder) " | " (f collapse-probability)
                    " | " (maybe-time median-collapse-time) " |"))
        peak-text (str/join ", "
                            (map #(str "L" (:width %) ":q=" (f (:q %))
                                       ",chi=" (f (:susceptibility %)))
                                 susceptibility-peaks))
        collapse-text
        (str/join "; "
                  (map (fn [{:keys [nu activity survival]}]
                         (str "nu=" nu " activity=" (f (:rmse activity))
                              "[" (:bins activity) " bins], survival="
                              (f (:rmse survival)) "[" (:bins survival) " bins]"))
                       collapse-candidates))
        reading
        (case classification
          :sharp-transition
          "The susceptibility maxima converge while growing with L, supporting a finite-size sharp-transition hypothesis. The collapse scores are exploratory rather than an exponent estimate; a denser q grid around the common peak is required before claiming a critical exponent."
          :metastability
          "At least one L,q cell mixes collapsed and surviving seeds, while the susceptibility peaks do not meet the joint convergence-and-growth bar. The honest reading is metastability: seed-dependent residence times dominate this horizon, so E3 transient scaling should test whether the mixed region sharpens or remains broad."
          "The susceptibility peaks fail the joint convergence-and-growth bar and no cell shows mixed collapse probability. The honest result at this horizon is a long-lived crossover, not evidence of a critical transition; the finite-size-collapse scores are descriptive only." )]
    (str "# E2 — Continuous interrupter finite-size scan (result)\n\n"
         "Reproduce bit-for-bit: `clojure -M -m mmca.experiments.control-param-scan`.\n"
         "Config: writing=" (pr-str writing) ", seeds " seed-start "–" seed-end
         " (" seed-count "), widths=" (pr-str widths) ", steps=" steps
         ", late window=" late-window ", collapse window=" collapse-window ".\n"
         "Feedforward base only: `run-propagator`, so X→G remains zero by construction. "
         "q=Pr(propagator write); 1-q holds the neighbour blend.\n\n"
         "Innovation density is the late changed-cell fraction. Collapse is "
         collapse-window " consecutive steps with zero G and X innovation.\n\n"
         "| L | q | a_G | a_X | exp(H(G)) | L Var(a_G) | Binder | P(collapse) | median T_c |\n"
         "|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n"
         (str/join "\n" (map line rows)) "\n\n"
         "## Finite-size diagnostics\n\n"
         "Susceptibility peaks: " peak-text ".  Peak-q span=" (f peak-q-span)
         "; chi(Lmax)/chi(Lmin)=" (f peak-growth) ".\n\n"
         "Exploratory collapse scores (lower RMSE is better; x=(q-qc)L^(1/nu)): "
         collapse-text ".\n\n"
         "## Reading — " (name classification) "\n\n" reading "\n")))

(defn -main [& _]
  (print (markdown-report (scan))))
