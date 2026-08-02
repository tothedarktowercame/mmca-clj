(ns mmca.baldwin-masking-intervention
  "Executable companion to DarkTower.BaldwinMaskingInterventionPreregistration.

   The confirmation unit is a locus. Seed/site rows remain available for audit,
   but are reduced to one strict-majority direction per locus before testing."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [mmca.baldwin-selection :as selection])
  (:import [java.math BigInteger]
           [java.security MessageDigest]))

(def discovery-revision "f893a4005842ca0ceeb472020990ed131bb2de67")
(def discovery-map-sha256
  "2d883386dfb04420af393b6c11513216ab810e062f984a61d1fb818e9dbb4b63")
(def lean-revision "3495b10b68657778657742edf501e93ef2208741")
(def pilot-seeds [901 902 903])
(def confirmation-seeds [101 102 103 104 105 106 107 108])
(def evaluation-sites [0 10 20 30 40 50 60 70])
(def capacity-cost 0.05)
(def capacity-cost-basis-points 500)
(def family-size 4)
(def arms [:plastic-current :held-current :plastic-good :held-good :held-bad])

(def registered-panel
  [{:stratum :early-dense :locus 0 :current-rule 224 :good-rule 52 :bad-rule 138 :hamming-distance 4}
   {:stratum :early-dense :locus 1 :current-rule 41 :good-rule 2 :bad-rule 174 :hamming-distance 4}
   {:stratum :early-dense :locus 2 :current-rule 191 :good-rule 47 :bad-rule 167 :hamming-distance 2}
   {:stratum :early-dense :locus 3 :current-rule 59 :good-rule 102 :bad-rule 65 :hamming-distance 5}
   {:stratum :middle-dense :locus 24 :current-rule 168 :good-rule 50 :bad-rule 59 :hamming-distance 4}
   {:stratum :middle-dense :locus 26 :current-rule 200 :good-rule 71 :bad-rule 123 :hamming-distance 5}
   {:stratum :middle-dense :locus 28 :current-rule 69 :good-rule 125 :bad-rule 108 :hamming-distance 3}
   {:stratum :middle-dense :locus 29 :current-rule 24 :good-rule 97 :bad-rule 100 :hamming-distance 5}
   {:stratum :middle-sparse :locus 45 :current-rule 28 :good-rule 146 :bad-rule 7 :hamming-distance 4}
   {:stratum :middle-sparse :locus 51 :current-rule 64 :good-rule 169 :bad-rule 27 :hamming-distance 5}
   {:stratum :middle-sparse :locus 52 :current-rule 105 :good-rule 37 :bad-rule 8 :hamming-distance 3}
   {:stratum :middle-sparse :locus 54 :current-rule 206 :good-rule 193 :bad-rule 4 :hamming-distance 4}
   {:stratum :late-sparse :locus 60 :current-rule 122 :good-rule 97 :bad-rule 2 :hamming-distance 4}
   {:stratum :late-sparse :locus 67 :current-rule 197 :good-rule 18 :bad-rule 10 :hamming-distance 6}
   {:stratum :late-sparse :locus 75 :current-rule 230 :good-rule 56 :bad-rule 121 :hamming-distance 6}
   {:stratum :late-sparse :locus 76 :current-rule 174 :good-rule 180 :bad-rule 30 :hamming-distance 3}])

(defn sha256 [path]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (with-open [input (io/input-stream path)]
      (let [buffer (byte-array 65536)]
        (loop []
          (let [n (.read input buffer)]
            (when (pos? n)
              (.update digest buffer 0 n)
              (recur))))))
    (format "%064x" (BigInteger. 1 (.digest digest)))))

