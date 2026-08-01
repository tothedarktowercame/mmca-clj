(ns mmca.baldwin-mechanism
  "Mechanism diagnostics which precede another Baldwin selection run.

   These functions ask whether lifetime rewriting leaves a reproducible field at
   t*.  Environment and rewrite randomness are separate arguments so fixed-p0
   and shared-rewrite-tape treatments can be crossed without changing either
   treatment's random-draw schedule."
  (:require [mmca.baldwin-selection :as selection]
            [mmca.core :as c]))

(defn learned-field-at-tstar
  "Return the genotype field after the learning interval.

   When environment-seed equals rewrite-seed and fixed-p0 is nil, this exactly
   reproduces the first stage of `selection/two-stage`.  The rewrite RNG still
   consumes the historical genotype and phenotype draws even when p0 comes from
   a different environment seed; treatment changes select values only after all
   scheduled draws have been consumed."
  [{:keys [gamma update-prob field mask hold]} environment-seed rewrite-seed
   fixed-p0]
  (let [random (java.util.Random. (long rewrite-seed))
        gate (java.util.Random. (long (+ 987654321 rewrite-seed)))
        upd (java.util.Random. (long (+ 123456789 rewrite-seed)))
        _ (c/java-random-genotype random selection/W)
        discarded-p0 (c/java-random-phenotype random selection/W)
        sampled-p0 (selection/sampled-initial-phenotype environment-seed)
        p0 (or fixed-p0 sampled-p0)
        genome {:gamma (double (or gamma 1.0))
                :update-prob (double (or update-prob 1.0))
                :field (vec field)
                :mask (vec (or mask (repeat selection/W true)))
                :hold (vec (or hold (repeat selection/W false)))}
        result (selection/run-from random gate upd (:field genome) p0
                                   selection/TSTAR (:gamma genome)
                                   (:update-prob genome) (:mask genome)
                                   (:hold genome) p0)]
    ;; Force the discarded value so this invariant remains visible to readers
    ;; and instrumentation even though Clojure would otherwise permit elision.
    (when-not (= selection/W (count discarded-p0))
      (throw (ex-info "historical rewrite-tape prefix was not consumed" {})))
    (:gen result)))

(defn field-agreement [a b]
  (when-not (= (count a) (count b))
    (throw (ex-info "field widths differ" {:left (count a) :right (count b)})))
  (/ (count (filter true? (map = a b))) (double (count a))))

(defn pairwise-agreements [fields]
  (vec
   (for [i (range (count fields))
         j (range (inc i) (count fields))]
     {:left i :right j :agreement (field-agreement (nth fields i) (nth fields j))})))

(defn mean [xs]
  (when (empty? xs) (throw (ex-info "mean requires data" {})))
  (/ (reduce + (map double xs)) (double (count xs))))

