;; A mutation-free, non-freezing diversity dial.
;;
;; The genotype field is initialized from a prescribed empirical measure and
;; subsequently evolves only through local bijective swaps.  Thus its rule
;; histogram (and therefore its Shannon diversity) is invariant, while rules
;; remain mobile.  Swap probabilities depend on the local phenotype, allowing
;; a phenotype perturbation to alter subsequent conservative genotype flow.
(require '[mmca.core :as c]
         '[mmca.rng :as rng])

(defn clone-rng [r]
  (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))

(defn shuffled
  [values ^java.util.Random random]
  (loop [result (vec values)
         i (dec (count result))]
    (if (<= i 0)
      result
      (let [j (.nextInt random (inc i))]
        (recur (assoc result
                      i (nth result j)
                      j (nth result i))
               (dec i))))))

(defn prescribed-population
  "A seed-specific nested rule ensemble, balanced as evenly as width permits."
  [seed width support]
  (let [rule-order (shuffled (range c/rule-count)
                             (java.util.Random. (+ 41000 seed)))
        selected (take support rule-order)
        population (take width (cycle selected))]
    (shuffled population (java.util.Random. (+ 51000 seed support)))))

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

(defn phenotype-bit [phenotype i]
  (Character/digit (nth phenotype i) 2))

(defn swap-probability
  "Active background transport plus enhanced transport at phenotype interfaces.

  The rate is a transport parameter, not a hold probability: even homogeneous
  phenotype pairs exchange rules at half-rate.  Interface pairs exchange at
  1.5 times the rate (capped at one), coupling conservative genotype motion to
  phenotype structure."
  [rate left-bit right-bit]
  (min 1.0 (* rate (if (= left-bit right-bit) 0.5 1.5))))

(defn conservative-step
  "One Margolus-style layer of disjoint, phenotype-gated rule swaps."
  [transport-r genotype phenotype rate t]
  (let [width (count genotype)
        start (mod t 2)
        before (frequencies genotype)
        result
        (reduce
         (fn [{:keys [genotype swaps]} i]
           (let [j (inc i)
                 probability (swap-probability
                              rate
                              (phenotype-bit phenotype i)
                              (phenotype-bit phenotype j))
                 swap? (< (rng/rand-double transport-r) probability)]
             (if swap?
               {:genotype (assoc genotype
                                 i (nth genotype j)
                                 j (nth genotype i))
                :swaps (inc swaps)}
               {:genotype genotype :swaps swaps})))
         {:genotype genotype :swaps 0}
         (range start (dec width) 2))]
    (when-not (= before (frequencies (:genotype result)))
      (throw (ex-info "Conservative transport changed the genotype measure"
                      {:time t :rate rate})))
    result))

(defn nearest-different-site [genotype site]
  (let [width (count genotype)
        rule (nth genotype site)]
    (or (some (fn [distance]
                (let [candidate (mod (+ site distance) width)]
                  (when (not= rule (nth genotype candidate))
                    candidate)))
              (range 1 width))
        (throw (ex-info "Perturbation requires at least two rules"
                        {:site site})))))

(defn measure-preserving-perturbation
  "Transpose the focal rule with the nearest different rule."
  [genotype site]
  (let [other (nearest-different-site genotype site)]
    {:genotype (assoc genotype
                      site (nth genotype other)
                      other (nth genotype site))
     :other-site other}))

(defn paired-response
  [transport-r genotype phenotype rate fork-time horizon site]
  (let [transport-a (clone-rng transport-r)
        transport-b (clone-rng transport-r)
        {perturbed-genotype :genotype
         other-site :other-site}
        (measure-preserving-perturbation genotype site)
        reference-measure (frequencies genotype)]
    (when-not (= reference-measure (frequencies perturbed-genotype))
      (throw (ex-info "Perturbation changed the genotype measure"
                      {:site site :other-site other-site})))
    (loop [dt 0
           genotype-a genotype
           phenotype-a phenotype
           genotype-b perturbed-genotype
           phenotype-b phenotype
           dG-area 0
           dG-peak 2
           dP-area 0
           dP-peak 0
           swaps 0]
      (if (= dt horizon)
        (merge {:perturbation-span
                (min (mod (- other-site site) (count genotype))
                     (mod (- site other-site) (count genotype)))
                :dG-final (c/changed-count genotype-a genotype-b)
                :dG-peak dG-peak
                :dG-area dG-area
                :dP-final (c/changed-count phenotype-a phenotype-b)
                :dP-peak dP-peak
                :dP-area dP-area
                :mean-swaps (/ swaps (double (* 2 horizon)))}
               (diversity-stats genotype-a))
        (let [t (+ fork-time dt)
              step-a (conservative-step transport-a genotype-a
                                        phenotype-a rate t)
              step-b (conservative-step transport-b genotype-b
                                        phenotype-b rate t)
              next-genotype-a (:genotype step-a)
              next-genotype-b (:genotype step-b)
              next-phenotype-a (c/phenotype-step genotype-a phenotype-a)
              next-phenotype-b (c/phenotype-step genotype-b phenotype-b)
              dG (c/changed-count next-genotype-a next-genotype-b)
              dP (c/changed-count next-phenotype-a next-phenotype-b)]
          (when-not (and (= reference-measure
                            (frequencies next-genotype-a))
                         (= reference-measure
                            (frequencies next-genotype-b)))
            (throw (ex-info "Paired branch escaped its prescribed measure"
                            {:time t :rate rate :site site})))
          (recur (inc dt)
                 next-genotype-a
                 next-phenotype-a
                 next-genotype-b
                 next-phenotype-b
                 (+ dG-area dG)
                 (max dG-peak dG)
                 (+ dP-area dP)
                 (max dP-peak dP)
                 (+ swaps (:swaps step-a) (:swaps step-b))))))))

(defn probe
  [seed width support rate fork-time horizon sites]
  (let [phenotype-r (rng/make-rng (format "dial4-phenotype-%d" seed))
        transport-r (rng/make-rng
                     (format "dial4-transport-%d-%d"
                             seed (long (* 100 rate))))
        genotype-0 (prescribed-population seed width support)
        phenotype-0 (c/random-phenotype phenotype-r width)
        reference-measure (frequencies genotype-0)]
    (loop [t 0
           genotype genotype-0
           phenotype phenotype-0]
      (if (= t fork-time)
        {:at-fork (diversity-stats genotype)
         :responses
         (mapv (fn [site]
                 (paired-response transport-r genotype phenotype rate
                                  fork-time horizon site))
               sites)}
        (let [{next-genotype :genotype}
              (conservative-step transport-r genotype phenotype rate t)]
          (when-not (= reference-measure (frequencies next-genotype))
            (throw (ex-info "Warm-up escaped its prescribed measure"
                            {:time t :support support :rate rate})))
          (recur (inc t)
                 next-genotype
                 (c/phenotype-step genotype phenotype)))))))

(defn option-value [option]
  (some (fn [[key value]]
          (when (= key option) value))
        (partition 2 *command-line-args*)))

(defn parse-list [value parse-value defaults]
  (if value
    (mapv parse-value (.split ^String value ","))
    defaults))

(let [width 256
      fork-time 30
      horizon 40
      supports (parse-list (option-value "--supports")
                           #(Integer/parseInt %)
                           [32 64 96 128 160 192 224 256])
      rates (parse-list (option-value "--rates")
                        #(Double/parseDouble %)
                        [0.0 0.25 0.5 0.75 1.0])
      seeds (parse-list (option-value "--seeds")
                        #(Integer/parseInt %)
                        (range 6))
      sites (range 0 width 32)]
  (println
   (str "support\ttransport-rate\tseed\trules-fork\teffective-fork"
        "\trules-end\teffective-end\tperturbation-span"
        "\tdG-final\tdG-peak\tdG-area\tdP-final\tdP-peak\tdP-area"
        "\tmean-swaps"))
  (doseq [support supports
          rate rates
          seed seeds]
    (let [{:keys [at-fork responses]}
          (probe seed width support rate fork-time horizon sites)]
      (doseq [response responses]
        (println
         (format (str "%d\t%.2f\t%d\t%d\t%.2f\t%d\t%.2f\t%d"
                      "\t%d\t%d\t%d\t%d\t%d\t%d\t%.2f")
                 support rate seed
                 (:rules at-fork)
                 (:effective-rules at-fork)
                 (:rules response)
                 (:effective-rules response)
                 (:perturbation-span response)
                 (:dG-final response)
                 (:dG-peak response)
                 (:dG-area response)
                 (:dP-final response)
                 (:dP-peak response)
                 (:dP-area response)
                 (:mean-swaps response)))))))
