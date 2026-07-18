(ns mmca.experiments.local-causal-states
  "Excursion E5: held-out joint local-causal-state reconstruction.

  A local past cone ends at time t and contains radius (lag + 1) at each
  preceding row.  Its depth is selected by seed-held-out likelihood.  Separate
  genotype, phenotype, and joint predictors estimate the factorized nine-bit
  future distribution (the centre cell's eight G bits and one X bit at t+1).

  Predictive distributions are quantized at a candidate tolerance; each bin is
  an empirical local causal state.  The tolerance, like depth, is selected by
  held-out likelihood, never by the resulting picture.  After selection we call
  states outside the fixed 80% background mass `coherent candidates` and join
  their spacetime points with an 8-neighbour graph.  Components smaller than
  three points are discarded.  Counts, lifetimes, and centroid velocities are
  descriptive diagnostics, not an assertion that every candidate is a particle."
  (:require [mmca.core :as c]))

(def default-config
  {:writing [4 5 6 7 0 1 2 3]
   :seeds (vec (range 6))
   :width 40
   :steps 56
   :burn-in 12
   :folds 3
   :depths [1 2]
   :tolerances [0.1 0.2 0.4]
   :alpha 0.5
   :background-mass 0.8
   :minimum-structure-size 3})

(def layer-order [:phenotype :genotype :joint])

(defn- bit-at [s i]
  (Character/digit (nth s i) 2))

(defn- cell-features [run t i layer]
  (case layer
    :phenotype [(bit-at (nth (:phe run) t) i)]
    :genotype (c/rule-bits (nth (nth (:gen run) t) i))
    :joint (conj (c/rule-bits (nth (nth (:gen run) t) i))
                 (bit-at (nth (:phe run) t) i))))

(defn- past-cone [run t i depth layer]
  (vec
   (mapcat (fn [lag]
             (let [radius (inc lag)
                   row (- t lag)]
               (mapcat #(cell-features run row % layer)
                       (range (- i radius) (inc (+ i radius))))))
           (range depth))))

(defn- future-cone [run t i]
  (conj (c/rule-bits (nth (nth (:gen run) (inc t)) i))
        (bit-at (nth (:phe run) (inc t)) i)))

(defn- engine-run [engine writing seed width steps]
  (case engine
    :base (c/run-propagator writing seed width steps)
    :river (c/run-river seed width steps)))

(defn- samples
  [engine layer depth {:keys [writing seeds width steps burn-in]}]
  (let [margin depth]
    (vec
     (for [seed seeds
           :let [run (engine-run engine writing seed width steps)]
           t (range (max burn-in (dec depth)) steps)
           i (range margin (- width margin))]
       {:seed seed
        :t t
        :i i
        :past (past-cone run t i depth layer)
        :future (future-cone run t i)}))))

(defn- fit-naive-bayes
  "Fit independent Bernoulli targets with Bernoulli feature likelihoods."
  [rows]
  (let [target-count (count (:future (first rows)))
        feature-count (count (:past (first rows)))]
    (reduce
     (fn [model {:keys [past future]}]
       (reduce
        (fn [m output]
          (let [y (nth future output)]
            (-> (reduce (fn [m2 feature]
                          (update-in m2 [:feature output feature y
                                         (nth past feature)] (fnil inc 0)))
                        m (range feature-count))
                (update-in [:target output y] (fnil inc 0)))))
        model (range target-count)))
     {:n (count rows)
      :target-count target-count
      :feature-count feature-count
      :target {}
      :feature {}}
     rows)))

(defn- clamp [x]
  (max 1.0e-9 (min (- 1.0 1.0e-9) x)))

(defn- probability
  [{:keys [n feature-count target feature]} past output alpha]
  (let [ones (get-in target [output 1] 0)
        zeros (- n ones)
        prior1 (/ (+ ones alpha) (+ n (* 2.0 alpha)))
        prior0 (- 1.0 prior1)
        scores
        (mapv
         (fn [y prior ny]
           (+ (Math/log prior)
              (reduce
               (fn [score j]
                 (let [value (nth past j)
                       hits (get-in feature [output j y value] 0)
                       p (/ (+ hits alpha) (+ ny (* 2.0 alpha)))]
                   (+ score (Math/log p))))
               0.0 (range feature-count))))
         [0 1] [prior0 prior1] [zeros ones])
        peak (apply max scores)
        weights (mapv #(Math/exp (- % peak)) scores)]
    (clamp (/ (nth weights 1) (reduce + weights)))))

(defn- predict [model past alpha]
  (mapv #(probability model past % alpha)
        (range (:target-count model))))

(defn- signature [probabilities tolerance]
  (mapv #(long (Math/floor (/ % tolerance))) probabilities))

(defn- fit-states [model rows tolerance alpha]
  (reduce
   (fn [states {:keys [past future]}]
     (let [state (signature (predict model past alpha) tolerance)]
       (reduce (fn [s output]
                 (-> s
                     (update-in [state :n] (fnil inc 0))
                     (update-in [state :ones output] (fnil + 0)
                                (nth future output))))
               states (range (count future)))))
   {} rows))

(defn- state-probabilities [entry target-count alpha]
  (mapv (fn [output]
          (/ (+ (get-in entry [:ones output] 0) alpha)
             (+ (:n entry) (* 2.0 alpha))))
        (range target-count)))

(defn- row-loss [probabilities future]
  (/ (reduce +
             (map (fn [p y]
                    (- (/ (Math/log (if (= y 1) p (- 1.0 p)))
                          (Math/log 2.0))))
                  probabilities future))
     (double (count future))))

(defn- score-fold
  [train test tolerance alpha]
  (let [model (fit-naive-bayes train)
        states (fit-states model train tolerance alpha)
        target-count (:target-count model)]
    {:n (count test)
     :loss
     (/ (reduce
         (fn [loss {:keys [past future]}]
           (let [raw (predict model past alpha)
                 state (get states (signature raw tolerance))
                 probabilities (if state
                                 (state-probabilities state target-count alpha)
                                 raw)]
             (+ loss (row-loss probabilities future))))
         0.0 test)
        (double (count test)))}))

(defn- cross-validated-score
  [rows folds tolerance alpha]
  (let [scores
        (for [fold (range folds)
              :let [test? #(= fold (mod (:seed %) folds))
                    train (vec (remove test? rows))
                    test (vec (filter test? rows))]]
          (score-fold train test tolerance alpha))
        n (reduce + (map :n scores))]
    {:loss (/ (reduce + (map #(* (:n %) (:loss %)) scores)) (double n))
     :held-out-n n}))

(defn- candidate-score
  [rows depth tolerance config]
  (let [score (cross-validated-score rows (:folds config) tolerance
                                     (:alpha config))]
    (assoc score :depth depth :tolerance tolerance)))

(defn- select-model [engine layer config]
  (let [candidates
        (vec
         (mapcat (fn [depth]
                   (let [rows (samples engine layer depth config)]
                     (mapv #(candidate-score rows depth % config)
                           (:tolerances config))))
                 (:depths config)))]
    {:selected (first (sort-by (juxt :loss :depth :tolerance) candidates))
     :candidates candidates}))

(defn- background-states [classified background-mass]
  (let [frequencies (frequencies (map :state classified))
        target (* background-mass (count classified))]
    (loop [ordered (sort-by (fn [[state n]] [(- n) state]) frequencies)
           total 0
           result #{}]
      (if (or (>= total target) (empty? ordered))
        result
        (let [[state n] (first ordered)]
          (recur (rest ordered) (+ total n) (conj result state)))))))

(defn- neighbours [[seed t i]]
  (for [dt [-1 0 1]
        di [-1 0 1]
        :when (not (and (zero? dt) (zero? di)))]
    [seed (+ t dt) (+ i di)]))

(defn- component [start remaining]
  (loop [frontier [start] seen #{start}]
    (if (empty? frontier)
      seen
      (let [point (peek frontier)
            additions (remove seen (filter remaining (neighbours point)))]
        (recur (into (pop frontier) additions) (into seen additions))))))

(defn- components [points]
  (loop [remaining (set points) result []]
    (if (empty? remaining)
      result
      (let [part (component (first remaining) remaining)]
        (recur (reduce disj remaining part) (conj result part))))))

(defn- component-summary [points]
  (let [by-time (group-by second points)
        times (sort (keys by-time))
        centroid (fn [t]
                   (/ (reduce + (map #(nth % 2) (get by-time t)))
                      (double (count (get by-time t)))))
        lifetime (inc (- (last times) (first times)))
        velocity (if (= 1 lifetime) 0.0
                     (/ (- (centroid (last times)) (centroid (first times)))
                        (double (dec lifetime))))]
    {:size (count points) :lifetime lifetime :velocity velocity}))

(defn- mean [xs]
  (if (seq xs) (/ (reduce + xs) (double (count xs))) 0.0))

(defn- reconstruction
  [engine layer {:keys [depth tolerance]} config]
  (let [rows (samples engine layer depth config)
        model (fit-naive-bayes rows)
        states (fit-states model rows tolerance (:alpha config))
        classified (mapv (fn [row]
                           (assoc row :state
                                  (signature (predict model (:past row)
                                                      (:alpha config))
                                             tolerance)))
                         rows)
        background (background-states classified (:background-mass config))
        candidate-points (map (juxt :seed :t :i)
                              (remove #(contains? background (:state %)) classified))
        structures (->> (components candidate-points)
                        (filter #(>= (count %) (:minimum-structure-size config)))
                        (map component-summary)
                        vec)
        lifetimes (map :lifetime structures)
        velocities (map :velocity structures)]
    {:state-count (count states)
     :background-state-count (count background)
     :candidate-point-count (count candidate-points)
     :structure-count (count structures)
     :mean-lifetime (mean lifetimes)
     :maximum-lifetime (if (seq lifetimes) (apply max lifetimes) 0)
     :mean-absolute-velocity (mean (map #(Math/abs %) velocities))
     :mean-signed-velocity (mean velocities)}))

(defn experiment
  "Run E5 and return deterministic, printable data."
  ([] (experiment default-config))
  ([config]
   (let [selection
         (into {}
               (for [engine [:base :river]]
                 [engine
                  (into {}
                        (for [layer layer-order]
                          [layer (select-model engine layer config)]))]))
         result
         (into {}
               (for [engine [:base :river]]
                 [engine
                  (into {}
                        (for [layer layer-order
                              :let [selected (get-in selection
                                                     [engine layer :selected])]]
                          [layer
                           (merge selected
                                  (reconstruction engine layer selected config))]))]))]
     {:config (select-keys config [:writing :seeds :width :steps :burn-in
                                   :folds :depths :tolerances :alpha
                                   :background-mass :minimum-structure-size])
      :models result
      :selection selection})))

(defn- model-line [engine layer result]
  (let [m (get-in result [:models engine layer])]
    (format "%-5s %-9s d=%d tau=%.2f loss=%.6f states=%4d structures=%4d lifetime(mean/max)=%.2f/%d |v|=%.3f v=%+.3f"
            (name engine) (name layer) (:depth m) (:tolerance m) (:loss m)
            (:state-count m) (:structure-count m) (:mean-lifetime m)
            (:maximum-lifetime m) (:mean-absolute-velocity m)
            (:mean-signed-velocity m))))

(defn -main [& _]
  (let [result (experiment)
        cfg (:config result)]
    (println (format "E5 joint local causal states | seeds %d-%d | W%d T%d burn%d | %d-fold"
                     (first (:seeds cfg)) (last (:seeds cfg)) (:width cfg)
                     (:steps cfg) (:burn-in cfg) (:folds cfg)))
    (println "future loss: mean held-out bits per predicted G/X bit")
    (println "state/structure diagnostics use held-out-selected depth and tolerance")
    (println)
    (doseq [engine [:base :river]
            layer layer-order]
      (println (model-line engine layer result)))
    (println)
    (doseq [engine [:base :river]
            :let [joint (get-in result [:models engine :joint :loss])
                  g (get-in result [:models engine :genotype :loss])
                  x (get-in result [:models engine :phenotype :loss])]]
      (println (format "%s joint gain vs best marginal: %+.6f bits/bit"
                       (name engine) (- (min g x) joint))))))
