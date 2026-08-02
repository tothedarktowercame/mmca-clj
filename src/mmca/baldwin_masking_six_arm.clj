(ns mmca.baldwin-masking-six-arm
  "Amended six-arm masking intervention with explicit rewrite-tape identity.

   The first five arms use the discovery rewrite tapes. The sixth repeats the
   held-good intervention on disjoint novel tapes. Environment seeds remain
   paired across arms, and locus is the inferential unit."
  (:require [clojure.set :as set]
            [mmca.baldwin-masking-intervention :as masking]
            [mmca.baldwin-selection :as selection]))

(def lean-revision "1445d9bd4de70b532cccbd06927285588f96fc1d")
(def pilot-environment-seeds [901 902 903])
(def confirmation-environment-seeds [101 102 103 104 105 106 107 108])
(def discovery-rewrite-tapes [1 2 3])
(def novel-rewrite-tapes [1001 1002 1003])
(def arms [:plastic-current :held-current :plastic-good :held-good :held-bad
           :held-good-novel-tape])
(def family-size 5)
(def readout-fields [:fitness :band :reach])
(def behavioral-field :band)
(def constant-offset-win-fraction-threshold 0.20)
(def score-additive-terms
  {:behavior {:field :band :coefficient 1.0}
   :capacity-cost {:field :dependence
                   :coefficient (- masking/capacity-cost)}})

(defn tapes-for-arm [arm]
  (if (= :held-good-novel-tape arm)
    novel-rewrite-tapes
    discovery-rewrite-tapes))

(defn intervention-arm [arm]
  (if (= :held-good-novel-tape arm) :held-good arm))

(defn intervene [genome entry arm]
  (masking/intervene genome entry (intervention-arm arm)))

(defn reach-by-site [genome environment-seed rewrite-tape sites]
  (let [{:keys [gamma update-prob field mask hold]} genome
        up (if (nil? update-prob) 1.0 update-prob)
        mk (or mask (vec (repeat selection/W true)))
        hd (or hold (vec (repeat selection/W false)))
        ref (selection/two-stage-separated 1.0 up mk hd environment-seed
                                           rewrite-tape field nil nil)
        frozen* (nth (:phe ref) selection/TSTAR)
        a (selection/two-stage-separated gamma up mk hd environment-seed
                                         rewrite-tape field nil frozen*)]
    (into {}
          (for [site sites
                :let [b (selection/two-stage-separated
                         gamma up mk hd environment-seed rewrite-tape field
                         (selection/flip-at site) frozen*)]]
            [site
             (double
              (reduce + (map #(if (= %1 %2) 0 1)
                             (nth (:phe a) (+ selection/TSTAR selection/DT))
                             (nth (:phe b) (+ selection/TSTAR selection/DT)))))]))))

(defn unit-rows [base entry arm environment-seed tape-slot sites]
  (let [rewrite-tape (nth (tapes-for-arm arm) tape-slot)
        genome (intervene base entry arm)
        reaches (reach-by-site genome environment-seed rewrite-tape sites)
        dependence (selection/plastic-dependence genome)]
    (mapv
     (fn [site]
       (let [reach (reaches site)]
         (merge entry
                {:arm arm
                 :environment-seed environment-seed
                 :rewrite-tape rewrite-tape
                 :tape-slot tape-slot
                 :site site
                 :reach reach
                 :band (selection/band-score reach)
                 :dependence dependence
                 :fitness (- (selection/band-score reach)
                             (* masking/capacity-cost dependence))
                 :intervened-rule (get (:field genome) (:locus entry))
                 :held (get (:hold genome) (:locus entry))})))
     sites)))

(defn unit-row [base entry arm environment-seed tape-slot site]
  (first (unit-rows base entry arm environment-seed tape-slot [site])))

(defn combination-schedule [environment-seeds]
  (for [entry masking/registered-panel
        arm arms
        environment-seed environment-seeds
        tape-slot (range (count discovery-rewrite-tapes))]
    [entry arm environment-seed tape-slot]))

(defn schedule [environment-seeds]
  (for [[entry arm environment-seed tape-slot]
        (combination-schedule environment-seeds)
        site masking/evaluation-sites]
    [entry arm environment-seed tape-slot site]))

(defn run-panel [base environment-seeds]
  ;; Each environment/tape combination owns its RNGs; pmap affects runtime only,
  ;; not row order or value. Sites share the identical reference trajectory.
  (->> (combination-schedule environment-seeds)
       (pmap (fn [[entry arm environment-seed tape-slot]]
               (unit-rows base entry arm environment-seed tape-slot
                          masking/evaluation-sites)))
       (mapcat identity)
       vec))

(defn row-key [row]
  ((juxt :locus :arm :environment-seed :tape-slot :site) row))

(defn expected-keys [environment-seeds]
  (set (for [[entry arm environment-seed tape-slot site]
             (schedule environment-seeds)]
         [(:locus entry) arm environment-seed tape-slot site])))

