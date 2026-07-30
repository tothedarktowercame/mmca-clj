;; A selection loop over (gamma, initial genotype field), scored by the paper's
;; own causal-reach protocol with an explicit cost on plasticity.
;;
;; This LAYERS on the published work rather than reimplementing it:
;;   * the same protocol constants as every other row -- L=80, T=120, t*=60, dt=59;
;;   * the same river update, `c/original-river-combine-rule` + `c/river-writing`,
;;     read through the same gain gate as `scripts/river_gain.clj`;
;;   * the same two-sided calibration -- ordered below rule 90 (8.00), complex to
;;     22, chaotic beyond (rule 30 = 36.45);
;;   * the same frozen-field discipline: the stale reference is supplied from
;;     OUTSIDE and is identical in both fork branches, so the gate cannot itself
;;     carry the perturbation. That is the correctness fix river_gain.clj records.
;;
;; What is new is only the population: gamma and the initial genotype field are
;; heritable, fitness charges for gamma, and selection runs over generations.
;; The Baldwin question is whether gamma rises and then FALLS while score holds.
;; See futon5/holes/tech-notes/TN-part-III-b-baldwin-recovery.md for the
;; preregistered criteria; they were fixed before this script existed.

(require '[mmca.core :as c] '[clojure.string :as str])

(def W 80) (def STEPS 120) (def TSTAR 60) (def DT 59)
(def BAND-CENTRE 15.0)   ;; midpoint of the complex band [8, 22]
(def BAND-HALF 7.0)
(def GAMMA-LEVELS (mapv #(/ (double %) 7.0) (range 8)))

;; --- the gain gate, identical in form to river_gain.clj -----------------------
(defn gain-genotype-step
  [^java.util.Random random ^java.util.Random gate genotype phenotype next-phenotype frozen gamma]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       ;; the gate coin is drawn for EVERY cell at every step regardless of the
       ;; branch it selects, so the two damage branches stay tape-aligned
       (let [live? (< (.nextDouble gate) gamma)
             ph (if live? phenotype frozen)
             nph (if live? next-phenotype frozen)
             predecessor (if (zero? i) c/default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth ph (dec i)) 2)
                        (Character/digit (nth ph i) 2)
                        (Character/digit (nth ph (inc i)) 2)
                        (Character/digit (nth nph i) 2)])]
         (c/propagate-at
          (c/original-river-combine-rule predecessor centre successor context)
          c/river-writing
          (.nextInt random c/bit-count))))
     (range width))))

(defn run-from [random gate genotype phenotype steps gamma frozen]
  (loop [t 0 g genotype p phenotype phes [phenotype]]
    (if (= t steps) {:phe phes :gen g}
      (let [np (c/phenotype-step g p)
            ng (gain-genotype-step random gate g p np frozen gamma)]
        (recur (inc t) ng np (conj phes np))))))

;; `g0` is INJECTED rather than derived from the seed: that is what makes the
;; initial field heritable, and hence what gives assimilation somewhere to
;; accumulate. Everything else matches river_gain.clj/two-stage.
(defn two-stage [gamma seed g0 intervene frozen*]
  (let [r (java.util.Random. (long seed))
        gate (java.util.Random. (long (+ 987654321 seed)))
        ;; river_gain.clj draws the genotype from `r` BEFORE the phenotype. We
        ;; inject the genotype instead, so `r` must still be advanced by exactly
        ;; those draws or `p0` diverges from the published initial condition and
        ;; the numbers stop being comparable to the paper's rows.
        _ (c/java-random-genotype r W)
        p0 (c/java-random-phenotype r W)
        a (run-from r gate g0 p0 TSTAR gamma (or frozen* p0))
        g* (:gen a) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (run-from r gate g' p' (- STEPS TSTAR) gamma (or frozen* p0))]
    {:phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

;; --- reach, at the published protocol ---------------------------------------
(defn reach [{:keys [gamma field]} seeds sites]
  (let [ms (for [seed seeds]
             (let [ref (two-stage 1.0 seed field nil nil)
                   frozen* (nth (:phe ref) TSTAR)
                   A (two-stage gamma seed field nil frozen*)]
               (for [x sites]
                 (let [B (two-stage gamma seed field (flip-at x) frozen*)]
                   (reduce + (map #(if (= %1 %2) 0 1)
                                  (nth (:phe A) (+ TSTAR DT))
                                  (nth (:phe B) (+ TSTAR DT))))))))
        all (mapv double (flatten ms))]
    {:mean (/ (reduce + all) (count all)) :n (count all)}))

;; two-sided: peaks in the complex band, penalising stasis AND saturation, so
;; the population cannot win by evolving toward rule-30 behaviour
(defn band-score [r]
  (max 0.0 (- 1.0 (/ (Math/abs (- (double r) BAND-CENTRE)) BAND-HALF))))

(defn fitness [genome c seeds sites]
  (let [r (:mean (reach genome seeds sites))]
    {:reach r :score (- (band-score r) (* c (:gamma genome)))}))

;; --- population --------------------------------------------------------------
(defn mutate [^java.util.Random rng {:keys [gamma field]} field-rate gamma-pinned?]
  {:gamma (if gamma-pinned? gamma
            (nth GAMMA-LEVELS
                 (max 0 (min (dec (count GAMMA-LEVELS))
                             (+ (.indexOf GAMMA-LEVELS gamma)
                                (dec (.nextInt rng 3)))))))
   ;; a genotype is a vector of integer rule indices (c/java-random-genotype),
   ;; so a field mutation is a point change of one cell's rule index
   :field (mapv (fn [r] (if (< (.nextDouble rng) field-rate)
                          (.nextInt rng c/rule-count)
                          r))
                field)})

(defn -main [& args]
  (let [{:strs [c gens pop seeds sites field-rate pin]}
        (apply hash-map (map #(if (str/starts-with? % "--") (subs % 2) %) args))
        cost (Double/parseDouble (or c "0.0"))
        G (Integer/parseInt (or gens "12"))
        P (Integer/parseInt (or pop "12"))
        nseeds (Integer/parseInt (or seeds "2"))
        nsites (Integer/parseInt (or sites "8"))
        frate (Double/parseDouble (or field-rate "0.02"))
        pinned (some-> pin Double/parseDouble)
        rng (java.util.Random. 20260730)
        seed-set (range 1 (inc nseeds))
        site-set (take nsites (range 0 W (max 1 (quot W nsites))))]
    (println "gen\tmean-gamma\tmean-score\tmean-reach\tbest-score\tbest-gamma")
    (loop [gen 0
           population (vec (repeatedly P #(hash-map
                                            :gamma (or pinned (nth GAMMA-LEVELS (.nextInt rng 8)))
                                            :field (c/java-random-genotype rng W))))]
      (when (< gen G)
        (let [scored (mapv #(merge % (fitness % cost seed-set site-set)) population)
              ranked (vec (sort-by :score > scored))
              n (count ranked)
              survivors (vec (take (max 1 (quot n 2)) ranked))
              offspring (vec (repeatedly (- n (count survivors))
                                         #(mutate rng (nth survivors (.nextInt rng (count survivors)))
                                                  frate (some? pinned))))
              mg (/ (reduce + (map :gamma scored)) (double n))
              ms (/ (reduce + (map :score scored)) (double n))
              mr (/ (reduce + (map :reach scored)) (double n))
              best (first ranked)]
          (println (format "%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f"
                           gen mg ms mr (:score best) (:gamma best)))
          (flush)
          (recur (inc gen) (into (mapv #(select-keys % [:gamma :field]) survivors) offspring)))))))

(apply -main *command-line-args*)
