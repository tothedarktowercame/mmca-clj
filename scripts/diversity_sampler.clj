;; Generate a 3x3 sampler of representative high-diversity behaviours from
;; diversity_dial{,2,3}.clj. All panels use the same full-population seed,
;; width, and horizon so their spacetime fields are directly comparable.
(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[mmca.core :as c]
         '[mmca.rng :as rng])

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

(defn writing-at [config t i]
  (let [spatial-phase
        (case (:kind config)
          :static-niches (quot i (:patch-width config))
          :phase-niches (quot i (:patch-width config))
          0)
        temporal-phase
        (case (:kind config)
          :static-niches 0
          t)]
    (if (even? (+ spatial-phase temporal-phase))
      (:writing-a config)
      (or (:writing-b config) (:writing-a config)))))

(defn genotype-step [source-r mechanism-r noise-r genotype config t]
  (let [width (count genotype)
        evaluation-order (concat [0 (dec width)] (range 1 (dec width)))
        updated
        (reduce
         (fn [output i]
           (let [[left centre right] (neighbours genotype i)
                 blended (c/blend-rule left centre right)
                 source (rng/rand-int source-r c/bit-count)
                 writing (writing-at config t i)
                 next-rule
                 (case (:kind config)
                   (:full :static-niches :phase-niches)
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
                       centre)))]
             (assoc output i next-rule)))
         (vec (repeat width nil))
         evaluation-order)]
    (if (pos? (:noise-p config 0.0))
      (c/genotype-noise noise-r updated (:noise-p config))
      updated)))

(defn run-config [config seed width steps]
  (let [source-r (rng/make-rng (format "prop-%d" seed))
        mechanism-r (rng/make-rng (format "sampler-mechanism-%d" seed))
        noise-r (rng/make-rng (format "noise-%d" seed))
        _ (c/random-genotype source-r width)
        genotype-0 (seeded-population seed width)
        phenotype-0 (c/random-phenotype source-r width)]
    (loop [t 0
           genotype genotype-0
           phenotype phenotype-0
           genotypes [genotype-0]
           phenotypes [phenotype-0]]
      (if (= t steps)
        {:gen genotypes
         :phe phenotypes}
        (let [next-genotype
              (genotype-step source-r mechanism-r noise-r genotype config t)
              next-phenotype (c/phenotype-step genotype phenotype)]
          (recur (inc t)
                 next-genotype
                 next-phenotype
                 (conj genotypes next-genotype)
                 (conj phenotypes next-phenotype)))))))

(defn write-run! [path run]
  (spit path
        (str "GEN\n"
             (str/join "\n"
                       (map #(str/join " " %) (:gen run)))
             "\nPHE\n"
             (str/join "\n" (:phe run))
             "\n")))

(let [seed 0
      width 256
      steps 70
      pa [3 0 1 2 7 4 5 6]
      two-4-cycle [6 7 0 2 1 4 3 5]
      rotation-2 [2 3 4 5 6 7 0 1]
      rotation-4 [4 5 6 7 0 1 2 3]
      configs
      [{:group "dial1"
        :tag "dial1-noise020"
        :name "PA + replacement noise 0.20"
        :kind :full
        :writing-a pa
        :noise-p 0.20}
       {:group "dial1"
        :tag "dial1-noise040"
        :name "PA + replacement noise 0.40"
        :kind :full
        :writing-a pa
        :noise-p 0.40}
       {:group "dial1"
        :tag "dial1-braid-pa-t4"
        :name "braid PA / two-4-cycle"
        :kind :full
        :writing-a pa
        :writing-b two-4-cycle}
       {:group "dial1"
        :tag "dial1-braid-r2-r4"
        :name "braid rot+2 / rot+4"
        :kind :full
        :writing-a rotation-2
        :writing-b rotation-4}

       {:group "dial2"
        :tag "dial2-blend035"
        :name "PA, blend strength 0.35"
        :kind :blend-strength
        :writing-a pa
        :blend-strength 0.35}
       {:group "dial2"
        :tag "dial2-blend070"
        :name "PA, blend strength 0.70"
        :kind :blend-strength
        :writing-a pa
        :blend-strength 0.70}
       {:group "dial2"
        :tag "dial2-async025"
        :name "PA, asynchronous fraction 0.25"
        :kind :async-refuges
        :writing-a pa
        :update-fraction 0.25}
       {:group "dial2"
        :tag "dial2-async050"
        :name "PA, asynchronous fraction 0.50"
        :kind :async-refuges
        :writing-a pa
        :update-fraction 0.50}
       {:group "dial2"
        :tag "dial2-async075"
        :name "PA, asynchronous fraction 0.75"
        :kind :async-refuges
        :writing-a pa
        :update-fraction 0.75}
       {:group "dial2"
        :tag "dial2-niches16"
        :name "PA / two-4-cycle niches, width 16"
        :kind :static-niches
        :writing-a pa
        :writing-b two-4-cycle
        :patch-width 16}
       {:group "dial2"
        :tag "dial2-niches64"
        :name "PA / two-4-cycle niches, width 64"
        :kind :static-niches
        :writing-a pa
        :writing-b two-4-cycle
        :patch-width 64}

       {:group "dial3"
        :tag "dial3-braid-blend070"
        :name "braid PA / two-4-cycle, blend 0.70"
        :kind :blend-strength
        :writing-a pa
        :writing-b two-4-cycle
        :blend-strength 0.70}
       {:group "dial3"
        :tag "dial3-braid-async075"
        :name "braid PA / two-4-cycle, async 0.75"
        :kind :async-refuges
        :writing-a pa
        :writing-b two-4-cycle
        :update-fraction 0.75}
       {:group "dial3"
        :tag "dial3-phase-niches64"
        :name "phase-shifted braid niches, width 64"
        :kind :phase-niches
        :writing-a pa
        :writing-b two-4-cycle
        :patch-width 64}]]
  (.mkdirs (io/file "data"))
  (.mkdirs (io/file "figures"))
  (let [rows
        (for [config configs
              :let [run (run-config config seed width steps)
                    stats (diversity-stats (peek (:gen run)))
                    path (format "data/diversity_sampler_%s.txt"
                                 (:tag config))]]
          (do
            (write-run! path run)
            [(:group config)
             (:tag config)
             (:name config)
             (:rules stats)
             (format "%.2f" (:effective-rules stats))]))]
    (spit "data/diversity_sampler_manifest.tsv"
          (str "group\ttag\tname\trules\teffective-rules\n"
               (str/join "\n" (map #(str/join "\t" %) rows))
               "\n"))
    (println (format "wrote %d diversity sampler fields and manifest"
                     (count rows)))))
