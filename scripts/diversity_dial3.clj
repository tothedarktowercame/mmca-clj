;; A noise-free early-fork diversity/damage experiment.
;;
;; diversity_dial.clj showed that replacement noise expands the diversity
;; range but mechanically erases paired damage. Here every configuration is
;; noise-free, every fork gets the same 40-step response horizon, and fork time
;; is the diversity dial. The strongest braid is also combined with continuous
;; blending, asynchronous refuges, and spatially phase-shifted braid niches.
(ns scripts.diversity-dial3
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

(defn braid-writing [config t i]
  (let [phase (if (= :phase-niches (:kind config))
                (quot i (:patch-width config))
                0)]
    (if (even? (+ t phase))
      (:writing-a config)
      (:writing-b config))))

(defn genotype-step
  "Apply one noise-free dial-3 step. All stochastic choices consume separate
  RNG streams whose states are cloned exactly at a perturbation fork."
  [source-r mechanism-r genotype config t]
  (let [width (count genotype)
        evaluation-order (concat [0 (dec width)] (range 1 (dec width)))]
    (reduce
     (fn [output i]
       (let [[left centre right] (neighbours genotype i)
             blended (c/blend-rule left centre right)
             source (rng/rand-int source-r c/bit-count)
             writing (braid-writing config t i)
             next-rule
             (case (:kind config)
               :braid
               (c/propagate-at blended writing source true)

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

               :phase-niches
               (c/propagate-at blended writing source true))]
         (assoc output i next-rule)))
     (vec (repeat width nil))
     evaluation-order)))

(defn paired-response
  [config source-r mechanism-r genotype phenotype fork-time horizon site]
  (let [source-a (clone-rng source-r)
        source-b (clone-rng source-r)
        mechanism-a (clone-rng mechanism-r)
        mechanism-b (clone-rng mechanism-r)
        genotype-b (assoc genotype site
                          (bit-xor (nth genotype site) 1))]
    (loop [dt 0
           genotype-a genotype
           phenotype-a phenotype
           genotype-b* genotype-b
           phenotype-b phenotype
           damage-area 0
           damage-peak 1]
      (if (= dt horizon)
        (merge {:dG-final (c/changed-count genotype-a genotype-b*)
                :dG-peak damage-peak
                :dG-area damage-area}
               (diversity-stats genotype-a))
        (let [t (+ fork-time dt)
              next-a (genotype-step source-a mechanism-a genotype-a config t)
              next-b (genotype-step source-b mechanism-b genotype-b* config t)
              next-phenotype-a (c/phenotype-step genotype-a phenotype-a)
              next-phenotype-b (c/phenotype-step genotype-b* phenotype-b)
              damage (c/changed-count next-a next-b)]
          (recur (inc dt)
                 next-a
                 next-phenotype-a
                 next-b
                 next-phenotype-b
                 (+ damage-area damage)
                 (max damage-peak damage)))))))

(defn probe
  [config seed width fork-time horizon sites]
  (let [source-r (rng/make-rng (format "prop-%d" seed))
        mechanism-r (rng/make-rng (format "dial3-%d" seed))
        ;; Match the full-population initialization convention of dial 1.
        _ (c/random-genotype source-r width)
        genotype-0 (seeded-population seed width)
        phenotype-0 (c/random-phenotype source-r width)]
    (loop [t 0
           genotype genotype-0
           phenotype phenotype-0]
      (if (= t fork-time)
        {:at-fork (diversity-stats genotype)
         :responses
         (mapv (fn [site]
                 (paired-response config source-r mechanism-r
                                  genotype phenotype fork-time horizon site))
               sites)}
        (recur (inc t)
               (genotype-step source-r mechanism-r genotype config t)
               (c/phenotype-step genotype phenotype))))))

(let [width 256
      horizon 40
      fork-times [0 5 10 20 30]
      sites (range 0 width 32)
      pa [3 0 1 2 7 4 5 6]
      two-4-cycle [6 7 0 2 1 4 3 5]
      rotation-2 [2 3 4 5 6 7 0 1]
      rotation-4 [4 5 6 7 0 1 2 3]
      configs
      [{:name "braid pa/t4"
        :kind :braid
        :writing-a pa
        :writing-b two-4-cycle}
       {:name "braid r2/r4"
        :kind :braid
        :writing-a rotation-2
        :writing-b rotation-4}
       {:name "braid pa/t4 blend0.70"
        :kind :blend-strength
        :writing-a pa
        :writing-b two-4-cycle
        :blend-strength 0.70}
       {:name "braid pa/t4 async0.75"
        :kind :async-refuges
        :writing-a pa
        :writing-b two-4-cycle
        :update-fraction 0.75}
       {:name "phase-niches pa/t4 patch64"
        :kind :phase-niches
        :writing-a pa
        :writing-b two-4-cycle
        :patch-width 64}]]
  (println
   (str "cfg\tseed\tfork-time\trules-fork\teffective-fork"
        "\trules-end\teffective-end\tdG-final\tdG-peak\tdG-area"))
  (doseq [config configs
          fork-time fork-times
          seed (range 2)]
    (let [{:keys [at-fork responses]}
          (probe config seed width fork-time horizon sites)]
      (doseq [response responses]
        (println
         (format "%s\t%d\t%d\t%d\t%.2f\t%d\t%.2f\t%d\t%d\t%d"
                 (:name config)
                 seed
                 fork-time
                 (:rules at-fork)
                 (:effective-rules at-fork)
                 (:rules response)
                 (:effective-rules response)
                 (:dG-final response)
                 (:dG-peak response)
                 (:dG-area response)))))))
