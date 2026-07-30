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
;; G5: zero lets a lineage stop rewriting entirely and become a FIXED field, which
;; is the blind destination -- fixed rules 90/110/54 sit in the complex band at
;; 8.00/16.68/18.30. Without a reachable zero, the gamma=0 organism is only a blind
;; REWRITER and assimilation has nowhere to land.
(def UPDATE-LEVELS [0.0 0.25 0.5 0.75 1.0])

;; --- the gain gate, identical in form to river_gain.clj -----------------------
(defn gain-genotype-step
  [^java.util.Random random ^java.util.Random gate ^java.util.Random upd
   genotype phenotype next-phenotype frozen gamma update-prob]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       ;; Every coin and every source draw is taken for EVERY cell at every step,
       ;; whatever is then decided with them, so the two damage branches stay
       ;; tape-aligned. The update coin comes from a THIRD stream so that adding
       ;; G5 leaves the gate and source tapes bit-identical to the published dial;
       ;; at update-prob = 1 this reduces exactly to river_gain.clj.
       (let [ucoin (.nextDouble upd)
             live? (< (.nextDouble gate) gamma)
             ph (if live? phenotype frozen)
             nph (if live? next-phenotype frozen)
             predecessor (if (zero? i) c/default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth ph (dec i)) 2)
                        (Character/digit (nth ph i) 2)
                        (Character/digit (nth ph (inc i)) 2)
                        (Character/digit (nth nph i) 2)])
             ;; source drawn unconditionally, used only when the cell rewrites
             source (.nextInt random c/bit-count)]
         (if (< ucoin update-prob)
           (c/propagate-at
            (c/original-river-combine-rule predecessor centre successor context)
            c/river-writing
            source)
           centre)))                          ; G5: hold the rule -- no rewrite
     (range width))))

(defn run-from [random gate upd genotype phenotype steps gamma update-prob frozen]
  (loop [t 0 g genotype p phenotype phes [phenotype]]
    (if (= t steps) {:phe phes :gen g}
      (let [np (c/phenotype-step g p)
            ng (gain-genotype-step random gate upd g p np frozen gamma update-prob)]
        (recur (inc t) ng np (conj phes np))))))

;; `g0` is INJECTED rather than derived from the seed: that is what makes the
;; initial field heritable, and hence what gives assimilation somewhere to
;; accumulate. Everything else matches river_gain.clj/two-stage.
(defn two-stage [gamma update-prob seed g0 intervene frozen*]
  (let [r (java.util.Random. (long seed))
        gate (java.util.Random. (long (+ 987654321 seed)))
        upd (java.util.Random. (long (+ 123456789 seed)))
        ;; river_gain.clj draws the genotype from `r` BEFORE the phenotype. We
        ;; inject the genotype instead, so `r` must still be advanced by exactly
        ;; those draws or `p0` diverges from the published initial condition and
        ;; the numbers stop being comparable to the paper's rows.
        _ (c/java-random-genotype r W)
        p0 (c/java-random-phenotype r W)
        a (run-from r gate upd g0 p0 TSTAR gamma update-prob (or frozen* p0))
        g* (:gen a) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (run-from r gate upd g' p' (- STEPS TSTAR) gamma update-prob (or frozen* p0))]
    {:phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

;; --- reach, at the published protocol ---------------------------------------
(defn reach [{:keys [gamma update-prob field]} seeds sites]
  (let [ms (for [seed seeds]
             (let [up (if (nil? update-prob) 1.0 update-prob)
                   ref (two-stage 1.0 up seed field nil nil)
                   frozen* (nth (:phe ref) TSTAR)
                   A (two-stage gamma up seed field nil frozen*)]
               (for [x sites]
                 (let [B (two-stage gamma up seed field (flip-at x) frozen*)]
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
(defn- step-level [^java.util.Random rng levels v]
  (nth levels (max 0 (min (dec (count levels))
                          (+ (.indexOf levels v) (dec (.nextInt rng 3)))))))

(defn mutate [^java.util.Random rng {:keys [gamma update-prob field]} field-rate gamma-pinned?]
  {:update-prob (step-level rng UPDATE-LEVELS (or update-prob 1.0))
   :gamma (if gamma-pinned? gamma
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
  (let [{:strs [c gens pop seeds sites field-rate pin warmup]}
        (apply hash-map (map #(if (str/starts-with? % "--") (subs % 2) %) args))
        cost (Double/parseDouble (or c "0.0"))
        G (Integer/parseInt (or gens "12"))
        P (Integer/parseInt (or pop "12"))
        nseeds (Integer/parseInt (or seeds "2"))
        nsites (Integer/parseInt (or sites "8"))
        frate (Double/parseDouble (or field-rate "0.02"))
        pinned (some-> pin Double/parseDouble)
        warm (Integer/parseInt (or warmup "8"))
        rng (java.util.Random. 20260730)
        seed-set (range 1 (inc nseeds))
        site-set (take nsites (range 0 W (max 1 (quot W nsites))))]
    (println "gen\tmean-gamma\tmean-score\tmean-reach\tbest-score\tbest-gamma\tmean-update\tbest-update")
    (loop [gen 0
           population (vec (repeatedly P #(hash-map
                                            :gamma (or pinned (nth GAMMA-LEVELS (.nextInt rng 8)))
                                            ;; Start at 1.0 -- where the river dynamics actually work, as the pre-G5 runs
   ;; implicitly did. Randomising this crippled most lineages from generation 0,
   ;; band-score was then 0 for the whole population, and the only ranking signal
   ;; left was -c*gamma, so selection just minimised gamma and never found reach.
   ;; Mutation still walks update-prob down to 0, so degeneration stays reachable.
   :update-prob 1.0
                                            :field (c/java-random-genotype rng W))))]
      (when (< gen G)
        ;; Fitness evaluation is embarrassingly parallel across the population:
        ;; each genome's reach builds its own seeded RNGs and shares no state, so
        ;; pmap changes wall-clock only, never the numbers. This is what lets one
        ;; run use a many-core box rather than one core.
        ;; Baldwin's first phase is plasticity ESTABLISHING; charging for it from
        ;; generation 0 forecloses that by construction. Cost applies after warm-up.
        (let [c-now (if (< gen warm) 0.0 cost)
              scored (vec (pmap #(merge % (fitness % c-now seed-set site-set)) population))
              ranked (vec (sort-by :score > scored))
              n (count ranked)
              survivors (vec (take (max 1 (quot n 2)) ranked))
              offspring (vec (repeatedly (- n (count survivors))
                                         #(mutate rng (nth survivors (.nextInt rng (count survivors)))
                                                  frate (some? pinned))))
              mg (/ (reduce + (map :gamma scored)) (double n))
              mu (/ (reduce + (map #(or (:update-prob %) 1.0) scored)) (double n))
              ms (/ (reduce + (map :score scored)) (double n))
              mr (/ (reduce + (map :reach scored)) (double n))
              best (first ranked)]
          (println (format "%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f"
                           gen mg ms mr (:score best) (:gamma best)
                           mu (or (:update-prob best) 1.0)))
          (flush)
          (recur (inc gen) (into (mapv #(select-keys % [:gamma :update-prob :field]) survivors) offspring)))))))

(apply -main *command-line-args*)
