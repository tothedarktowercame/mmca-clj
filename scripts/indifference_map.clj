;; PRE-GO TEST for a propagating gain field.
;;
;; For each cell, the fraction of steps at which the live and frozen reads select
;; the SAME rule. If indifference is spatially structured -- some cells reliably
;; indifferent -- a gain field carried in the phenotype has something to lock onto.
;; If it is flat near the global rate for every cell, indifference is temporally
;; scattered, nothing accumulates, and a propagating field fails for the same
;; reason the static per-cell mask did (mean-plastic settled at 0.963, not 0.73).
;;
;; Uses the published construction unchanged: same combine-rule, same writing, same
;; frozen-field discipline as scripts/river_gain.clj.

(require '[mmca.core :as c])
(def W 80) (def TSTAR 60) (def STEPS 120)

(defn step-and-compare
  "Advance one river step at gamma=1 and record, per cell, whether the rule the
   LIVE context selects equals the rule the FROZEN context would have selected."
  [^java.util.Random random genotype phenotype next-phenotype frozen agree]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       (let [pred (if (zero? i) c/default-rule (nth genotype (dec i)))
             ctr (nth genotype i)
             succ (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             ctx (fn [ph nph]
                   (when (and (pos? i) (< i (dec width)))
                     [(Character/digit (nth ph (dec i)) 2)
                      (Character/digit (nth ph i) 2)
                      (Character/digit (nth ph (inc i)) 2)
                      (Character/digit (nth nph i) 2)]))
             live-rule (c/original-river-combine-rule pred ctr succ (ctx phenotype next-phenotype))
             froz-rule (c/original-river-combine-rule pred ctr succ (ctx frozen frozen))
             source (.nextInt random c/bit-count)]
         (when (= live-rule froz-rule) (vswap! agree update i (fnil inc 0)))
         (c/propagate-at live-rule c/river-writing source true)))
     (range width))))

(let [seeds [1 2 3]
      ;; The genotype is FIXED across seeds -- that is what a genome in the selection
      ;; loop actually experiences: one heritable field scored against several
      ;; phenotype initial conditions. Drawing a fresh genotype per seed (the earlier
      ;; version) compared cell i holding rule X against cell i holding rule Y, which
      ;; are different cells in every sense that matters, so zero correlation was
      ;; guaranteed and said nothing about assimilability.
      fixed-field (c/java-random-genotype (java.util.Random. 20260730) W)
      per-seed
      (for [seed seeds]
        (let [r (java.util.Random. (long seed))
              g0 fixed-field
              p0 (c/java-random-phenotype r W)
              agree (volatile! {})
              steps (atom 0)]
          ;; frozen reference is the t* phenotype of the unperturbed run, exactly as
          ;; the published ablation supplies it from outside
          ;; warm-up uses the SAME river update (river_gain.clj drives it from a
          ;; java.util.Random directly, not through genotype-step), with a throwaway
          ;; tally so the comparison only counts the measured pass
          (let [scratch (volatile! {})
                warm (loop [t 0 g g0 p p0]
                       (if (= t TSTAR) p
                         (let [np (c/phenotype-step g p)]
                           (recur (inc t) (step-and-compare r g p np p scratch) np))))]
            (loop [t 0 g g0 p p0]
              (when (< t STEPS)
                (let [np (c/phenotype-step g p)
                      ng (step-and-compare r g p np warm agree)]
                  (swap! steps inc)
                  (recur (inc t) ng np)))))
          (mapv #(/ (double (get @agree % 0)) @steps) (range W))))]
  (println "cell\tseed1\tseed2\tseed3\tmean")
  (doseq [i (range W)]
    (let [vs (mapv #(nth % i) per-seed)]
      (println (format "%d\t%.4f\t%.4f\t%.4f\t%.4f" i (nth vs 0) (nth vs 1) (nth vs 2)
                       (/ (reduce + vs) 3.0))))))
