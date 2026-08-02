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

(defn tapes-for-arm [arm]
  (if (= :held-good-novel-tape arm)
    novel-rewrite-tapes
    discovery-rewrite-tapes))

(defn intervention-arm [arm]
  (if (= :held-good-novel-tape arm) :held-good arm))

(defn intervene [genome entry arm]
  (masking/intervene genome entry (intervention-arm arm)))

(defn unit-row [base entry arm environment-seed tape-slot site]
  (let [rewrite-tape (nth (tapes-for-arm arm) tape-slot)
        genome (intervene base entry arm)
        reach (:mean (selection/reach-separated
                      genome [[environment-seed rewrite-tape]] [site]))
        dependence (selection/plastic-dependence genome)]
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

(defn schedule [environment-seeds]
  (for [entry masking/registered-panel
        arm arms
        environment-seed environment-seeds
        tape-slot (range (count discovery-rewrite-tapes))
        site masking/evaluation-sites]
    [entry arm environment-seed tape-slot site]))

(defn run-panel [base environment-seeds]
  ;; Each cell owns its RNGs; pmap affects runtime only, not row order or value.
  (->> (schedule environment-seeds)
       (pmap (fn [[entry arm environment-seed tape-slot site]]
               (unit-row base entry arm environment-seed tape-slot site)))
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

(defn contrast [rows left right]
  (let [idx (into {} (map (juxt row-key identity)) rows)
        environments (sort (distinct (map :environment-seed rows)))
        locus-votes
        (for [{:keys [locus]} masking/registered-panel]
          (let [deltas
                (for [environment-seed environments
                      tape-slot (range (count discovery-rewrite-tapes))
                      site masking/evaluation-sites]
                  (- (:fitness (idx [locus left environment-seed tape-slot site]))
                     (:fitness (idx [locus right environment-seed tape-slot site]))))
                positive (count (filter pos? deltas))
                negative (count (filter neg? deltas))]
            (cond (> positive negative) :win
                  (> negative positive) :loss
                  :else :tie)))]
    {:wins (count (filter #{:win} locus-votes))
     :losses (count (filter #{:loss} locus-votes))
     :ties (count (filter #{:tie} locus-votes))}))

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
  (let [contrasts (into (sorted-map)
                        (map (fn [[name [left right]]]
                               [name (contrast rows left right)]))
                        contrast-pairs)
        counts (frequencies (map :arm rows))]
    {:kind :baldwin-masking-six-arm-result
     :schema 1
     :environment-seeds environment-seeds
     :discovery-rewrite-tapes discovery-rewrite-tapes
     :novel-rewrite-tapes novel-rewrite-tapes
     :evaluation-sites masking/evaluation-sites
     :raw-units-per-arm (mapv counts arms)
     :contrasts contrasts
     :primary-contrast-family-size family-size
     :outcome (classify contrasts)}))

(def read-edn-lines masking/read-edn-lines)
(def write-edn-lines! masking/write-edn-lines!)
(def write-edn! masking/write-edn!)
(def sha256 masking/sha256)
