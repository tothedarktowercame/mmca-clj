(ns mmca.experiments.multiscale-spectra
  "Excursion E6 of M-metaca-eoc: multiscale spacetime spectra S_AB(k,omega).

  Correction round (v2): the river panel now uses the AUTHENTIC paper river
  (c/run-river), and adds a MATCHED feedback-off control (c/run-river-ablated)
  that shares the river's exact Java seed, initial state, RNG tape, and
  constant-zero quad-4cand construction -- only the live X->G edge is cut (the
  genotype step reads the frozen initial phenotype). The contrast 'river minus
  matched ablation' isolates the feedback-spectral signature.

  The ensemble path averages demeaned cross-spectra over seeds and reports
  seed intervals (min/max), not bare averages. DC-dominated raw spectra from a
  single seed cannot establish 'significance', so that word is avoided; we
  report descriptive structure and the feedback contrast.

  All spectra are DESCRIPTIVE -- they characterise the spatiotemporal structure
  but do not by themselves establish criticality.

  Deterministic: same seed/width/steps => identical output."
  (:require [mmca.core :as c])
  (:import [java.lang Math]))

;; -- math helpers -----------------------------------------------------------

(def PI Math/PI)
(def TWO-PI (* 2.0 PI))

(defn cos-fn [x] (Math/cos x))
(defn sin-fn [x] (Math/sin x))

(defn dft-1d-real
  "Compute the DFT of a real sequence at frequency index k (0-indexed, N length).
  Returns [re im]."
  [data k N]
  (let [omega (* -2.0 PI (/ (double k) N))]
    (loop [i 0 re 0.0 im 0.0]
      (if (= i N)
        [re im]
        (let [angle (* omega i)
              x (double (nth data i))]
          (recur (inc i)
                 (+ re (* x (cos-fn angle)))
                 (+ im (* x (sin-fn angle)))))))))

(defn dft-2d-power
  "Compute S(k,omega) = |sum_{x,t} f(x,t) e^{-i(kx+omega t)}|^2 / (W*T).
  Uses direct DFT (O(W*T) per frequency pair). For the frequency bins we use
  k in [0, W/2] and omega in [-T/2, T/2]."
  [grid W T k-bin omega-bin]
  (let [k (if (> k-bin (/ W 2)) (- k-bin W) k-bin)
        om (if (> omega-bin (/ T 2)) (- omega-bin T) omega-bin)
        k-norm (* TWO-PI (/ (double k) W))
        om-norm (* TWO-PI (/ (double om) T))]
    (loop [x 0 t 0 re 0.0 im 0.0]
      (cond
        (= t T) (+ (* re re) (* im im))
        (= x W) (recur 0 (inc t) re im)
        :else
        (let [phase (+ (* k-norm x) (* om-norm t))
              val (double (get-in grid [t x]))]
          (recur (inc x) t
                 (+ re (* val (cos-fn phase)))
                 (+ im (* val (sin-fn phase)))))))))

;; -- activity extraction ----------------------------------------------------