(defn binomial
  "Exact binomial coefficient in arbitrary-precision integer arithmetic."
  [n k]
  (when-not (and (integer? n) (integer? k) (<= 0 k n))
    (throw (ex-info "invalid binomial arguments" {:n n :k k})))
  (let [k (min k (- n k))]
    (loop [i 1 acc 1N]
      (if (> i k)
        acc
        (recur (inc i) (quot (*' acc (+ (- n k) i)) i))))))

(defn binomial-tail [n k]
  (reduce +' 0N (map #(binomial n %) (range k (inc n)))))

(defn pow2 [n]
  (reduce *' 1N (repeat n 2N)))

(defn familywise-significant-direction?
  "Two-sided exact sign test with Bonferroni familywise alpha 0.05.

   Ties are excluded. `family-size` is fixed by the preregistration rather than
   reduced when some probes are unavailable, so missing tests cannot relax the
   gate. The integer inequality is
   2 * tail / 2^n <= 0.05 / family-size."
  [positive negative family-size]
  (let [n (+ positive negative)
        k (max positive negative)]
    (and (pos? n)
         (pos? family-size)
         (<= (*' 40N family-size (binomial-tail n k)) (pow2 n)))))

(defn paired-sign-summary [row family-size]
  (let [positive (:positive row)
        negative (:negative row)]
    {:non-tied (+ positive negative)
     :dominant-sign (cond (> positive negative) :positive
                          (< positive negative) :negative
                          :else :tie)
     :familywise-significant?
     (familywise-significant-direction? positive negative family-size)}))

(defn stationarity-cell
  [genome environment-seeds rewrite-seeds fixed-p0]
  (when-not (= (count environment-seeds) (count rewrite-seeds))
    (throw (ex-info "paired seed schedules differ in length"
                    {:environment environment-seeds :rewrite rewrite-seeds})))
  (let [fields (mapv #(learned-field-at-tstar genome %1 %2 fixed-p0)
                     environment-seeds rewrite-seeds)
        pairs (pairwise-agreements fields)
        inherited (mapv #(field-agreement (:field genome) %) fields)]
    {:environment-seeds (vec environment-seeds)
     :rewrite-seeds (vec rewrite-seeds)
     :fixed-p0? (some? fixed-p0)
     :fields fields
     :pairwise pairs
     :mean-pairwise-agreement (mean (map :agreement pairs))
     :mean-inherited-agreement (mean inherited)}))

(defn stationarity-grid
  "Cross p0 stationarity with rewrite-tape stationarity.

   The fully fixed cell is an apparatus control and must have exact agreement
   1.0.  The other three cells separately expose total variation, p0 variation,
   and rewrite-tape variation."
  [genome environment-seeds common-rewrite-seed fixed-p0]
  (let [variable-rewrite (vec environment-seeds)
        shared-rewrite (vec (repeat (count environment-seeds) common-rewrite-seed))
        grid {:variable-p0/variable-rewrite
              (stationarity-cell genome environment-seeds variable-rewrite nil)
              :fixed-p0/variable-rewrite
              (stationarity-cell genome environment-seeds variable-rewrite fixed-p0)
              :variable-p0/shared-rewrite
              (stationarity-cell genome environment-seeds shared-rewrite nil)
              :fixed-p0/shared-rewrite
              (stationarity-cell genome environment-seeds shared-rewrite fixed-p0)}]
    (assoc grid :apparatus-valid?
           (= 1.0 (get-in grid
                          [:fixed-p0/shared-rewrite :mean-pairwise-agreement])))))

(defn paired-reach
  "Preserve the experimental unit instead of collapsing directly to an arm mean."
  [genome seeds sites options]
  (mapv (fn [[seed site]]
          {:seed seed
           :site site
           :reach (:mean (selection/reach genome [seed] [site] options))})
        (for [seed seeds site sites] [seed site])))

(defn replace-unheld-allele [genome locus rule]
  (when-not (< -1 locus (count (:field genome)))
    (throw (ex-info "locus outside field" {:locus locus})))
  (when (get (:hold genome) locus)
    (throw (ex-info "allele-sensitivity probe requires an unheld locus"
                    {:locus locus})))
  (assoc genome :field (assoc (:field genome) locus rule)))

(defn allele-sensitivity
  "Paired selection coefficient of one inherited allele under active rewriting."
  [genome locus rule baseline seeds sites options]
  (let [candidate-genome (replace-unheld-allele genome locus rule)
        candidate (paired-reach candidate-genome seeds sites options)
        deltas (mapv (fn [a b]
                       (when-not (= (select-keys a [:seed :site])
                                    (select-keys b [:seed :site]))
                         (throw (ex-info "paired evaluation order diverged"
                                         {:baseline a :candidate b})))
                       (- (:reach b) (:reach a)))
                     baseline candidate)]
    {:locus locus
     :rule rule
     :current-rule (get (:field genome) locus)
     :n (count deltas)
     :mean-delta (mean deltas)
     :positive (count (filter pos? deltas))
     :negative (count (filter neg? deltas))
     :ties (count (filter zero? deltas))
     :deltas deltas}))

(defn context-key [bits]
  (reduce (fn [n bit] (+ (* 2 n) bit)) 0 bits))

(defn context-step
  "One gamma=1/update=1 river step with context-indexed observation.

   Draw order and resulting genotype are tested against
   `selection/gain-genotype-step`; this is instrumentation, not a new dynamic."
  [^java.util.Random random ^java.util.Random gate ^java.util.Random upd
   genotype phenotype next-phenotype frozen tally]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       (let [_ (.nextDouble upd)
             _ (.nextDouble gate)
             predecessor (if (zero? i) c/default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth phenotype (dec i)) 2)
                        (Character/digit (nth phenotype i) 2)
                        (Character/digit (nth phenotype (inc i)) 2)
                        (Character/digit (nth next-phenotype i) 2)])
             frozen-context (when context
                              [(Character/digit (nth frozen (dec i)) 2)
                               (Character/digit (nth frozen i) 2)
                               (Character/digit (nth frozen (inc i)) 2)
                               (Character/digit (nth frozen i) 2)])
             live-rule (c/original-river-combine-rule
                        predecessor centre successor context)
             frozen-rule (c/original-river-combine-rule
                          predecessor centre successor frozen-context)
             source (.nextInt random c/bit-count)]
         (when context
           (let [k (context-key context)]
             (vswap! tally
                     (fn [m]
                       (-> m
                           (update-in [k :count] (fnil inc 0))
                           (update-in [k :equal]
                                      (fnil + 0) (if (= live-rule frozen-rule) 1 0))
                           (update-in [k :rules live-rule] (fnil inc 0)))))))
         (c/propagate-at live-rule c/river-writing source)))
     (range width))))

(defn initial-state
  [environment-seed rewrite-seed fixed-p0]
  (let [random (java.util.Random. (long rewrite-seed))
        gate (java.util.Random. (long (+ 987654321 rewrite-seed)))
        upd (java.util.Random. (long (+ 123456789 rewrite-seed)))
        _ (c/java-random-genotype random selection/W)
        _ (c/java-random-phenotype random selection/W)]
    {:random random :gate gate :upd upd
     :phenotype (or fixed-p0
                    (selection/sampled-initial-phenotype environment-seed))}))

(defn context-profile
  "Profile the learned rule selected within each of the 16 live contexts."
  [genome environment-seed rewrite-seed fixed-p0]
  (let [state (initial-state environment-seed rewrite-seed fixed-p0)
        reference (selection/run-from
                   (:random state) (:gate state) (:upd state) (:field genome)
                   (:phenotype state) selection/TSTAR 1.0 1.0
                   (vec (repeat selection/W true))
                   (vec (repeat selection/W false)) (:phenotype state))
        frozen (peek (:phe reference))
        state (initial-state environment-seed rewrite-seed fixed-p0)
        tally (volatile! {})]
    (loop [t 0 g (:field genome) p (:phenotype state)]
      (if (= t selection/TSTAR)
        @tally
        (let [np (c/phenotype-step g p)
              ng (context-step (:random state) (:gate state) (:upd state)
                               g p np frozen tally)]
          (recur (inc t) ng np))))))

(defn summarize-context [context observations]
  (let [n (:count observations)
        [modal-rule modal-count] (apply max-key val (:rules observations))]
    {:context context
     :count n
     :live-frozen-agreement (/ (:equal observations) (double n))
     :modal-rule modal-rule
     :modal-share (/ modal-count (double n))
     :distinct-rules (count (:rules observations))}))
