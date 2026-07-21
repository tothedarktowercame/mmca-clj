(ns mmca.experiments.aliveness-census
  "Full census of the 40320 bijective operators (permutations of the 8 genotype
  positions), each classified in the genotype-aliveness x phenotype-aliveness
  2x2. Aliveness of a field = mean changed-cell fraction between consecutive rows
  in the late window (0 = frozen/collapsed; ~0.3-0.8 = live), seed-averaged.
  Classified with a single threshold; the raw means are checkpointed so the
  threshold can be re-applied without recomputing.

  Central question: is the live-genotype / dead-phenotype quadrant (the fig8
  [0 0 1 2 3 4 5 6] phenomenon) reachable by any BIJECTION, or does it need a
  non-bijective writing? The census answers this directly.

  RESUMABLE: permutations are processed in chunks (pmap across cores); each
  finished chunk is appended to the checkpoint. Re-running -main resumes from the
  last chunk. Deterministic: each row depends only on (config, perm)."
  (:require [mmca.core :as c]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def config
  {:seeds 3 :width 60 :steps 100 :late 40 :alive 0.10 :chunk 200})

(def checkpoint-path "holes/aliveness-census-checkpoint.edn")
(def results-path "holes/aliveness-census-results.md")

(defn permutations [coll]
  (if (empty? coll)
    (list ())
    (mapcat (fn [x] (map #(cons x %) (permutations (remove #{x} coll)))) coll)))

(defn- change-rate
  "Mean fraction of cells that differ between consecutive rows."
  [rows]
  (let [pairs (map vector rows (rest rows))]
    (if (empty? pairs)
      0.0
      (/ (reduce + (map (fn [[a b]]
                          (/ (count (filter true? (map not= a b))) (double (count a))))
                        pairs))
         (count pairs)))))

(defn measure [perm]
  (let [{:keys [seeds width steps late]} config
        rs (for [s (range seeds)]
             (let [run (c/run-propagator (vec perm) s width steps)
                   g (vec (:gen run))
                   p (mapv vec (:phe run))
                   lg (take-last late g)
                   lp (take-last (inc late) p)]
               [(double (/ (reduce + (map (fn [r] (count (distinct r))) lg)) (count lg)))
                (change-rate lg)
                (change-rate lp)]))
        avg (fn [i] (/ (reduce + (map #(nth % i) rs)) (double (count rs))))]
    {:perm (vec perm) :g-div (avg 0) :g-change (avg 1) :p-change (avg 2)}))

(defn- load-done []
  (if (.exists (io/file checkpoint-path))
    (with-open [r (io/reader checkpoint-path)]
      (into #{} (comp (remove str/blank?) (map #(:perm (edn/read-string %))))
            (line-seq r)))
    #{}))

(defn- quadrant [th {:keys [g-change p-change]}]
  [(>= g-change th) (>= p-change th)])

(def ^:private quad-label
  {[true true] "genotype-ALIVE / phenotype-ALIVE"
   [true false] "genotype-ALIVE / phenotype-DEAD"
   [false true] "genotype-DEAD / phenotype-ALIVE"
   [false false] "genotype-DEAD / phenotype-DEAD"})

(def ^:private quad-score
  ;; per-quadrant "cleanest exemplar" scorer (higher = more prototypical)
  {[true true] (fn [r] (+ (:g-change r) (:p-change r)))
   [true false] (fn [r] (- (:g-change r) (:p-change r)))
   [false true] (fn [r] (- (:p-change r) (:g-change r)))
   [false false] (fn [r] (- (+ (:g-change r) (:p-change r))))})

(defn- report [rows]
  (let [th (:alive config)
        groups (group-by #(quadrant th %) rows)
        n (count rows)]
    (str "# Aliveness census -- 40320 bijective operators (result)\n\n"
         "Reproduce: `clojure -M -m mmca.experiments.aliveness-census`.\n"
         (format "Config: %s\n" (pr-str config))
         "Aliveness = late-window mean changed-cell fraction, seed-averaged; "
         (format "a field is ALIVE if its change rate >= %.2f.\n\n" th)
         "| quadrant | count | share | cleanest exemplars (perm : g-change p-change g-div) |\n"
         "|---|---:|---:|---|\n"
         (str/join "\n"
           (for [k [[true true] [true false] [false true] [false false]]
                 :let [g (groups k [])
                       ex (->> g (sort-by (quad-score k) >) (take 3))]]
             (format "| %s | %d | %.1f%% | %s |"
                     (quad-label k) (count g) (* 100.0 (/ (count g) (double n)))
                     (str/join "; "
                       (map #(format "%s : %.2f %.2f %.1f"
                                     (pr-str (:perm %)) (:g-change %) (:p-change %) (:g-div %))
                            ex)))))
         "\n")))

(defn -main [& _]
  (let [all (map vec (permutations (range 8)))
        done (load-done)
        todo (remove done all)]
    (println (format "aliveness census | %d/%d done, %d to go" (count done) (count all) (count todo)))
    (flush)
    (doseq [chunk (partition-all (:chunk config) todo)]
      (let [rows (doall (pmap measure chunk))]
        (spit checkpoint-path (str (str/join "\n" (map pr-str rows)) "\n") :append true)
        (print ".") (flush)))
    (println)
    (let [rows (with-open [r (io/reader checkpoint-path)]
                 (into [] (comp (remove str/blank?) (map edn/read-string)) (line-seq r)))]
      (spit results-path (report rows))
      (println (str "COMPLETE -> " results-path " (" (count rows) " operators)"))
      (flush))))
