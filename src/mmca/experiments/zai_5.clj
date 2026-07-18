(ns mmca.experiments.zai-5
  "Excursion E6 of M-metaca-eoc: multiscale spacetime spectra S_AB(k,omega).

  Computes full spacetime power spectra for genotype activity and phenotype
  activity, their cross-spectrum, and a four-point dynamical susceptibility
  (local overlap at lag tau, then spatial covariance).

  All spectra are DESCRIPTIVE — they characterise the spatiotemporal structure
  but do not by themselves establish criticality.

  Deterministic: same seed/width/steps => identical output."
  (:require [mmca.core :as c]
            [mmca.rng :as rng])
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

;; -- main experiment --------------------------------------------------------

(defn run-spectra
  "Run one simulation and return spectra data.
  writing = positional writing (e.g. [4 5 6 7 0 1 2 3])
  seed, width, steps, engine ('base or 'river)."
  [writing seed width steps engine]
  (let [result (case engine
                 :base (c/run-propagator writing seed width steps)
                 :river (c/run-river seed width steps))
        gen-rows (:gen result)
        phe-rows (:phe result)
        T (dec (count gen-rows))  ; activity has T-1 rows
        grid-G (genotype-activity-grid gen-rows width)
        grid-X (phenotype-activity-grid phe-rows width)]
    {:grid-G grid-G :grid-X grid-X :T T :width width}))

(defn -main [& _]
  (let [writing [4 5 6 7 0 1 2 3]   ; offset+4 (all-even, collapses)
        seed 0 width 60 steps 80
        engine :base
        k-scan (range 0 (inc (/ width 2)) 5)   ; k = 0,5,10,15,20,25,30
        om-scan (range -10 11 5)                ; omega = -10,-5,0,5,10
        tau-list [1 5 10]
        max-r 15]
    (println "E6 spacetime spectra | offset+4 | base engine | seed=0 W=60 T=80")
    (let [{:keys [grid-G grid-X T width]} (run-spectra writing seed width steps engine)]
      ;; Power spectra at selected (k, omega) bins
      (println "\n  S_GG(k,omega) - genotype activity power spectrum:")
      (doseq [k k-scan]
        (let [powers (for [om om-scan]
                       (dft-2d-power grid-G width T k om))]
          (println (format "    k=%-3d %s" k
                           (apply str (map #(format "%10.2f" %) powers))))))
      (println (format "           %s" (apply str (map #(format "%10s" (str "om=" %)) om-scan))))
      (println "\n  S_XX(k,omega) - phenotype activity power spectrum:")
      (doseq [k k-scan]
        (let [powers (for [om om-scan]
                       (dft-2d-power grid-X width T k om))]
          (println (format "    k=%-3d %s" k
                           (apply str (map #(format "%10.2f" %) powers))))))
      ;; Cross-spectrum S_GX at selected bins
      (println "\n  S_GX(k,omega) - cross-spectrum (cospectrum = Re):")
      (let [cross (compute-cross-spectrum grid-G grid-X width T
                                          [0 5 10 15 20 25 30] [-10 -5 0 5 10])]
        (doseq [{:keys [k omega cross]} cross]
          (println (format "    k=%-3d  om=%-4d  cross=%12.2f" k omega cross))))
      ;; Four-point susceptibility: spatial covariance of overlap at lag tau
      (println "\n  Four-point susceptibility C_overlap(r) at lag tau:")
      (doseq [tau tau-list]
        (let [ov-T (- T tau)
              ov (local-overlap grid-G width T tau)
              cov (spatial-covariance ov width ov-T max-r)]
          (println (format "    tau=%d: %s" tau
                           (apply str (map #(format " %.4f" %) cov))))))
      ;; Ridges: find peak (k,omega) for each layer
      (let [peak-G (apply max-key :power
                          (for [k (range 0 (inc (/ width 2)))
                                om (range (int (- (/ T 2))) (inc (int (/ T 2))))]
                            {:k k :omega om :power (dft-2d-power grid-G width T k om)}))
            peak-X (apply max-key :power
                          (for [k (range 0 (inc (/ width 2)))
                                om (range (int (- (/ T 2))) (inc (int (/ T 2))))]
                            {:k k :omega om :power (dft-2d-power grid-X width T k om)}))]
        (println (format "\n  Peak S_GG at k=%d omega=%d (power=%.1f)"
                         (:k peak-G) (:omega peak-G) (:power peak-G)))
        (println (format "  Peak S_XX at k=%d omega=%d (power=%.1f)"
                         (:k peak-X) (:omega peak-X) (:power peak-X)))))))
