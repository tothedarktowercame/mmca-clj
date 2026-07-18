#_{:clj-kondo/ignore [:namespace-name-mismatch]}
(ns mmca.experiments.directed-predictive-info
  "Excursion E4: directed, cross-validated predictive information.

  We compare held-out categorical log loss for two local predictors:

    X+ : X- versus (X-, G-)
    G+ : G- versus (G-, X-)

  The difference (baseline loss minus augmented loss) is reported in bits per
  site-step. G+ is factorized into its eight rule bits. Its G-only lightcone is
  a compact sufficient statistic for the feedforward genotype transition: the
  destination bit and the unique propagated source bit of the local blended
  rule. Consequently X- cannot improve the feedforward predictor except for
  finite-sample estimation noise. The river deliberately violates that null by
  reading phenotype context during genotype construction.

  Folds hold out entire simulation seeds. All shuffles use mmca.rng."
  (:require [mmca.core :as c]
            [mmca.rng :as rng]))

(def default-config
  {:writing [4 5 6 7 0 1 2 3]
   :seeds (vec (range 8))
   :width 48
   :steps 64
   :burn-in 16
   :folds 4
   :surrogate-seed "e4-surrogate-1729"
   :alpha 0.5})

(defn- bit-at [s i]
  (Character/digit (nth s i) 2))

(defn- inverse-writing [writing]
  (reduce-kv (fn [inverse source destination]
               (assoc inverse destination source))
             (vec (repeat c/bit-count nil))
             writing))

(defn- g-only-lightcones [writing predecessor centre successor]
  (let [proxy-bits (c/rule-bits (c/blend-rule predecessor centre successor))
        inverse (inverse-writing writing)]
    (mapv (fn [destination]
            (let [source (nth inverse destination)]
              [destination (nth proxy-bits destination) (nth proxy-bits source)]))
          (range c/bit-count))))

