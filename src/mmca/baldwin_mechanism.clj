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