(defn validate-rows! [rows environment-seeds]
  (let [grouped (group-by row-key rows)
        actual (set (keys grouped))
        expected (expected-keys environment-seeds)
        duplicates (filter (fn [[_ rs]] (not= 1 (count rs))) grouped)]
    (when (seq duplicates)
      (throw (ex-info "duplicate evaluation cells"
                      {:duplicates (mapv first duplicates)})))
    (when-not (= expected actual)
      (throw (ex-info "evaluation cells differ from amended schedule"
                      {:missing (count (set/difference expected actual))
                       :extra (count (set/difference actual expected))})))
    true))

(defn paired-deltas [rows left right field]
  (let [idx (into {} (map (juxt row-key identity)) rows)
        environments (sort (distinct (map :environment-seed rows)))]
    (vec
     (for [{:keys [locus]} masking/registered-panel
           environment-seed environments
           tape-slot (range (count discovery-rewrite-tapes))
           site masking/evaluation-sites]
       (- (double (field (idx [locus left environment-seed tape-slot site])))
          (double (field (idx [locus right environment-seed tape-slot site]))))))))

(defn contrast
  ([rows left right] (contrast rows left right :fitness))
  ([rows left right field]
   (let [idx (into {} (map (juxt row-key identity)) rows)
         environments (sort (distinct (map :environment-seed rows)))
         locus-votes
         (for [{:keys [locus]} masking/registered-panel]
           (let [deltas
                 (for [environment-seed environments
                       tape-slot (range (count discovery-rewrite-tapes))
                       site masking/evaluation-sites]
                   (- (double (field (idx [locus left environment-seed tape-slot site])))
                      (double (field (idx [locus right environment-seed tape-slot site])))))
                 positive (count (filter pos? deltas))
                 negative (count (filter neg? deltas))]
             (cond (> positive negative) :win
                   (> negative positive) :loss
                   :else :tie)))]
     {:wins (count (filter #{:win} locus-votes))
      :losses (count (filter #{:loss} locus-votes))
      :ties (count (filter #{:tie} locus-votes))})))

(def contrast-pairs
  {:good-held-vs-current-held [:held-good :held-current]
   :good-held-vs-bad-held [:held-good :held-bad]
   :good-held-vs-plastic-good [:held-good :plastic-good]
   :plastic-good-vs-plastic-current [:plastic-good :plastic-current]
   :discovery-held-good-vs-novel-held-good
   [:held-good :held-good-novel-tape]
   :held-current-vs-plastic-current [:held-current :plastic-current]})

(defn- choose [n k]
  (if (or (neg? k) (> k n))
    0N
    (reduce (fn [acc i] (quot (* acc (- n (dec i))) i))
            1N (range 1 (inc k)))))

(defn- binom-tail [n k]
  (reduce + 0N (map #(choose n %) (range k (inc n)))))

(defn familywise-win? [{:keys [wins losses]}]
  (let [n (+ wins losses)]
    (and (pos? n) (> wins losses)
         (<= (* 20N family-size (binom-tail n wins))
             (reduce * 1N (repeat n 2N))))))

(defn contrast-direction [{:keys [wins losses]}]
  (cond (> wins losses) :positive
        (< wins losses) :negative
        :else :zero))

(defn- mean-value [values]
  (/ (reduce + 0.0 values) (double (count values))))

(defn unit-level-estimate [rows left right field]
  (let [deltas (paired-deltas rows left right field)]
    {:units (count deltas)
     :mean-delta (mean-value deltas)
     :wins (count (filter pos? deltas))
     :losses (count (filter neg? deltas))
     :ties (count (filter zero? deltas))}))

(def ^:private constant-tolerance 1.0e-12)

(defn score-decomposition-valid? [rows]
  (every?
   (fn [row]
     (let [sum (reduce + 0.0
                       (for [[_ {:keys [field coefficient]}]
                             score-additive-terms]
                         (* coefficient (double (field row)))))]
       (<= (Math/abs (- (double (:fitness row)) sum)) constant-tolerance)))
   rows))

(defn additive-term-attribution [rows left right term]
  (let [{:keys [field coefficient]} term
        left-rows (filter #(= left (:arm %)) rows)
        right-rows (filter #(= right (:arm %)) rows)
        left-values (mapv #(* coefficient (double (field %))) left-rows)
        right-values (mapv #(* coefficient (double (field %))) right-rows)
        term-deltas (paired-deltas
                     (mapv #(assoc % ::term (* coefficient (double (field %)))) rows)
                     left right ::term)
        offset (first term-deltas)
        constant? (every? #(<= (Math/abs (- (double %) (double offset)))
                               constant-tolerance)
                          term-deltas)
        fitness-deltas (paired-deltas rows left right :fitness)
        winning-margins (filterv pos? fitness-deltas)
        within (if (and constant? (pos? offset))
                 (count (filter #(<= (double %) (+ (double offset)
                                                   constant-tolerance))
                                winning-margins))
                 0)]
    {:field field
     :coefficient coefficient
     :left-mean (mean-value left-values)
     :right-mean (mean-value right-values)
     :constant? constant?
     :constant-offset (when constant? offset)
     :unit-wins (count winning-margins)
     :wins-within-offset within
     :win-fraction-within-offset
     (if (seq winning-margins) (/ within (double (count winning-margins))) 0.0)}))

(defn readout-disagreements [contrasts-by-field]
  (vec
   (for [[name _] contrast-pairs
         :let [fitness (get-in contrasts-by-field [:fitness name])
               behavior (get-in contrasts-by-field [behavioral-field name])]
         :when (not= (contrast-direction fitness)
                     (contrast-direction behavior))]
     {:contrast name
      :fitness-direction (contrast-direction fitness)
      :behavior-direction (contrast-direction behavior)})))

(defn production-bar-disagreements [contrasts-by-field]
  (vec
   (for [[name _] contrast-pairs
         :let [fitness (get-in contrasts-by-field [:fitness name])
               behavior (get-in contrasts-by-field [behavioral-field name])]
         :when (and (familywise-win? fitness)
                    (not (familywise-win? behavior)))]
     {:contrast name :fitness fitness :behavior behavior})))

(defn constant-offset-failures [attribution threshold]
  (vec
   (for [[contrast terms] attribution
         [term-name result] terms
         :when (and (:constant? result)
                    (number? (:constant-offset result))
                    (pos? (double (:constant-offset result)))
                    (> (:win-fraction-within-offset result) threshold))]
     {:contrast contrast
      :term term-name
      :constant-offset (:constant-offset result)
      :unit-wins (:unit-wins result)
      :wins-within-offset (:wins-within-offset result)
      :win-fraction-within-offset (:win-fraction-within-offset result)})))

(defn readout-analysis [rows]
  (let [contrasts-by-field
        (into (sorted-map)
              (for [field readout-fields]
                [field
                 (into (sorted-map)
                       (for [[name [left right]] contrast-pairs]
                         [name (contrast rows left right field)]))]))
        unit-level
        (into (sorted-map)
              (for [field readout-fields]
                [field
                 (into (sorted-map)
                       (for [[name [left right]] contrast-pairs]
                         [name (unit-level-estimate rows left right field)]))]))
        attribution
        (into (sorted-map)
              (for [[name [left right]] contrast-pairs]
                [name
                 (into (sorted-map)
                       (for [[term-name term] score-additive-terms]
                         [term-name
                          (additive-term-attribution rows left right term)]))]))
        disagreements (readout-disagreements contrasts-by-field)
        production-disagreements
        (production-bar-disagreements contrasts-by-field)
        offset-failures
        (constant-offset-failures
         attribution constant-offset-win-fraction-threshold)]
    {:contrasts-by-field contrasts-by-field
     :unit-level-estimates unit-level
     :additive-term-attribution attribution
     :score-decomposition-valid (score-decomposition-valid? rows)
     :readout-disagreements disagreements
     :production-bar-disagreements production-disagreements
     :constant-offset-win-fraction-threshold
     constant-offset-win-fraction-threshold
     :constant-offset-failures offset-failures}))

(defn classify [contrasts]
  (let [passes (into {} (map (fn [[k c]] [k (familywise-win? c)]) contrasts))
        held-current (:good-held-vs-current-held passes)
        held-bad (:good-held-vs-bad-held passes)
        held-plastic (:good-held-vs-plastic-good passes)
        plastic-visible (:plastic-good-vs-plastic-current passes)
        tape-degrades (:discovery-held-good-vs-novel-held-good passes)]
    (cond
      tape-degrades :tape-degradation-detected
      (and held-current held-bad held-plastic (not plastic-visible))
      :joint-only-no-tape-degradation-detected
      (and held-current held-bad held-plastic plastic-visible)
      :visible-content-and-joint-advantage
      (and held-current held-bad (not held-plastic)) :held-content-specific
      (and plastic-visible (not held-current) (not held-bad)) :plastic-content-only
      (not-any? true? [held-current held-bad held-plastic plastic-visible])
      :no-registered-mechanism
      :else :mixed-evidence)))

(defn analyze [rows environment-seeds]
  (validate-rows! rows environment-seeds)
  (let [readouts (readout-analysis rows)
        contrasts (get-in readouts [:contrasts-by-field :fitness])
        counts (frequencies (map :arm rows))]
    (merge
     {:kind :baldwin-masking-six-arm-result
      :schema 2
      :environment-seeds environment-seeds
      :discovery-rewrite-tapes discovery-rewrite-tapes
      :novel-rewrite-tapes novel-rewrite-tapes
      :evaluation-sites masking/evaluation-sites
      :raw-units-per-arm (mapv counts arms)
      :contrasts contrasts
      :primary-contrast-family-size family-size
      :outcome (classify contrasts)}
     readouts)))

(def read-edn-lines masking/read-edn-lines)
(def write-edn-lines! masking/write-edn-lines!)
(def write-edn! masking/write-edn!)
(def sha256 masking/sha256)