(defn genotype-activity-grid
  "Build a W x T activity grid from genotype history.
  Activity[i,t] = 1 if rule byte at position i changed from t to t+1, else 0.
  Returns grid indexed as [t][x], t=0..T-2 (one fewer than total rows)."
  [gen-rows width]
  (let [T (dec (count gen-rows))]
    (loop [t 0 result []]
      (if (= t T)
        result
        (let [row-a (nth gen-rows t)
              row-b (nth gen-rows (inc t))]
          (recur (inc t)
                 (conj result (mapv #(if (= %1 %2) 0 1) row-a row-b))))))))

(defn phenotype-activity-grid
  "Build a W x T activity grid from phenotype history.
  Activity[i,t] = 1 if bit at position i changed from t to t+1, else 0."
  [phe-rows width]
  (let [T (dec (count phe-rows))]
    (loop [t 0 result []]
      (if (= t T)
        result
        (let [row-a (nth phe-rows t)
              row-b (nth phe-rows (inc t))]
          (recur (inc t)
                 (conj result (mapv #(if (= %1 %2) 0 1)
                                    row-a row-b))))))))

;; -- demeaning (for ensemble cross-spectra) ---------------------------------

(defn grid-temporal-mean
  "Per-cell temporal mean of a [T][W] grid. Returns a [W] vector of means."
  [grid width]
  (let [T (count grid)]
    (mapv (fn [x]
            (/ (double (reduce + (map #(get-in % [x]) grid))) T))
          (range width))))

(defn demean-grid
  "Subtract the per-cell temporal mean from each cell. This removes the DC
  (k=0, omega=0) component that dominates raw activity spectra, revealing
  off-DC structure. Returns a new [T][W] grid."
  [grid width]
  (let [means (grid-temporal-mean grid width)]
    (mapv (fn [row]
            (mapv (fn [x] (- (get-in row [x]) (nth means x))) (range width)))
          grid)))

;; -- spectra ----------------------------------------------------------------

(defn compute-power-spectrum
  "Compute S(k,omega) for k in [0, W/2], omega in [-T/2, T/2].
  Returns a map {:k-bin :omega-bin :power}."
  [grid width time-steps]
  (let [k-max (int (/ width 2))
        om-min (int (- (/ time-steps 2)))
        om-max (int (dec (/ time-steps 2)))]
    (for [k (range 0 (inc k-max))
          om (range om-min (inc om-max))]
      {:k k :omega om :power (dft-2d-power grid width time-steps k om)})))

(defn compute-cross-spectrum
  "Compute S_GX(k,omega) = Re(F_G * conj(F_X)) at selected bins.
  Returns {:k :omega :cross} values (real part = cospectrum)."
  [grid-G grid-X width time-steps k-list omega-list]
  (for [k k-list
        om omega-list]
    (let [k-n (if (> k (/ width 2)) (- k width) k)
          om-n (if (> om (/ time-steps 2)) (- om time-steps) om)
          kn (* TWO-PI (/ (double k-n) width))
          on (* TWO-PI (/ (double om-n) time-steps))]
      (loop [x 0 t 0 reG 0.0 imG 0.0 reX 0.0 imX 0.0]
        (cond
          (= t time-steps) {:k k :omega om
                            :cross (+ (* reG reX) (* imG imX))} ; Re(F_G * conj(F_X))
          (= x width) (recur 0 (inc t) reG imG reX imX)
          :else
          (let [phase (+ (* kn x) (* on t))
                co (cos-fn phase) si (sin-fn phase)
                vG (double (get-in grid-G [t x]))
                vX (double (get-in grid-X [t x]))]
            (recur (inc x) t
                   (+ reG (* vG co)) (+ imG (* vG si))
                   (+ reX (* vX co)) (+ imX (* vX si)))))))))

;; -- four-point susceptibility ----------------------------------------------

(defn local-overlap
  "Compute local overlap O(x,t,tau) = A(x,t) * A(x,t+tau) for activity grid.
  Returns grid of same width but T-tau time steps."
  [grid width T tau]
  (mapv (fn [t]
          (mapv (fn [x] (* (get-in grid [t x]) (get-in grid [(+ t tau) x])))
                (range width)))
        (range 0 (- T tau))))

(defn spatial-covariance
  "Mean over time of the product A(x,t)*A(x+r,t), minus mean(A)^2.
  Returns C(r) for r = 0..max-r."
  [grid width T max-r]
  (let [total (reduce + (for [t (range T) x (range width)]
                          (get-in grid [t x])))
        mean-act (/ (double total) (* width T))]
    (for [r (range 0 (inc max-r))]
      (let [n-pairs (* T (- width r))
            sum-prod (reduce + (for [t (range T) x (range (- width r))]
                                 (* (get-in grid [t x]) (get-in grid [t (+ x r)]))))
            mean-prod (/ (double sum-prod) n-pairs)]
        (- mean-prod (* mean-act mean-act))))))

;; -- main experiment: single-seed spectra -----------------------------------

(defn run-spectra
  "Run one simulation and return spectra data.
  writing = positional writing (e.g. [4 5 6 7 0 1 2 3])
  seed, width, steps, engine (:base, :river, :river-ablated)."
  [writing seed width steps engine]
  (let [result (case engine
                 :base (c/run-propagator writing seed width steps)
                 :river (c/run-river seed width steps)
                 :river-ablated (c/run-river-ablated seed width steps))
        gen-rows (:gen result)
        phe-rows (:phe result)
        T (dec (count gen-rows))  ; activity has T-1 rows
        grid-G (genotype-activity-grid gen-rows width)
        grid-X (phenotype-activity-grid phe-rows width)]
    {:grid-G grid-G :grid-X grid-X :T T :width width}))

(defn print-spectra
  "Print spectra for one config."
  [label grid-G grid-X T width k-scan om-scan tau-list max-r]
  (println (format "\n=== %s (T=%d, W=%d) ===" label T width))
  (println "\n  S_GG(k,omega) - genotype activity power spectrum:")
  (doseq [k k-scan]
    (let [powers (for [om om-scan] (dft-2d-power grid-G width T k om))]
      (println (format "    k=%-3d %s" k (apply str (map #(format "%10.2f" %) powers))))))
  (println (format "           %s" (apply str (map #(format "%10s" (str "om=" %)) om-scan))))
  (println "\n  S_XX(k,omega) - phenotype activity power spectrum:")
  (doseq [k k-scan]
    (let [powers (for [om om-scan] (dft-2d-power grid-X width T k om))]
      (println (format "    k=%-3d %s" k (apply str (map #(format "%10.2f" %) powers))))))
  (println "\n  S_GX(k,omega) - cross-spectrum (cospectrum = Re):")
  (let [cross (compute-cross-spectrum grid-G grid-X width T
                                      [0 5 10 15 20 25 30] [-10 -5 0 5 10])]
    (doseq [{:keys [k omega cross]} cross]
      (println (format "    k=%-3d  om=%-4d  cross=%12.2f" k omega cross))))
  (println "\n  Four-point susceptibility C_overlap(r) at lag tau:")
  (doseq [tau tau-list]
    (let [ov-T (- T tau)
          ov (local-overlap grid-G width T tau)
          cov (spatial-covariance ov width ov-T max-r)]
      (println (format "    tau=%d: %s" tau (apply str (map #(format " %.4f" %) cov))))))
  (let [k-range (range 0 (inc (/ width 2)))
        om-range (range (int (- (/ T 2))) (inc (int (/ T 2))))
        peak-G (apply max-key :power
                      (for [k k-range om om-range]
                        {:k k :omega om :power (dft-2d-power grid-G width T k om)}))
        peak-X (apply max-key :power
                      (for [k k-range om om-range]
                        {:k k :omega om :power (dft-2d-power grid-X width T k om)}))]
    (println (format "\n  Peak S_GG at k=%d omega=%d (power=%.1f)" (:k peak-G) (:omega peak-G) (:power peak-G)))
    (println (format "  Peak S_XX at k=%d omega=%d (power=%.1f)" (:k peak-X) (:omega peak-X) (:power peak-X)))))

;; -- ensemble: seed-averaged demeaned cross-spectra ------------------------

(defn ensemble-cross-spectrum
  "Compute the demeaned cross-spectrum S_GX(k,omega) for a single seed/engine,
  then collect across seeds. Demeaning removes the DC dominance so off-DC
  structure is visible per-seed.

  Returns a map from [k omega] -> demeaned-cross value for the given seed."
  [seed width steps engine k-list omega-list]
  (let [{:keys [grid-G grid-X T]} (run-spectra nil seed width steps engine)
        dmG (demean-grid grid-G width)
        dmX (demean-grid grid-X width)]
    (into {}
          (for [{:keys [k omega cross]}
                (compute-cross-spectrum dmG dmX width T k-list omega-list)]
            [[k omega] cross]))))

(defn ensemble-averaged-cross
  "Average the demeaned cross-spectrum across seeds for one engine.
  Returns a map from [k omega] -> {:mean :lo :hi} over the seed ensemble."
  [seeds width steps engine k-list omega-list]
  (let [per-seed (mapv #(ensemble-cross-spectrum % width steps engine
                                                 k-list omega-list)
                       seeds)
        n (count seeds)]
    (into {}
          (for [kk (keys (first per-seed))]
            (let [vals (mapv #(get % kk) per-seed)
                  mean (/ (reduce + vals) (double n))]
              [kk {:mean mean
                   :lo (apply min vals)
                   :hi (apply max vals)}])))))

(defn ensemble-power
  "Average the power spectrum S_GG or S_XX across seeds for one engine.
  grid-key is :grid-G or :grid-X. Returns a map from [k omega] -> {:mean :lo :hi}."
  [seeds width steps engine grid-key k-list omega-list]
  (let [per-seed (mapv (fn [seed]
                         (let [grids (run-spectra nil seed width steps engine)
                               grid (grid-key grids)
                               T (:T grids)]
                           (into {}
                                 (for [k k-list om omega-list]
                                   [[k om] (dft-2d-power grid width T k om)]))))
                       seeds)
        n (count seeds)]
    (into {}
          (for [kk (keys (first per-seed))]
            (let [vals (mapv #(get % kk) per-seed)
                  mean (/ (reduce + vals) (double n))]
              [kk {:mean mean
                   :lo (apply min vals)
                   :hi (apply max vals)}])))))

(defn ensemble-susceptibility
  "Average the four-point susceptibility C_overlap(r) across seeds for one engine.
  Returns a map from tau -> vector of {:mean :lo :hi} per r."
  [seeds width steps engine tau-list max-r]
  (let [per-seed (mapv (fn [seed]
                         (let [{:keys [grid-G T]} (run-spectra nil seed width steps engine)]
                           (into {}
                                 (for [tau tau-list]
                                   (let [ov-T (- T tau)
                                         ov (local-overlap grid-G width T tau)
                                         cov (spatial-covariance ov width ov-T max-r)]
                                     [tau cov])))))
                       seeds)
        n (count seeds)]
    (into {}
          (for [tau tau-list]
            [tau (mapv (fn [r-idx]
                         (let [vals (mapv #(nth (get % tau) r-idx) per-seed)
                               mean (/ (reduce + vals) (double n))]
                           {:mean mean :lo (apply min vals) :hi (apply max vals)}))
                       (range (inc max-r)))]))))

(defn format-interval
  "Format a {:mean :lo :hi} map as 'mean [lo, hi]'."
  [{:keys [mean lo hi]} width-places]
  (let [fmt (str "%." width-places "f [%." width-places "f, %." width-places "f]")]
    (format fmt mean lo hi)))

(defn- format-row
  "Format a row of interval strings with fixed-width columns."
  [k-label cells]
  (str k-label (apply str (map #(format "%22s" %) cells))))

(defn- interval-cells
  "Build a vector of formatted interval strings for given k across om-scan."
  [result k om-scan places]
  (vec (for [om om-scan] (format-interval (get result [k om]) places))))

(defn print-ensemble-cross
  "Print the ensemble-averaged demeaned cross-spectrum table for one engine."
  [label seeds width steps engine k-scan om-scan]
  (let [result (ensemble-averaged-cross seeds width steps engine k-scan om-scan)]
    (println (format "\n  %s demeaned S_GX(k,omega) [ensemble mean, seed-range]:" label))
    (println (format "    seeds %d-%d, demeaned per-cell (DC removed)" (first seeds) (last seeds)))
    (doseq [k k-scan]
      (println (format-row (format "    k=%-3d " k)
                           (interval-cells result k om-scan 1))))
    (println (format-row "           " (mapv #(str "om=" %) om-scan)))))

(defn print-ensemble-power
  "Print the ensemble-averaged power spectrum table for one engine."
  [label seeds width steps engine grid-key k-scan om-scan]
  (let [result (ensemble-power seeds width steps engine grid-key k-scan om-scan)]
    (println (format "\n  %s %s(k,omega) [ensemble mean, seed-range]:" label (name grid-key)))
    (doseq [k k-scan]
      (println (format-row (format "    k=%-3d " k)
                           (interval-cells result k om-scan 0))))))

(defn print-ensemble-susceptibility
  "Print the ensemble-averaged four-point susceptibility for one engine."
  [label seeds width steps engine tau-list max-r]
  (let [result (ensemble-susceptibility seeds width steps engine tau-list max-r)]
    (println (format "\n  %s four-point susceptibility C_overlap(r) [mean, seed-range]:" label))
    (doseq [tau tau-list]
      (let [cells (mapv #(format-interval % 4) (get result tau))]
        (println (format-row (format "    tau=%d: " tau)
                             (mapv #(format " %s" %) cells))))))) 

;; -- main -------------------------------------------------------------------

(def default-config
  {:seed 0 :width 60 :steps 80
   :ensemble-seeds (vec (range 8))
   :k-scan [0 5 10 15 20 25 30]
   :om-scan [-10 -5 0 5 10]
   :tau-list [1 5 10]
   :max-r 15})

(defn -main [& _]
  (let [{:keys [seed width steps ensemble-seeds
                k-scan om-scan tau-list max-r]} default-config
        k-scan-vec (vec k-scan)
        om-scan-vec (vec om-scan)]
    (println "E6 multiscale spacetime spectra (correction round v2)")
    (println (format "  single-seed panels: seed=%d W=%d steps=%d" seed width steps))
    (println (format "  ensemble: seeds %d-%d, demeaned cross-spectra"
                     (first ensemble-seeds) (last ensemble-seeds)))
    (println "  river = c/run-river (authentic); river-ablated = matched feedback-off control")
    ;; ---- Panel 1: offset+2 / base (sustained) ----
    (let [{:keys [grid-G grid-X T width]}
          (run-spectra [2 3 4 5 6 7 0 1] seed width steps :base)]
      (print-spectra "offset+2 / base (sustained)" grid-G grid-X T width
                     k-scan-vec om-scan-vec tau-list max-r))
    ;; ---- Panel 2: river / feedback (single-seed detailed) ----
    (let [{:keys [grid-G grid-X T width]}
          (run-spectra nil seed width steps :river)]
      (print-spectra "river / feedback (authentic, treatment)" grid-G grid-X T width
                     k-scan-vec om-scan-vec tau-list max-r))
    ;; ---- Panel 3: river-ablated / matched feedback-off control (single-seed) ----
    (let [{:keys [grid-G grid-X T width]}
          (run-spectra nil seed width steps :river-ablated)]
      (print-spectra "river-ablated / matched feedback-off (control)" grid-G grid-X T width
                     k-scan-vec om-scan-vec tau-list max-r))
    ;; ---- Ensemble: demeaned cross-spectrum, river vs ablated ----
    (println "\n=== ENSEMBLE: demeaned cross-spectra (river vs matched ablation) ===")
    (println "  Demeaning removes per-cell temporal mean (DC) before the cross-DFT.")
    (println "  The contrast 'river - ablated' isolates the feedback-spectral structure.")
    (print-ensemble-cross "river" ensemble-seeds width steps :river k-scan-vec om-scan-vec)
    (print-ensemble-cross "river-ablated" ensemble-seeds width steps :river-ablated k-scan-vec om-scan-vec)
    ;; ---- Ensemble: power spectra (river vs ablated) ----
    (println "\n=== ENSEMBLE: power spectra (river vs matched ablation) ===")
    (print-ensemble-power "river" ensemble-seeds width steps :river :grid-G k-scan-vec om-scan-vec)
    (print-ensemble-power "river-ablated" ensemble-seeds width steps :river-ablated :grid-G k-scan-vec om-scan-vec)
    ;; ---- Ensemble: susceptibility (river vs ablated) ----
    (println "\n=== ENSEMBLE: four-point susceptibility (river vs matched ablation) ===")
    (print-ensemble-susceptibility "river" ensemble-seeds width steps :river tau-list max-r)
    (print-ensemble-susceptibility "river-ablated" ensemble-seeds width steps :river-ablated tau-list max-r)
    ;; ---- Feedback contrast summary ----
    (let [cross-rv (ensemble-averaged-cross ensemble-seeds width steps :river k-scan-vec om-scan-vec)
          cross-ab (ensemble-averaged-cross ensemble-seeds width steps :river-ablated k-scan-vec om-scan-vec)
          nyquist (last k-scan-vec)]
      (println "\n=== FEEDBACK CONTRAST: demeaned S_GX(k=Nyquist, omega=0) ===")
      (println (format "  k=%d (Nyquist for W=%d):" nyquist width))
      (let [rv (get cross-rv [nyquist 0])
            ab (get cross-ab [nyquist 0])]
        (println (format "    river:          %s" (format-interval rv 2)))
        (println (format "    river-ablated:  %s" (format-interval ab 2)))
        (println (format "    contrast (rv-ab mean): %.2f" (- (:mean rv) (:mean ab)))))
      (println "\n  Does the off-DC S_GX structure at k=Nyquist survive ablation?")
      (let [rv-mean (:mean (get cross-rv [nyquist 0]))
            ab-mean (:mean (get cross-ab [nyquist 0]))]
        (println (format "    river mean=%.2f, ablated mean=%.2f, ratio=%.2f"
                         rv-mean ab-mean
                         (if (zero? ab-mean)
                           Double/POSITIVE_INFINITY
                           (/ rv-mean ab-mean))))))))