(defn- parse-value [column value]
  (cond
    (#{:locus :rule :current-rule} column) (parse-long value)
    (#{:reach :band :dependence :fitness :base-band :base-fitness} column)
    (parse-double value)
    (#{:function-preserved :selectable} column) (= "true" value)
    :else value))

(defn read-map [path]
  (let [[header & lines] (str/split-lines (slurp path))
        columns (mapv #(keyword (str/replace % "_" "-")) (str/split header #"\t"))]
    (mapv (fn [line]
            (into {} (map (fn [column value] [column (parse-value column value)])
                          columns (str/split line #"\t"))))
          (remove str/blank? lines))))

(defn hamming-distance [a b]
  (Integer/bitCount (bit-xor (int a) (int b))))

(defn- endpoint-counts [rows]
  (->> rows
       (group-by :locus)
       (map (fn [[locus rs]] [locus (count (filter :selectable rs))]))
       (into {})))

(defn- choose-loci [counts lo hi dense?]
  (->> (range lo hi)
       (filter #(pos? (get counts % 0)))
       (sort-by (fn [locus]
                  [(if dense? (- (get counts locus)) (get counts locus)) locus]))
       (take 4)
       sort))

(defn- best-good [rows locus]
  (->> rows
       (filter #(and (= locus (:locus %)) (:selectable %)))
       (sort-by (juxt (comp - :fitness) :rule))
       first))

(defn- best-bad [rows locus current distance]
  (->> rows
       (filter #(and (= locus (:locus %))
                     (not (:selectable %))
                     (= distance (hamming-distance current (:rule %)))))
       (sort-by (juxt :fitness :rule))
       first))

(defn derive-panel [rows]
  (let [counts (endpoint-counts rows)
        groups [[:early-dense (choose-loci counts 0 20 true)]
                [:middle-dense (choose-loci counts 20 40 true)]
                [:middle-sparse (choose-loci counts 40 60 false)]
                [:late-sparse (choose-loci counts 60 80 false)]]]
    (mapv
     (fn [[stratum locus]]
       (let [good (best-good rows locus)
             current (:current-rule good)
             distance (hamming-distance current (:rule good))
             bad (best-bad rows locus current distance)]
         (when-not (and good bad)
           (throw (ex-info "panel endpoint is not derivable"
                           {:stratum stratum :locus locus})))
         {:stratum stratum :locus locus :current-rule current
          :good-rule (:rule good) :bad-rule (:rule bad)
          :hamming-distance distance}))
     (mapcat (fn [[stratum loci]] (map #(vector stratum %) loci)) groups))))

(defn validate-discovery! [map-path]
  (let [actual-sha (sha256 map-path)
        panel (derive-panel (read-map map-path))]
    (when-not (= discovery-map-sha256 actual-sha)
      (throw (ex-info "discovery map checksum mismatch"
                      {:expected discovery-map-sha256 :actual actual-sha})))
    (when-not (= registered-panel panel)
      (throw (ex-info "rederived panel differs from registration"
                      {:expected registered-panel :actual panel})))
    {:source-revision-observed discovery-revision
     :source-map-sha256-observed actual-sha
     :panel-selection-recomputed true
     :observed-panel panel}))

(defn intervene [genome {:keys [locus current-rule good-rule bad-rule]} arm]
  (when-not (= current-rule (get (:field genome) locus))
    (throw (ex-info "base genome does not match registered current rule"
                    {:locus locus :expected current-rule
                     :actual (get (:field genome) locus)})))
  (let [[rule held?]
        (case arm
          :plastic-current [current-rule false]
          :held-current [current-rule true]
          :plastic-good [good-rule false]
          :held-good [good-rule true]
          :held-bad [bad-rule true]
          (throw (ex-info "unknown intervention arm" {:arm arm})))]
    (-> genome
        (assoc :field (assoc (:field genome) locus rule))
        (assoc :hold (assoc (:hold genome) locus held?)))))

(defn unit-row [base entry arm seed site]
  (let [genome (intervene base entry arm)
        reach (:mean (selection/reach genome [seed] [site]))
        dependence (selection/plastic-dependence genome)]
    (merge entry
           {:arm arm :seed seed :site site :reach reach
            :band (selection/band-score reach)
            :dependence dependence
            :fitness (- (selection/band-score reach) (* capacity-cost dependence))
            :intervened-rule (get (:field genome) (:locus entry))
            :held (get (:hold genome) (:locus entry))})))

(defn run-panel [base seeds]
  ;; Each row is deterministic and owns its RNGs, so bounded JVM worker-pool
  ;; parallelism changes runtime but not ordering or values.
  (->> (for [entry registered-panel arm arms seed seeds site evaluation-sites]
         [entry arm seed site])
       (pmap (fn [[entry arm seed site]] (unit-row base entry arm seed site)))
       vec))

(defn- index-rows [rows]
  (let [grouped (group-by (juxt :locus :arm :seed :site) rows)
        duplicates (into {} (filter (fn [[_ rs]] (not= 1 (count rs))) grouped))]
    (when (seq duplicates)
      (throw (ex-info "duplicate evaluation cells" {:duplicates (keys duplicates)})))
    (into {} (map (fn [[k rs]] [k (first rs)]) grouped))))

(defn expected-keys [seeds]
  (set (for [entry registered-panel arm arms seed seeds site evaluation-sites]
         [(:locus entry) arm seed site])))

(defn validate-rows! [rows seeds]
  (let [actual (set (map (juxt :locus :arm :seed :site) rows))
        expected (expected-keys seeds)]
    (when-not (= expected actual)
      (throw (ex-info "evaluation cells differ from registered schedule"
                      {:missing (count (set/difference expected actual))
                       :extra (count (set/difference actual expected))})))
    true))

(defn contrast [rows left right]
  (let [idx (index-rows rows)
        locus-votes
        (for [{:keys [locus]} registered-panel]
          (let [deltas (for [seed (distinct (map :seed rows))
                             site evaluation-sites]
                         (- (:fitness (idx [locus left seed site]))
                            (:fitness (idx [locus right seed site]))))
                positive (count (filter pos? deltas))
                negative (count (filter neg? deltas))]
            (cond (> positive negative) :win
                  (> negative positive) :loss
                  :else :tie)))]
    {:wins (count (filter #{:win} locus-votes))
     :losses (count (filter #{:loss} locus-votes))
     :ties (count (filter #{:tie} locus-votes))}))

(defn- choose [n k]
  (if (or (neg? k) (> k n))
    0N
    (reduce (fn [acc i] (quot (* acc (- n (dec i))) i)) 1N (range 1 (inc k)))))

(defn binom-tail [n k]
  (reduce + 0N (map #(choose n %) (range k (inc n)))))

(defn familywise-win? [{:keys [wins losses]}]
  (let [n (+ wins losses)]
    (and (pos? n) (> wins losses)
         (<= (* 20N family-size (binom-tail n wins))
             (reduce * 1N (repeat n 2N))))))

(def contrast-pairs
  {:good-held-vs-current-held [:held-good :held-current]
   :good-held-vs-bad-held [:held-good :held-bad]
   :good-held-vs-plastic-good [:held-good :plastic-good]
   :plastic-good-vs-plastic-current [:plastic-good :plastic-current]
   :held-current-vs-plastic-current [:held-current :plastic-current]})

(defn classify [contrasts]
  (let [passes (into {} (map (fn [[k c]] [k (familywise-win? c)]) contrasts))
        held-current (:good-held-vs-current-held passes)
        held-bad (:good-held-vs-bad-held passes)
        held-plastic (:good-held-vs-plastic-good passes)
        plastic-visible (:plastic-good-vs-plastic-current passes)]
    (cond
      (and held-current held-bad held-plastic (not plastic-visible)) :joint-only-detected
      (and held-current held-bad held-plastic plastic-visible) :visible-content-and-joint-advantage
      (and held-current held-bad (not held-plastic)) :held-content-specific
      (and plastic-visible (not held-current) (not held-bad)) :plastic-content-only
      (not-any? true? (map passes (keys (dissoc contrast-pairs :held-current-vs-plastic-current))))
      :no-registered-mechanism
      :else :mixed-evidence)))

(defn analyze [rows seeds]
  (validate-rows! rows seeds)
  (let [contrasts (into {}
                        (map (fn [[name [left right]]]
                               [name (contrast rows left right)]))
                        contrast-pairs)]
    {:kind :baldwin-masking-intervention-result
     :schema 1
     :evaluation-seeds seeds
     :evaluation-sites evaluation-sites
     :raw-units-per-arm (* (count registered-panel) (count seeds)
                           (count evaluation-sites))
     :contrasts contrasts
     :family-size family-size
     :outcome (classify contrasts)}))

(defn read-edn-lines [path]
  (->> (str/split-lines (slurp path))
       (remove str/blank?)
       (mapv edn/read-string)))

(defn write-edn-lines! [path rows]
  (spit path (str (str/join "\n" (map pr-str rows)) "\n")))

(defn write-edn! [path value]
  (spit path (str (pr-str value) "\n")))
