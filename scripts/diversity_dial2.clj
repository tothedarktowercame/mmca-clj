;; Orthogonal diversity dials for comparison with diversity_dial.clj:
;;
;; 1. blend strength: blend the neighbourhood with probability b, otherwise
;;    propagate from the centre rule;
;; 2. asynchronous refuges: update a cell with probability u, otherwise retain
;;    its rule exactly for that step;
;; 3. spatial niches: alternate two writings in fixed spatial patches.
;;
;; Every stochastic decision has its own RNG. Both perturbation branches clone
;; every RNG at the fork, so the mechanisms inject no spurious branch damage.
(ns scripts.diversity-dial2
  (:require [mmca.core :as c]
            [mmca.rng :as rng]))

(defn clone-rng [r]
  (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))

(defn seeded-population [seed width]
  (let [random (java.util.Random. (+ 90000 seed))
        base (vec (take width (cycle (range 256))))]
    (loop [population base
           i (dec (count population))]
      (if (<= i 0)
        population
        (let [j (.nextInt random (inc i))]
          (recur (assoc population
                        i (nth population j)
                        j (nth population i))
                 (dec i)))))))

(defn diversity-stats [genotype]
  (let [width (double (count genotype))
        counts (vals (frequencies genotype))
        entropy (- (reduce + 0.0
                           (map (fn [n]
                                  (let [p (/ n width)]
                                    (* p (Math/log p))))
                                counts)))]
    {:rules (count counts)
     :effective-rules (Math/exp entropy)}))

(defn neighbours [genotype i]
  (let [width (count genotype)]
    [(if (zero? i) c/default-rule (nth genotype (dec i)))
     (nth genotype i)
     (if (= i (dec width)) c/default-rule (nth genotype (inc i)))]))

(defn writing-at [config i]
  (if (= :niches (:kind config))
    (if (even? (quot i (:patch-width config)))
      (:writing-a config)
      (:writing-b config))
    (:writing config)))

(defn genotype-step
  "Apply one dial-2 genotype step. Source and mechanism draws are consumed
  independently of genotype values, preserving paired-branch comparability."
  [source-r mechanism-r genotype config]
  (let [width (count genotype)
        evaluation-order (concat [0 (dec width)] (range 1 (dec width)))]
    (reduce
     (fn [output i]
       (let [[left centre right] (neighbours genotype i)
             blended (c/blend-rule left centre right)
             source (rng/rand-int source-r c/bit-count)
             writing (writing-at config i)
             next-rule
             (case (:kind config)
               :blend-strength
               (let [blend? (< (rng/rand-double mechanism-r)
                               (:blend-strength config))]
                 (c/propagate-at (if blend? blended centre)
                                 writing source true))

               :async-refuges
               (let [update? (< (rng/rand-double mechanism-r)
                                (:update-fraction config))]
                 (if update?
                   (c/propagate-at blended writing source true)
                   centre))

               :niches
               (c/propagate-at blended writing source true))]
         (assoc output i next-rule)))
     (vec (repeat width nil))
     evaluation-order)))

(defn probe [config seed width steps fork-time sites]
  (let [source-r (rng/make-rng (format "prop-%d" seed))
        mechanism-r (rng/make-rng (format "dial2-%d" seed))
        ;; Consume the ordinary genotype initialization so phenotype seeding is
        ;; directly comparable with diversity_dial.clj.
        _ (c/random-genotype source-r width)
        genotype-0 (seeded-population seed width)
        phenotype-0 (c/random-phenotype source-r width)]
    (loop [t 0
           genotype genotype-0
           phenotype phenotype-0]
      (if (= t fork-time)
        {:at-fork (diversity-stats genotype)
         :probes
         (mapv
          (fn [x]
            (let [source-a (clone-rng source-r)
                  source-b (clone-rng source-r)
                  mechanism-a (clone-rng mechanism-r)
                  mechanism-b (clone-rng mechanism-r)
                  genotype-b (assoc genotype x
                                    (bit-xor (nth genotype x) 1))]
              (loop [tt fork-time
                     genotype-a genotype
                     phenotype-a phenotype
                     genotype-b* genotype-b
                     phenotype-b phenotype]
                (if (= tt steps)
                  (merge {:dG (c/changed-count genotype-a genotype-b*)}
                         (diversity-stats genotype-a))
                  (recur
                   (inc tt)
                   (genotype-step source-a mechanism-a genotype-a config)
                   (c/phenotype-step genotype-a phenotype-a)
                   (genotype-step source-b mechanism-b genotype-b* config)
                   (c/phenotype-step genotype-b* phenotype-b))))))
          sites)}
        (recur (inc t)
               (genotype-step source-r mechanism-r genotype config)
               (c/phenotype-step genotype phenotype))))))

(let [width 256
      steps 70
      fork-time 30
      sites (range 0 width 16)
      pa [3 0 1 2 7 4 5 6]
      two-4-cycle [6 7 0 2 1 4 3 5]
      rotation-1 [1 2 3 4 5 6 7 0]
      rotation-3 [3 4 5 6 7 0 1 2]
      configs
      (concat
       (for [strength [0.0 0.1 0.35 0.7 1.0]]
         {:name (format "blend-strength %.2f" strength)
          :kind :blend-strength
          :writing pa
          :blend-strength strength})
       (for [fraction [0.25 0.5 0.75]]
         {:name (format "async-refuges %.2f" fraction)
          :kind :async-refuges
          :writing pa
          :update-fraction fraction})
       [{:name "niches pa/two4 patch16"
         :kind :niches
         :writing-a pa
         :writing-b two-4-cycle
         :patch-width 16}
        {:name "niches pa/two4 patch64"
         :kind :niches
         :writing-a pa
         :writing-b two-4-cycle
         :patch-width 64}
        {:name "niches r1/r3 patch16"
         :kind :niches
         :writing-a rotation-1
         :writing-b rotation-3
         :patch-width 16}])]
  (println "cfg\tseed\trules-t30\teffective-t30\trules-t70\teffective-t70\tdG")
  (doseq [config configs
          seed (range 2)]
    (let [{:keys [at-fork probes]}
          (probe config seed width steps fork-time sites)]
      (doseq [result probes]
        (println
         (format "%s\t%d\t%d\t%.2f\t%d\t%.2f\t%d"
                 (:name config)
                 seed
                 (:rules at-fork)
                 (:effective-rules at-fork)
                 (:rules result)
                 (:effective-rules result)
                 (:dG result)))))))