(defn- run-samples
  [engine {:keys [writing seeds width steps burn-in]}]
  (let [neighbourhood-writing
        (if (contains? #{:river :river-ablated} engine)
          c/river-writing
          (c/positional-writing->neighbourhood-writing writing))]
    (vec
     (for [seed seeds
           :let [run (case engine
                       :base (c/run-propagator writing seed width steps)
                       :river (c/run-river seed width steps)
                       :river-ablated (c/run-river-ablated seed width steps))]
           t (range burn-in steps)
           i (range 1 (dec width))
           :let [g (nth (:gen run) t)
                 g+ (nth (:gen run) (inc t))
                 x (nth (:phe run) t)
                 x+ (nth (:phe run) (inc t))
                 predecessor (nth g (dec i))
                 centre (nth g i)
                 successor (nth g (inc i))]]
       {:seed seed
        :t t
        :i i
        :x-past [(bit-at x (dec i)) (bit-at x i) (bit-at x (inc i))]
        :x-next (bit-at x+ i)
        :g-source centre
        :g-only (g-only-lightcones neighbourhood-writing
                                    predecessor centre successor)
        :g-next-bits (c/rule-bits (nth g+ i))}))))

(defn- fixed-rule-samples
  "Pool the balanced Rule 105/204 control pair under fixed genotypes."
  [{:keys [seeds width steps burn-in]}]
  (vec
   (for [rule [105 204]
         seed seeds
         :let [r (rng/make-rng (format "e4-control-%d-%d" rule seed))
               x0 (c/random-phenotype r width)
               genotype (vec (repeat width rule))]
         [t x x+] (loop [t 0 x x0 rows []]
                    (if (= t steps)
                      rows
                      (let [x+ (c/phenotype-step genotype x)]
                        (recur (inc t) x+ (conj rows [t x x+])))))
         :when (>= t burn-in)
         i (range 1 (dec width))]
     {:seed seed
      :t t
      :i i
      :x-past [(bit-at x (dec i)) (bit-at x i) (bit-at x (inc i))]
      :x-next (bit-at x+ i)
      :g-source rule})))

(defn- fit-categorical [rows key-fn target-fn]
  (reduce (fn [model row]
            (let [k (key-fn row)
                  y (target-fn row)]
              (-> model
                  (update-in [k :total] (fnil inc 0))
                  (update-in [k :labels y] (fnil inc 0)))))
          {}
          rows))

(defn- log-loss
  [model rows key-fn target-fn classes alpha]
  (if (empty? rows)
    0.0
    (/ (reduce
        (fn [loss row]
          (let [entry (get model (key-fn row))
                n (long (or (:total entry) 0))
                hits (long (or (get-in entry [:labels (target-fn row)]) 0))
                probability (/ (+ hits alpha) (+ n (* alpha classes)))]
            (- loss (/ (Math/log probability) (Math/log 2.0)))))
        0.0
        rows)
       (double (count rows)))))

(defn- held-out-improvement
  [rows folds alpha key-alone key-joint target]
  (let [scores
        (for [fold (range folds)
              :let [test? #(= fold (mod (:seed %) folds))
                    train (remove test? rows)
                    test (filter test? rows)
                    alone-model (fit-categorical train key-alone target)
                    joint-model (fit-categorical train key-joint target)]]
          {:n (count test)
           :alone (log-loss alone-model test key-alone target 2 alpha)
           :joint (log-loss joint-model test key-joint target 2 alpha)})
        n (reduce + (map :n scores))
        weighted (fn [k]
                   (/ (reduce + (map #(* (:n %) (k %)) scores)) (double n)))
        alone (weighted :alone)
        joint (weighted :joint)]
    {:baseline-loss alone
     :joint-loss joint
     :improvement (- alone joint)
     :held-out-n n}))

(defn- x-direction [samples folds alpha]
  (held-out-improvement samples folds alpha
                        :x-past
                        (juxt :x-past :g-source)
                        :x-next))

(defn- g-bit-rows [samples]
  (vec
   (mapcat (fn [row]
             (map-indexed
              (fn [bit target]
                (assoc row
                       :g-key (nth (:g-only row) bit)
                       :g-target target))
              (:g-next-bits row)))
           samples)))

(defn- g-direction [samples folds alpha]
  (let [score (held-out-improvement (g-bit-rows samples) folds alpha
                                    :g-key
                                    (juxt :g-key :x-past)
                                    :g-target)]
    (-> score
        (update :baseline-loss #(* c/bit-count %))
        (update :joint-loss #(* c/bit-count %))
        (update :improvement #(* c/bit-count %))
        (update :held-out-n quot c/bit-count))))

(defn- shuffled-g-source [samples seed]
  (let [r (rng/make-rng seed)
        shuffled
        (loop [values (vec (map :g-source samples))
               i (dec (count samples))]
          (if (<= i 0)
            values
            (let [j (rng/rand-int r (inc i))]
              (recur (assoc values i (nth values j) j (nth values i))
                     (dec i)))))]
    (mapv #(assoc %1 :g-source %2) samples shuffled)))

(defn- spacetime-shuffled-g-source [samples width burn-in steps]
  (let [by-coordinate (into {} (map (juxt (juxt :seed :t :i) identity) samples))
        time-span (- steps burn-in)
        interior-width (- width 2)
        time-shift (max 1 (quot time-span 3))
        space-shift (max 1 (quot interior-width 3))]
    (mapv
     (fn [row]
       (let [donor-t (+ burn-in (mod (+ (- (:t row) burn-in) time-shift)
                                      time-span))
             donor-i (+ 1 (mod (+ (dec (:i row)) space-shift) interior-width))
             donor (get by-coordinate [(:seed row) donor-t donor-i])]
         (assoc row :g-source (:g-source donor))))
     samples)))

(defn experiment
  "Run E4 and return deterministic data suitable for equality testing."
  ([] (experiment default-config))
  ([{:keys [folds surrogate-seed width burn-in steps alpha] :as config}]
   (let [base (run-samples :base config)
         river (run-samples :river config)
         ablated (run-samples :river-ablated config)
         controls (fixed-rule-samples config)
         base-g-to-x (x-direction base folds alpha)
         base-x-to-g (g-direction base folds alpha)
         river-g-to-x (x-direction river folds alpha)
         river-x-to-g (g-direction river folds alpha)
         ablated-x-to-g (g-direction ablated folds alpha)]
     {:config (select-keys config [:writing :seeds :width :steps :burn-in
                                   :folds :surrogate-seed :alpha])
      :base {:g-to-x base-g-to-x
             :x-to-g base-x-to-g}
      :river {:g-to-x river-g-to-x
              :x-to-g river-x-to-g}
      ;; MATCHED feedback-off control: run-river-ablated shares the river's exact
      ;; Java seed/tape/construction/fallback, cutting only the X->G edge (frozen
      ;; phenotype). river X->G minus this is the isolated causal feedback --
      ;; replacing the old base-engine control, which differed in RNG + construction.
      :river-ablated {:x-to-g ablated-x-to-g}
      :surrogates
      {:rule-label-shuffle
       {:g-to-x (x-direction (shuffled-g-source river surrogate-seed) folds alpha)}
       :spatial-temporal-genotype-shuffle
       {:g-to-x (x-direction
                 (spacetime-shuffled-g-source river width burn-in steps)
                 folds alpha)}
       :feedback-breaking
       {:x-to-g ablated-x-to-g}}
      :live-dead-control
      {:rules [105 204]
       :g-to-x (x-direction controls folds alpha)}})))

(defn- metric-line [label score]
  (format "%-43s % .6f  (L0 %.6f, L1 %.6f, n=%d)"
          label (:improvement score) (:baseline-loss score)
          (:joint-loss score) (:held-out-n score)))

(defn -main [& _]
  (let [result (experiment)
        cfg (:config result)]
    (println (format "E4 directed predictive information | seeds %d-%d | W%d T%d burn%d | %d-fold"
                     (first (:seeds cfg)) (last (:seeds cfg)) (:width cfg)
                     (:steps cfg) (:burn-in cfg) (:folds cfg)))
    (println (str "surrogate seed: " (:surrogate-seed cfg)))
    (println "bits per site-step; positive = held-out log-loss improvement")
    (println)
    (println (metric-line "base I(G-;X+|X-)" (get-in result [:base :g-to-x])))
    (println (metric-line "base I(X-;G+|G-) CHECK ~0" (get-in result [:base :x-to-g])))
    (println (metric-line "river I(G-;X+|X-)" (get-in result [:river :g-to-x])))
    (println (metric-line "river I(X-;G+|G-)" (get-in result [:river :x-to-g])))
    (println (metric-line "river rule-label shuffle G->X"
                          (get-in result [:surrogates :rule-label-shuffle :g-to-x])))
    (println (metric-line "river spacetime G shuffle G->X"
                          (get-in result [:surrogates
                                          :spatial-temporal-genotype-shuffle
                                          :g-to-x])))
    (println (metric-line "river-ablated MATCHED feedback-off X->G"
                          (get-in result [:river-ablated :x-to-g])))
    (println (metric-line "Rule 105/204 pooled control G->X"
                          (get-in result [:live-dead-control :g-to-x])))
    (let [rv (get-in result [:river :x-to-g :improvement])
          ab (get-in result [:river-ablated :x-to-g :improvement])]
      (println (format "%-43s % .6f"
                       "ISOLATED X->G feedback (river - matched ablation)"
                       (- rv ab))))
    (let [null-value (get-in result [:base :x-to-g :improvement])]
      (println)
      (println (format "CHECK feedforward X->G |value| < 0.05: %s (%.6f)"
                       (if (< (Math/abs null-value) 0.05) "PASS" "FAIL")
                       null-value)))))
