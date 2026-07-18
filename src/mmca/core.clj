(ns mmca.core
  "Standalone Clojure port of the paper's Tier-1 MetaCA engine.

  A field cell carries an ECA rule byte. Phenotype evolution is feedforward
  (G->X and X->X); genotype evolution blends neighbouring truth-table bits and
  then applies one propagator write. `run-river` is the separately named
  phenotype-reading composition used for Figure 6."
  (:require [mmca.rng :as rng]))

(def default-rule 0)
(def rule-count 256)
(def bit-count 8)
(def wolfram-neighbourhoods
  ["111" "110" "101" "100" "011" "010" "001" "000"])
(def legacy-neighbourhoods
  ["000" "001" "010" "100" "011" "101" "110" "111"])

(defn positional-writing->neighbourhood-writing
  "Re-express a writing defined in 2014 positions in Wolfram positions.

  This is the ordering-independent `positional-sigma->neighbourhood-sigma`
  shim: current-index -> neighbourhood -> legacy-index -> legacy destination
  neighbourhood -> current destination index."
  [positional-writing]
  {:pre [(= bit-count (count positional-writing))
         (every? #(<= 0 % 7) positional-writing)]}
  (let [legacy-index (zipmap legacy-neighbourhoods (range bit-count))
        wolfram-index (zipmap wolfram-neighbourhoods (range bit-count))]
    (mapv (fn [current-index]
            (let [neighbourhood (nth wolfram-neighbourhoods current-index)
                  old-source (legacy-index neighbourhood)
                  old-destination (nth positional-writing old-source)
                  destination-neighbourhood
                  (nth legacy-neighbourhoods old-destination)]
              (wolfram-index destination-neighbourhood)))
          (range bit-count))))

(defn rule-bits
  "Rule byte as an MSB-first vector, matching the legacy engine."
  [rule]
  {:pre [(<= 0 rule 255)]}
  (mapv #(if (bit-test rule %) 1 0) (range 7 -1 -1)))

(defn bits-rule [bits]
  {:pre [(= bit-count (count bits))
         (every? #{0 1} bits)]}
  (reduce (fn [n bit] (+ (bit-shift-left n 1) bit)) 0 bits))

(defn rule-output
  "Standard Wolfram ECA lookup for neighbourhood [left centre right]."
  [rule left centre right]
  (if (bit-test rule (+ (* 4 left) (* 2 centre) right)) 1 0))

(def ^:private legacy-low-order [0 1 2 4 3 5 6 7])

(defn legacy-index->rule
  "The 2014 truth-table-8 entry at index i.

  Its table swaps entries 3 and 4 in each block of eight. Random initialization
  selects table entries, so retaining this ordering is required for exact runs."
  [i]
  (+ (* 8 (quot i 8)) (legacy-low-order (mod i 8))))

(defn random-genotype [r width]
  (mapv (fn [_] (legacy-index->rule (rng/rand-int r rule-count)))
        (range width)))

(defn random-phenotype [r width]
  (apply str (map (fn [_] (char (+ (int \0) (rng/rand-int r 2))))
                  (range width))))

(defn phenotype-step
  "Evolve phenotype against the current per-cell rules with zero boundaries."
  [genotype phenotype]
  {:pre [(= (count genotype) (count phenotype))]}
  (let [width (count phenotype)]
    (apply str
           (for [i (range width)]
             (let [left (if (zero? i) 0 (Character/digit (nth phenotype (dec i)) 2))
                   centre (Character/digit (nth phenotype i) 2)
                   right (if (= i (dec width)) 0
                             (Character/digit (nth phenotype (inc i)) 2))]
               (char (+ (int \0) (rule-output (nth genotype i)
                                              left centre right))))))))

(defn blend-rule
  "Neighbour-agreement blend from the 2014 engine, before propagation."
  [predecessor centre successor]
  (let [pb (rule-bits predecessor)
        cb (rule-bits centre)
        sb (rule-bits successor)]
    (bits-rule
     (mapv (fn [left middle right]
             (if (= left right)
               left
               (rule-output centre left middle right)))
           pb cb sb))))

(defn propagate-at
  "Apply bit[writing[source]] := not bit[source] at an explicit source index."
  ([rule writing source]
   (propagate-at rule writing source true))
  ([rule writing source invert?]
   {:pre [(= bit-count (count writing))
          (<= 0 source 7)
          (every? #(<= 0 % 7) writing)]}
   (let [bits (rule-bits rule)
         value (nth bits source)
         value (if invert? (bit-xor value 1) value)]
     (bits-rule (assoc bits (nth writing source) value)))))

(defn propagate
  ([r rule writing] (propagate r rule writing true))
  ([r rule writing invert?]
   (propagate-at rule writing (rng/rand-int r bit-count) invert?)))

(defn genotype-step
  "Blend the rule field with zero-rule boundaries, then propagate once/cell.

  The legacy engine evaluates head, then tail, then interior cells. This
  observable RNG order is retained deliberately."
  ([r genotype writing] (genotype-step r genotype writing true))
  ([r genotype writing invert?]
   (let [width (count genotype)
         evaluation-order (concat [0 (dec width)] (range 1 (dec width)))]
     (reduce (fn [out i]
               (let [predecessor (if (zero? i) default-rule
                                     (nth genotype (dec i)))
                     centre (nth genotype i)
                     successor (if (= i (dec width)) default-rule
                                   (nth genotype (inc i)))]
                 (assoc out i
                        (propagate r
                                   (blend-rule predecessor centre successor)
                                   writing invert?))))
             (vec (repeat width nil))
             evaluation-order))))

(defn genotype-step-interrupted
  "One feedforward genotype step with continuous interrupter strength q.

  For each cell, q is the probability of applying the propagator write to the
  neighbour-agreement blend; otherwise the blended rule is held. A separate
  interrupter RNG decides apply/hold, while the propagator RNG consumes one
  source position per cell regardless of the decision. Thus q changes only the
  intervention, not the source-position tape. This function never reads X."
  ([propagator-r interrupter-r genotype writing q]
   (genotype-step-interrupted propagator-r interrupter-r genotype writing q true))
  ([propagator-r interrupter-r genotype writing q invert?]
   {:pre [(number? q) (<= 0.0 (double q) 1.0)]}
   (let [width (count genotype)
         evaluation-order (concat [0 (dec width)] (range 1 (dec width)))]
     (reduce
      (fn [out i]
        (let [predecessor (if (zero? i) default-rule
                              (nth genotype (dec i)))
              centre (nth genotype i)
              successor (if (= i (dec width)) default-rule
                            (nth genotype (inc i)))
              blended (blend-rule predecessor centre successor)
              source (rng/rand-int propagator-r bit-count)
              apply? (< (rng/rand-double interrupter-r) (double q))]
          (assoc out i (if apply?
                         (propagate-at blended writing source invert?)
                         blended))))
      (vec (repeat width nil))
      evaluation-order))))

(defn genotype-step-alone
  ([r genotype writing] (genotype-step-alone r genotype writing true))
  ([r genotype writing invert?]
   (mapv #(propagate r % writing invert?) genotype)))

(defn changed-count [a b]
  (count (remove true? (map = a b))))

(defn distinct-rules [genotype]
  (count (distinct genotype)))

(defn- finish-run [phenotypes genotypes activities]
  {:death (reduce-kv (fn [last-active i activity]
                       (if (pos? activity) (inc i) last-active))
                     0
                     activities)
   :rules (distinct-rules (peek genotypes))
   :activity (reduce + 0 activities)
   :phe phenotypes
   :gen genotypes})

(defn run-propagator
  "Run the blending/interrupter Tier-1 dynamics.

  Returns `:gen` as rows of integer rule bytes and `:phe` as binary strings,
  including the initial row. Same writing+seed+width+steps is deterministic and
  matches the GNU Emacs 30/Linux Tier-1 stream."
  ([writing seed width steps]
   (run-propagator writing seed width steps {}))
  ([writing seed width steps {:keys [invert? interrupter-q]
                              :or {invert? true interrupter-q 1.0}}]
   (let [writing (positional-writing->neighbourhood-writing writing)
         r (rng/make-rng (format "prop-%d" seed))
         interrupter-r (when-not (= 1.0 (double interrupter-q))
                         (rng/make-rng (format "interrupt-%d" seed)))
         g0 (random-genotype r width)
         p0 (random-phenotype r width)]
     (loop [t 0
            genotype g0
            phenotype p0
            genotypes [g0]
            phenotypes [p0]
            activities []]
       (if (= t steps)
         (finish-run phenotypes genotypes activities)
         (let [next-phenotype (phenotype-step genotype phenotype)
               next-genotype
               (if (= 1.0 (double interrupter-q))
                 (genotype-step r genotype writing invert?)
                 (genotype-step-interrupted r interrupter-r genotype writing
                                            interrupter-q invert?))]
           (recur (inc t) next-genotype next-phenotype
                  (conj genotypes next-genotype)
                  (conj phenotypes next-phenotype)
                  (conj activities (changed-count phenotype next-phenotype)))))))))

(defn run-propagator-alone
  "Run one independent propagator write per genotype cell, without blending."
  ([writing seed width steps]
   (run-propagator-alone writing seed width steps {}))
  ([writing seed width steps {:keys [invert?] :or {invert? true}}]
   (let [writing (positional-writing->neighbourhood-writing writing)
         r (rng/make-rng (format "prop-%d" seed))
         g0 (random-genotype r width)
         p0 (random-phenotype r width)]
     (loop [t 0 genotype g0 phenotype p0 genotypes [g0] phenotypes [p0]
            activities []]
       (if (= t steps)
         (finish-run phenotypes genotypes activities)
         (let [next-phenotype (phenotype-step genotype phenotype)
               next-genotype (genotype-step-alone r genotype writing invert?)]
           (recur (inc t) next-genotype next-phenotype
                  (conj genotypes next-genotype)
                  (conj phenotypes next-phenotype)
                  (conj activities (changed-count phenotype next-phenotype)))))))))

(defn river-templates [a b c d]
  [[[a b c] d]
   [[(bit-xor a 1) (bit-xor b 1) (bit-xor c 1)] (bit-xor d 1)]
   [[a (bit-xor b 1) c] d]
   [[(bit-xor a 1) b (bit-xor c 1)] (bit-xor d 1)]])

(defn river-combine-rule
  "Phenotype-reading template construction used before Figure 6's rot+2 write."
  [predecessor centre successor context]
  (let [pb (rule-bits predecessor)
        cb (rule-bits centre)
        sb (rule-bits successor)
        templates (when context (apply river-templates context))]
    (bits-rule
     (mapv (fn [left middle right]
             (or (some (fn [[parent result]]
                         (when (= parent [left middle right]) result))
                       templates)
                 (rule-output centre left middle right)))
           pb cb sb))))

(defn original-river-combine-rule
  "Original Figure-6 `quad-4cand / firstMatch` construction.

  Its complete typed coordinate is context-quadruple-four-candidates,
  first-template-match-else-fallback, constant-zero, eight-bits-to-rule-byte.
  The later reconstruction used the centre rule as fallback instead."
  [predecessor centre successor context]
  (let [pb (rule-bits predecessor)
        cb (rule-bits centre)
        sb (rule-bits successor)
        templates (when context (apply river-templates context))]
    (bits-rule
     (mapv (fn [left middle right]
             (or (some (fn [[parent result]]
                         (when (= parent [left middle right]) result))
                       templates)
                 0))
           pb cb sb))))

(def river-writing
  (positional-writing->neighbourhood-writing [2 3 4 5 6 7 0 1]))

(defn river-genotype-step [r genotype phenotype next-phenotype]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       (let [predecessor (if (zero? i) default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth phenotype (dec i)) 2)
                        (Character/digit (nth phenotype i) 2)
                        (Character/digit (nth phenotype (inc i)) 2)
                        (Character/digit (nth next-phenotype i) 2)])]
         (propagate r (river-combine-rule predecessor centre successor context)
                    river-writing)))
     (range width))))

(defn run-river-reconstruction
  "DEPRECATED reconstruction (centre-rule local fallback, Emacs-seed). This is
  NOT the paper river -- use `run-river`, the authentic constant-zero/Java-seed
  Figure 6 system. Kept only for provenance; do not use it in experiments or
  figures (that mistake is what the correction round fixed)."
  [seed width steps]
  (let [r (rng/make-rng (format "prop-%d" seed))
        g0 (random-genotype r width)
        p0 (random-phenotype r width)]
    (loop [t 0 genotype g0 phenotype p0 genotypes [g0] phenotypes [p0]
           activities []]
      (if (= t steps)
        (finish-run phenotypes genotypes activities)
        (let [next-phenotype (phenotype-step genotype phenotype)
              next-genotype (river-genotype-step r genotype phenotype next-phenotype)]
          (recur (inc t) next-genotype next-phenotype
                 (conj genotypes next-genotype)
                 (conj phenotypes next-phenotype)
                 (conj activities (changed-count phenotype next-phenotype))))))))

(defn java-random-genotype
  "Generate a width-length genotype by drawing rule indices from a
  `java.util.Random`. Public so experiments can reproduce the river's exact
  initial-state construction for injection (see `run-river-from`)."
  [^java.util.Random random width]
  (mapv (fn [_] (.nextInt random rule-count)) (range width)))

(defn java-random-phenotype
  "Generate a width-length binary phenotype string by drawing bits from a
  `java.util.Random`. Public so experiments can reproduce the river's exact
  initial-state construction for injection (see `run-river-from`)."
  [^java.util.Random random width]
  (apply str
         (map (fn [_] (if (< (.nextDouble random) 0.5) \1 \0))
              (range width))))

(defn- original-paper-river-genotype-step
  [^java.util.Random random genotype phenotype next-phenotype]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       (let [predecessor (if (zero? i) default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth phenotype (dec i)) 2)
                        (Character/digit (nth phenotype i) 2)
                        (Character/digit (nth phenotype (inc i)) 2)
                        (Character/digit (nth next-phenotype i) 2)])]
         (propagate-at
          (original-river-combine-rule predecessor centre successor context)
          river-writing
          (.nextInt random bit-count))))
     (range width))))

(defn run-river-from
  "The iteration body of `run-river`, factored so experiments can inject into
  the initial `genotype`/`phenotype` before running. `random` must already be
  consumed past initialization (i.e. produced by the same construction as
  `run-river`'s seed); the per-step RNG draws then proceed identically. This
  preserves the matched-control guarantee: `run-river-from` and
  `run-river-ablated-from` started from the same injected state share the exact
  tape, construction, and fallback -- only the live-vs-frozen phenotype read
  differs."
  [^java.util.Random random genotype phenotype steps]
  (loop [t 0 genotype genotype phenotype phenotype
         genotypes [genotype] phenotypes [phenotype] activities []]
    (if (= t steps)
      (finish-run phenotypes genotypes activities)
      (let [next-phenotype (phenotype-step genotype phenotype)
            next-genotype
            (original-paper-river-genotype-step
             random genotype phenotype next-phenotype)]
        (recur (inc t) next-genotype next-phenotype
               (conj genotypes next-genotype)
               (conj phenotypes next-phenotype)
               (conj activities
                     (changed-count phenotype next-phenotype)))))))

(defn run-river
  "Replay original Figure 6: `quad-4cand / firstMatch prop:rot2`.

  The no-match fallback is constant zero. One `java.util.Random`, seeded by
  the literal integer, supplies numeric rule initialization, `nextDouble`
  phenotype bits, and propagator source positions. The ordering-independent
  shim converts positional rot2 before the run. This is deliberately separate
  from `run-river-reconstruction`, the later centre-rule/Emacs-seed reconstruction."
  [seed width steps]
  (let [random (java.util.Random. (long seed))
        g0 (java-random-genotype random width)
        p0 (java-random-phenotype random width)]
    (run-river-from random g0 p0 steps)))

(defn run-river-ablated-from
  "The iteration body of `run-river-ablated`, factored for injection (see
  `run-river-from`). Shares the river's exact RNG tape and construction; only
  the genotype step reads the FROZEN `phenotype` (the initial, injected state)
  instead of the live one."
  [^java.util.Random random genotype phenotype steps]
  (loop [t 0 genotype genotype phenotype phenotype
         genotypes [genotype] phenotypes [phenotype] activities []]
    (if (= t steps)
      (finish-run phenotypes genotypes activities)
      (let [next-phenotype (phenotype-step genotype phenotype)
            next-genotype
            (original-paper-river-genotype-step
             random genotype phenotype phenotype)]
        (recur (inc t) next-genotype next-phenotype
               (conj genotypes next-genotype)
               (conj phenotypes next-phenotype)
               (conj activities
                     (changed-count phenotype next-phenotype)))))))

(defn run-river-ablated
  "Matched feedback-OFF control for `run-river`: identical Java
  seed, initial state, RNG tape, and constant-zero quad-4cand construction --
  but the genotype step reads the FROZEN initial phenotype p0 for its template
  context instead of the live (evolving) phenotype. The phenotype still evolves
  as a G->X readout; only the dynamic X->G edge is cut. Because the context does
  not consume the RNG and the per-step draw count is genotype-independent, this
  shares the river's exact tape. Differencing the two genotype trajectories from
  the same seed isolates the causal effect of the live phenotype->genotype
  feedback; everything else (tape, construction, fallback, boundaries) is held
  identical. (Nil context is NOT used: the constant-zero fallback would collapse
  every cell to rule 0, a degenerate control.)"
  [seed width steps]
  (let [random (java.util.Random. (long seed))
        g0 (java-random-genotype random width)
        p0 (java-random-phenotype random width)]
    (run-river-ablated-from random g0 p0 steps)))
