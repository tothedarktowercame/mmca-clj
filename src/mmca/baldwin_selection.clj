;; A selection loop over (gamma, initial genotype field), scored by the paper's
;; own causal-reach protocol with an explicit cost on plasticity.
;;
;; This LAYERS on the published work rather than reimplementing it:
;;   * the same protocol constants as every other row -- L=80, T=120, t*=60, dt=59;
;;   * the same river update, `c/original-river-combine-rule` + `c/river-writing`,
;;     read through the same gain gate as `scripts/river_gain.clj`;
;;   * the same two-sided calibration -- ordered below rule 90 (8.00), complex to
;;     22, chaotic beyond (rule 30 = 36.45);
;;   * the same frozen-field discipline: the stale reference is supplied from
;;     OUTSIDE and is identical in both fork branches, so the gate cannot itself
;;     carry the perturbation. That is the correctness fix river_gain.clj records.
;;
;; What is new is only the population: gamma and the initial genotype field are
;; heritable, fitness charges for gamma, and selection runs over generations.
;; The Baldwin question is whether gamma rises and then FALLS while score holds.
;; See futon5/holes/tech-notes/TN-part-III-b-baldwin-recovery.md for the
;; preregistered criteria; they were fixed before this script existed.

(ns mmca.baldwin-selection
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [mmca.baldwin-spec :as spec]
            [mmca.core :as c]))

(def W 80) (def STEPS 120) (def TSTAR 60) (def DT 59)
;; CALIBRATION, measured in THIS experiment's own frame (same reach fn, same
;; protocol, same p0 construction, update-prob 0 so the field is fixed and the run
;; is a pure CA on that rule):
;;
;;   rule 0    0.0000     rule 90   10.0000
;;   rule 204  1.0000     rule 110  16.6000
;;   rule 54   6.0333     rule 30   18.7000
;;
;; The previous bands [8, 22] were imported from a calibration run with PERIODIC
;; boundaries (`eca-row`), while every construction here evolves with ZERO
;; boundaries (`c/phenotype-step`). That made "high function" ill-defined: rule 30
;; measured 18.4 under the constructions' own dynamics, inside the supposed complex
;; band, so a calibration-chaotic endpoint could score as successful.
;;
;; Anchored on rule 90 (onset of complexity) and rule 30 (chaos), which are the
;; canonical endpoints. NOTE, and this is not a rescaling: the rules REORDER between
;; frames. Rule 54 is 18.30 periodic but 6.03 here, below rule 90 rather than above
;; it, so it does not behave as a complex rule under these dynamics. Anchoring on 54
;; would give a different and less defensible band.
(def BAND-LOW 10.0)      ;; rule 90, in-frame
(def BAND-HIGH 18.7)     ;; rule 30, in-frame
(def BAND-CENTRE (/ (+ BAND-LOW BAND-HIGH) 2.0))
(def BAND-HALF (/ (- BAND-HIGH BAND-LOW) 2.0))
;; Gene resolution must sit where the function VARIES. Eight uniform levels put
;; seven in the dead zone below the band and one at the top, giving a profile with
;; a single cliff that no hill-climber can descend -- the preflight rejects it, and
;; correctly. Measured in-frame, reach is graded across 0.875..1.0 (7.60, 8.90,
;; 8.63, 11.43, 12.07) rather than discontinuous, so the region merely needed
;; resolution. Levels are therefore dense near 1 where the gain curve is convex.
(def GAMMA-LEVELS [0.0 0.5 0.75 0.875 0.9 0.95 0.99 1.0])
;; G5: zero lets a lineage stop rewriting entirely and become a FIXED field, which
;; is the blind destination -- fixed rules 90/110/54 sit in the complex band at
;; 8.00/16.68/18.30. Without a reachable zero, the gamma=0 organism is only a blind
;; REWRITER and assimilation has nowhere to land.
(def UPDATE-LEVELS [0.0 0.25 0.5 0.75 1.0])

;; PER-CELL PLASTICITY. A global gamma is all-or-nothing across the lattice, so a
;; partially assimilated genome cannot exist and selection has no gradient to climb.
;; Hinton & Nowlan's plasticity is PER POSITION -- each locus independently `?` or
;; fixed -- which is what makes partial assimilation pay there. `:mask` is the
;; analogue: true = this cell may read the live phenotype, false = it always reads
;; the frozen one. About 27% of cell-steps are indifferent to the read (the live
;; context selects a different rule in 73.1%), so that fraction is assimilable at
;; no cost -- exactly H&N's loci where the `?` did not matter.
;; All-true reduces exactly to the scalar-gamma behaviour.

;; --- the gain gate, identical in form to river_gain.clj -----------------------
(defn gain-genotype-step
  [^java.util.Random random ^java.util.Random gate ^java.util.Random upd
   genotype phenotype next-phenotype frozen gamma update-prob mask hold]
  (let [width (count genotype)]
    (mapv
     (fn [i]
       ;; Every coin and every source draw is taken for EVERY cell at every step,
       ;; whatever is then decided with them, so the two damage branches stay
       ;; tape-aligned. The update coin comes from a THIRD stream so that adding
       ;; G5 leaves the gate and source tapes bit-identical to the published dial;
       ;; at update-prob = 1 this reduces exactly to river_gain.clj.
       (let [ucoin (.nextDouble upd)
             ;; coin drawn unconditionally either way; the mask only decides whether
             ;; its outcome is allowed to select the live read
             coin (.nextDouble gate)
             live? (and (nth mask i) (< coin gamma))
             ph (if live? phenotype frozen)
             nph (if live? next-phenotype frozen)
             predecessor (if (zero? i) c/default-rule (nth genotype (dec i)))
             centre (nth genotype i)
             successor (if (= i (dec width)) c/default-rule (nth genotype (inc i)))
             context (when (and (pos? i) (< i (dec width)))
                       [(Character/digit (nth ph (dec i)) 2)
                        (Character/digit (nth ph i) 2)
                        (Character/digit (nth ph (inc i)) 2)
                        (Character/digit (nth nph i) 2)])
             ;; source drawn unconditionally, used only when the cell rewrites
             source (.nextInt random c/bit-count)]
         ;; A HELD cell never rewrites: it keeps its inherited rule, which is the
         ;; same 8-bit string in every universe the genome is evaluated in. That is
         ;; the Hinton & Nowlan fixed locus. The earlier :mask only chose which
         ;; phenotype was READ, so a non-plastic cell still churned its rule from
         ;; stale context and was never fixed at all.
         (if (and (< ucoin update-prob) (not (nth hold i)))
           (c/propagate-at
            (c/original-river-combine-rule predecessor centre successor context)
            c/river-writing
            source)
           centre)))                          ; G5: hold the rule -- no rewrite
     (range width))))

(defn run-from [random gate upd genotype phenotype steps gamma update-prob mask hold frozen]
  (loop [t 0 g genotype p phenotype phes [phenotype]]
    (if (= t steps) {:phe phes :gen g}
      (let [np (c/phenotype-step g p)
            ng (gain-genotype-step random gate upd g p np frozen gamma update-prob mask hold)]
        (recur (inc t) ng np (conj phes np))))))

;; `g0` is INJECTED rather than derived from the seed: that is what makes the
;; initial field heritable, and hence what gives assimilation somewhere to
;; accumulate. Everything else matches river_gain.clj/two-stage.
(defn two-stage [gamma update-prob mask hold seed g0 intervene frozen*]
  (let [r (java.util.Random. (long seed))
        gate (java.util.Random. (long (+ 987654321 seed)))
        upd (java.util.Random. (long (+ 123456789 seed)))
        ;; river_gain.clj draws the genotype from `r` BEFORE the phenotype. We
        ;; inject the genotype instead, so `r` must still be advanced by exactly
        ;; those draws or `p0` diverges from the published initial condition and
        ;; the numbers stop being comparable to the paper's rows.
        _ (c/java-random-genotype r W)
        p0 (c/java-random-phenotype r W)
        a (run-from r gate upd g0 p0 TSTAR gamma update-prob mask hold (or frozen* p0))
        g* (:gen a) p* (peek (:phe a))
        [g' p'] (if intervene (intervene g* p*) [g* p*])
        b (run-from r gate upd g' p' (- STEPS TSTAR) gamma update-prob mask hold (or frozen* p0))]
    {:phe (into (:phe a) (rest (:phe b)))}))

(defn flip-at [x] (fn [g p] [g (str (subs p 0 x) (if (= \1 (nth p x)) \0 \1) (subs p (inc x)))]))

;; --- reach, at the published protocol ---------------------------------------
(defn reach [{:keys [gamma update-prob field mask hold]} seeds sites]
  (let [ms (for [seed seeds]
             (let [up (if (nil? update-prob) 1.0 update-prob)
                   mk (or mask (vec (repeat W true)))
                   hd (or hold (vec (repeat W false)))
                   ref (two-stage 1.0 up mk hd seed field nil nil)
                   frozen* (nth (:phe ref) TSTAR)
                   A (two-stage gamma up mk hd seed field nil frozen*)]
               (for [x sites]
                 (let [B (two-stage gamma up mk hd seed field (flip-at x) frozen*)]
                   (reduce + (map #(if (= %1 %2) 0 1)
                                  (nth (:phe A) (+ TSTAR DT))
                                  (nth (:phe B) (+ TSTAR DT))))))))
        all (mapv double (flatten ms))]
    {:mean (/ (reduce + all) (count all)) :n (count all)}))

;; two-sided: peaks in the complex band, penalising stasis AND saturation, so
;; the population cannot win by evolving toward rule-30 behaviour
(defn band-score [r]
  (max 0.0 (- 1.0 (/ (Math/abs (- (double r) BAND-CENTRE)) BAND-HALF))))

(defn plastic-dependence
  "Lean: `ExperimentalDesign.plasticDependence`. ONE operational definition, used
   for BOTH the reported column and the cost term.

   A cell contributes plastic dependence exactly when it is not held (so it can
   rewrite), it actually rewrites (probability `update-prob`), and its rewrite
   reads the CURRENT phenotype rather than the frozen snapshot (`mask` and
   `gamma`). Hence

     dependence = update-prob * gamma * fraction of cells that are unheld and unmasked

   Previously `fraction-not-held` was reported while `c * gamma * fraction` was
   charged, and neither accounted for `update-prob` or `mask`, so the reported
   quantity and the selected quantity were different things and neither
   corresponded to the Lean field."
  [{:keys [gamma update-prob mask hold]}]
  (let [n (count (or hold []))
        live (if (zero? n)
               1.0
               (/ (count (filter identity
                                 (map (fn [h m] (and (not h) m))
                                      hold (or mask (repeat n true)))))
                  (double n)))]
    (* (double (or update-prob 1.0)) (double (or gamma 0.0)) live)))

;; retained name for the reporting column, now identical to what is charged
(def plastic-fraction plastic-dependence)

(defn decode-record-genome
  "Convert the compact 0/1 record representation back to an executable genome."
  [record]
  (-> record
      (update :field vec)
      (update :mask #(mapv (partial = 1) %))
      (update :hold #(mapv (partial = 1) %))))

(defn best-genome-from-record
  "Recover the highest raw-band genome from an experiment record. Cost-adjusted
   score is deliberately not used: mechanistic probes ask what function evolution
   built, independently of the treatment cost."
  [path]
  (let [best (->> (str/split-lines (slurp path))
                  (remove str/blank?)
                  (map edn/read-string)
                  (remove :kind)
                  (sort-by #(or (:band %) -1.0) >)
                  first)]
    (when-not best
      (throw (ex-info "record contains no genomes" {:path path})))
    (select-keys (decode-record-genome best)
                 [:gamma :update-prob :field :mask :hold])))

(defn fitness [genome c seeds sites]
  ;; charge for the plasticity actually carried: a genome that has assimilated
  ;; half its cells pays half as much. Under a scalar gamma this reduces to c*gamma.
  (let [r (:mean (reach genome seeds sites))]
    ;; charge exactly the quantity that is reported -- see plastic-dependence
    {:reach r :score (- (band-score r) (* c (plastic-dependence genome)))}))

;; --- population --------------------------------------------------------------
(defn- step-level [^java.util.Random rng levels v]
  (nth levels (max 0 (min (dec (count levels))
                          (+ (.indexOf levels v) (dec (.nextInt rng 3)))))))

;; HORIZONTAL GENE TRANSFER. Mutation alone cannot assemble: if one lineage has
;; assimilated cells 0-20 and another 40-60, no sequence of point mutations combines
;; them, so partial solutions cannot meet. Recombination is what crosses that valley.
;; The segment is CONTIGUOUS along the lattice because the building blocks here are
;; spatial -- a uniform crossover would shred the very structure that carries reach.
;; Field and mask transfer TOGETHER: moving good rules into cells that are still
;; plastic would let them be overwritten immediately, so the segment must carry its
;; own assimilation state.
;; `hgt` draws its cut points from a DEDICATED stream. Drawing them from the
;; mutation rng meant enabling HGT consumed extra draws, so HGT and no-HGT arms
;; stopped sharing genetic randomness and could not be compared as a paired
;; treatment. This is the tape-alignment discipline applied to the outer loop.
(defn hgt [^java.util.Random rng a b]
  (let [w (count (:field a))
        i (.nextInt rng w) j (.nextInt rng w)
        [lo hi] (if (< i j) [i j] [j i])
        splice (fn [x y] (vec (concat (subvec x 0 lo) (subvec y lo hi) (subvec x hi w))))]
    (assoc a :field (splice (:field a) (:field b))
             :mask (splice (or (:mask a) (vec (repeat w true)))
                           (or (:mask b) (vec (repeat w true))))
             ;; :hold MUST travel with :field. Transferring rules under the
             ;; recipient's unrelated hold pattern cannot combine an inherited rule
             ;; with its fixed/plastic status, which is the whole mechanism this is
             ;; meant to test. Omitting it made the earlier HGT arm uninterpretable.
             :hold (splice (or (:hold a) (vec (repeat w false)))
                           (or (:hold b) (vec (repeat w false)))))))

;; When `plasticity-pinned?`, mutation may not touch `update-prob` or `mask`.
;; `hold-only` additionally pins gamma through `gamma-pinned?`; all three must be
;; fixed to make holding the only route by which dependence can fall. The failed
;; pin arms fixed only mask, then update-prob collapsed and gamma remained another
;; escape route.
(defn mutate
  [^java.util.Random rng {:keys [gamma update-prob field mask hold]} field-rate
   gamma-pinned? plasticity-pinned? hold-pinned?]
  ;; The pin MUST be applied here as well as to :mask. It was not, in the first
  ;; version: the patch targeted an older `(if (< (.nextDouble rng) 0.15) ...)` form
  ;; that no longer existed, str.replace silently no-opped, and clj-kondo passed
  ;; because the result was still valid Clojure that ignored the new parameter. Both
  ;; pin arms then ran unpinned and merely duplicated c05 and c2.
  {:update-prob (if plasticity-pinned?
                  (or update-prob 1.0)
                  (step-level rng UPDATE-LEVELS (or update-prob 1.0)))
   :gamma (if gamma-pinned? gamma
            (nth GAMMA-LEVELS
                 (max 0 (min (dec (count GAMMA-LEVELS))
                             (+ (.indexOf GAMMA-LEVELS gamma)
                                (dec (.nextInt rng 3)))))))
   ;; a genotype is a vector of integer rule indices (c/java-random-genotype),
   ;; so a field mutation is a point change of one cell's rule index
   :field (mapv (fn [r] (if (< (.nextDouble rng) field-rate)
                          (.nextInt rng c/rule-count)
                          r))
                field)
   ;; each cell's plasticity flips independently -- the per-locus analogue of
   ;; Hinton & Nowlan mutating a `?` to a fixed value or back
   :mask (let [m0 (or mask (vec (repeat (count field) true)))]
           (if plasticity-pinned?
             m0
             (mapv (fn [m] (if (< (.nextDouble rng) field-rate) (not m) m)) m0)))
   ;; each locus independently flips between plastic and FIXED -- H&N's ? <-> value
   :hold (let [h0 (or hold (vec (repeat (count field) false)))]
           (if hold-pinned?
             h0
             (mapv (fn [h] (if (< (.nextDouble rng) field-rate) (not h) h)) h0)))})


;; ---------------------------------------------------------------- PREFLIGHT ----
;; Delegates to mmca.baldwin-spec, which is the port of DarkTower/BaldwinDesign.lean
;; and whose predicates are tested against the counterexamples that motivated them.
;;
;; An earlier inline version reimplemented these weakly and gave FALSE ASSURANCE: its
;; I3 test checked key presence rather than donor linkage, so a child with field from
;; one parent and hold from the other passed -- exactly the defect it existed to
;; catch; and its I6 test accepted any profile with two distinct values, so the spike
;; [0 ... 0 0.627] passed, which is the landscape on which gradual retreat is
;; impossible. Duplicating a check weakly is worse than not having it.
;;
;; There is deliberately NO bypass flag. A run that cannot pass its invariants is a
;; run whose output cannot be interpreted.

(defn preflight
  "Returns the seq of invariant failures. Empty means the run may proceed."
  [seed-set site-set]
  (let [neutral {:gamma 1.0 :update-prob 1.0
                 :field (c/java-random-genotype (java.util.Random. 1) W)
                 :mask (vec (repeat W true)) :hold (vec (repeat W false))}
        dial (fn [g]
               (let [ms (for [sd [1 2 3]]
                          (let [r (java.util.Random. (long sd))
                                g0 (c/java-random-genotype r W)]
                            (:mean (reach (assoc neutral :gamma g :field g0) [sd] (range W)))))]
                 (/ (reduce + ms) (count ms))))
        rng (java.util.Random. 7)
        a (assoc neutral :hold (vec (repeat W true)))
        b (assoc neutral :field (c/java-random-genotype (java.util.Random. 2) W))
        child (hgt rng a b)
        walk (take 4000 (iterate #(mutate rng % 0.05 false false false) neutral))
        probe (mapv #(band-score (:mean (reach (assoc neutral :gamma %) seed-set site-set)))
                    GAMMA-LEVELS)
        ;; I2 layering fidelity -- Lean Extension.agrees
        i2-cases [{:neutral? true :performance (dial 0.0) :reference 1.2833  :label "gamma=0"}
                  {:neutral? true :performance (dial 1.0) :reference 12.3875 :label "gamma=1"}]
        i2 (spec/extension-failures i2-cases 1e-4)
        ;; I3 donor linkage -- Lean linkedHGT_sameDonor. Tests LINKAGE, not key presence.
        i3 (spec/linked-hgt? a b child)
        ;; I4 reachability -- Lean GeneReachable
        i4g (= (set GAMMA-LEVELS) (set (map :gamma walk)))
        i4u (= (set UPDATE-LEVELS) (set (map :update-prob walk)))
        i4h (= #{true false} (set (mapcat :hold walk)))
        i4m (= #{true false} (set (mapcat :mask walk)))
        i4f (> (count (distinct (mapcat :field walk))) 1)
        ;; I6 navigability -- rejects the constant axis AND the spike
        i6 (spec/axis-navigable? probe 2)]
    (cond-> []
      (seq i2) (conj {:invariant :I2-layering :failures (vec i2)})
      (not i3) (conj {:invariant :I3-donor-linkage :note "hgt did not keep field and hold from one donor"})
      (not (and i4g i4u i4h i4m i4f))
      (conj {:invariant :I4-reachability :gamma i4g :update-prob i4u :hold i4h :mask i4m :field i4f})
      (not i6) (conj {:invariant :I6-axis-not-navigable :profile probe
                      :gradient-steps (spec/gradient-steps probe)
                      :note "a constant axis or a single cliff cannot be descended"}))))

;; ------------------------------------------------------------- RECORDING ----
;; Lean's BaldwinWitness needs an accessible PATH, raw function at every step,
;; declining dependence, and a functional static endpoint. A TSV of population
;; means can express none of those, so even a successful run could not produce the
;; certificate. This writes, per individual per generation: the complete genome,
;; its parent and HGT donor, raw reach, band score and cost-adjusted fitness kept
;; SEPARATE, dependence, and -- for the generation's best -- the held-rule endpoint
;; evaluation that `inheritedFunction` requires.

(defn genome-record [gen ind]
  (-> (select-keys ind [:id :parent :donor :gamma :update-prob :reach :band :dependence :score])
      (assoc :gen gen
             :field (:field ind)
             :mask (mapv #(if % 1 0) (:mask ind))
             :hold (mapv #(if % 1 0) (:hold ind)))))

(defn write-records! [w gen scored best-endpoint]
  (when w
    (doseq [ind scored]
      (.write ^java.io.Writer w (str (pr-str (genome-record gen ind)) "\n")))
    (.write ^java.io.Writer w
            (str (pr-str {:gen gen :kind :endpoint
                          :id (:id (first scored))
                          :held-reach (:mean best-endpoint)}) "\n"))
    (.flush ^java.io.Writer w)))

(def ^:private allowed-arguments
  #{"c" "evolution-seed" "field-rate" "gens" "hgt" "manifest" "mode"
    "neutral" "pin" "pop" "preflight-certificate" "preflight-only" "record"
    "revision" "seeds" "sites" "warmup"})

(defn parse-arguments
  "Parse strict --key value pairs. Unknown, duplicate, and incomplete arguments
   are errors: silently accepting a misspelled treatment flag creates a plausible
   control arm and is therefore unsafe."
  [args]
  (when (odd? (count args))
    (throw (ex-info "arguments must be --key value pairs" {:args args})))
  (let [pairs (mapv (fn [[k v]]
                      (when-not (str/starts-with? k "--")
                        (throw (ex-info "argument name must start with --" {:argument k})))
                      [(subs k 2) v])
                    (partition 2 args))
        duplicates (->> pairs (map first) frequencies (keep (fn [[k n]] (when (> n 1) k))) sort vec)
        unknown (->> pairs (map first) (remove allowed-arguments) sort vec)]
    (when (seq duplicates)
      (throw (ex-info "duplicate arguments" {:duplicates duplicates})))
    (when (seq unknown)
      (throw (ex-info "unknown arguments" {:unknown unknown :allowed (sort allowed-arguments)})))
    (into {} pairs)))

(defn- flag? [argm k]
  (let [v (get argm k "0")]
    (when-not (#{"0" "1"} v)
      (throw (ex-info "boolean argument must be 0 or 1" {:argument k :value v})))
    (= "1" v)))

(defn mode-settings
  "Return the causal degrees of freedom for an explicit experimental mode.

   `hold-only` is the mechanistic ablation the failed pin arms intended: gamma,
   update probability, and every mask bit are fixed, while field and hold evolve.
   `static-search` is a positive/control arm in which every locus is permanently
   held and only the inherited rule field evolves."
  [mode]
  (case mode
    "standard" {:gamma nil :plasticity-pinned? false :hold-pinned? false
                :initial-hold false}
    "hold-only" {:gamma 1.0 :plasticity-pinned? true :hold-pinned? false
                 :initial-hold false}
    "static-search" {:gamma 1.0 :plasticity-pinned? true :hold-pinned? true
                     :initial-hold true}
    (throw (ex-info "unknown experimental mode"
                    {:mode mode :allowed ["standard" "hold-only" "static-search"]}))))

(defn validate-config!
  [{:keys [cost generations population evaluation-seed-count evaluation-site-count
           field-rate warmup pin]}]
  (when-not (and (pos? generations) (pos? evaluation-seed-count)
                 (<= 1 evaluation-site-count W)
                 (<= 0 warmup generations)
                 (<= 0.0 field-rate 1.0)
                 (not (neg? cost))
                 (>= population 2)
                 (even? population))
    (throw (ex-info "invalid experiment configuration"
                    {:cost cost :generations generations :population population
                     :evaluation-seed-count evaluation-seed-count
                     :evaluation-site-count evaluation-site-count
                     :field-rate field-rate :warmup warmup})))
  (when (and pin (not (some #{pin} GAMMA-LEVELS)))
    (throw (ex-info "--pin must be one of the heritable gamma levels"
                    {:pin pin :levels GAMMA-LEVELS})))
  true)

(defn assert-mode!
  "Fail immediately if a genome violates the treatment that its label promises."
  [mode genomes]
  (when (#{"hold-only" "static-search"} mode)
    (doseq [g genomes]
      (when-not (= 1.0 (double (:gamma g)))
        (throw (ex-info "mode invariant failed: gamma is not pinned"
                        {:mode mode :id (:id g) :gamma (:gamma g)})))
      (when-not (= 1.0 (double (:update-prob g)))
        (throw (ex-info "mode invariant failed: update-prob is not pinned"
                        {:mode mode :id (:id g) :update-prob (:update-prob g)})))
      (when-not (every? true? (:mask g))
        (throw (ex-info "mode invariant failed: mask is not all live"
                        {:mode mode :id (:id g)})))))
  (when (= "static-search" mode)
    (doseq [g genomes]
      (when-not (every? true? (:hold g))
        (throw (ex-info "mode invariant failed: static-search locus became plastic"
                        {:mode mode :id (:id g)})))))
  true)

(defn- run-manifest [argm config seed-set site-set]
  {:kind :manifest
   :schema 1
   :revision (get argm "revision" "unrecorded")
   :arguments (into (sorted-map)
                    (dissoc argm "manifest" "preflight-certificate" "record"))
   :configuration config
   :evaluation-seeds (vec seed-set)
   :evaluation-sites (vec site-set)
   :protocol {:width W :steps STEPS :tstar TSTAR :damage-time DT
              :band-low BAND-LOW :band-high BAND-HIGH}})

(defn- preflight-key [revision seed-set site-set]
  {:revision revision
   :evaluation-seeds (vec seed-set)
   :evaluation-sites (vec site-set)
   :protocol {:width W :steps STEPS :tstar TSTAR :damage-time DT
              :band-low BAND-LOW :band-high BAND-HIGH}})

(defn ensure-preflight!
  "Run the expensive invariant battery once, or validate a passing certificate
   bound to the exact revision and evaluation design. A certificate is a cache,
   not a bypass: any mismatch refuses the run."
  [revision seed-set site-set certificate-path create?]
  (let [key (preflight-key revision seed-set site-set)]
    (if create?
      (let [fails (vec (preflight seed-set site-set))
            certificate {:kind :baldwin-preflight :schema 1 :key key
                         :passed? (empty? fails) :failures fails}]
        (spit certificate-path (str (pr-str certificate) "\n"))
        (when (seq fails)
          (throw (ex-info "preflight failed" {:failures fails})))
        certificate)
      (if certificate-path
        (let [certificate (edn/read-string (slurp certificate-path))]
          (when-not (and (= :baldwin-preflight (:kind certificate))
                         (= 1 (:schema certificate))
                         (:passed? certificate)
                         (empty? (:failures certificate))
                         (= key (:key certificate)))
            (throw (ex-info "preflight certificate does not match this run"
                            {:expected key :certificate certificate})))
          certificate)
        (let [fails (vec (preflight seed-set site-set))]
          (when (seq fails)
            (throw (ex-info "preflight failed" {:failures fails})))
          {:kind :baldwin-preflight :schema 1 :key key
           :passed? true :failures []})))))

(def ^:private heritable-keys
  [:id :gamma :update-prob :field :mask :hold])

(defn breed
  "Apply the one shared truncation/reproduction operator used by selected and
   mutation-only arms. Keeping this lifecycle in one function prevents a cheap
   null from silently becoming a different null."
  [ranked ^java.util.Random rng ^java.util.Random hrng
   {:keys [hgt? field-rate gamma-pinned? plasticity-pinned? hold-pinned? fresh-id]}]
  (let [n (count ranked)
        survivors (vec (take (max 1 (quot n 2)) ranked))
        offspring (vec
                   (repeatedly
                    (- n (count survivors))
                    #(let [pa (nth survivors (.nextInt rng (count survivors)))
                           pb (nth survivors (.nextInt rng (count survivors)))
                           used-hgt? (and hgt? (not= pa pb))
                           base (if used-hgt? (hgt hrng pa pb) pa)]
                       (assoc (mutate rng base field-rate gamma-pinned?
                                      plasticity-pinned? hold-pinned?)
                              :id (fresh-id)
                              :parent (:id pa)
                              :donor (when used-hgt? (:id pb))))))]
    {:survivors survivors
     :offspring offspring
     :population (into (mapv #(select-keys % heritable-keys) survivors)
                       offspring)}))

(defn- write-manifest! [path record-writer manifest]
  (when path
    (spit path (str (pr-str manifest) "\n")))
  (when record-writer
    (.write ^java.io.Writer record-writer (str (pr-str manifest) "\n"))
    (.flush ^java.io.Writer record-writer)))

(defn -main [& args]
  (let [argm (parse-arguments args)
        {:strs [c gens pop seeds sites field-rate pin warmup]} argm
        cost (Double/parseDouble (or c "0.0"))
        G (Integer/parseInt (or gens "12"))
        P (Integer/parseInt (or pop "12"))
        nseeds (Integer/parseInt (or seeds "2"))
        nsites (Integer/parseInt (or sites "8"))
        frate (Double/parseDouble (or field-rate "0.02"))
        pinned (some-> pin Double/parseDouble)
        warm (Integer/parseInt (or warmup "8"))
        mode (get argm "mode" "standard")
        settings (mode-settings mode)
        mode-gamma (:gamma settings)
        _mode-check (when (and mode-gamma pinned (not= mode-gamma pinned))
                      (throw (ex-info "--pin conflicts with experimental mode"
                                      {:mode mode :mode-gamma mode-gamma :pin pinned})))
        gamma-start (or mode-gamma pinned)
        ;; read from the arg map, NOT a destructured `hgt` binding -- that would
        ;; shadow the hgt fn and (hgt rng pa pb) would try to call a string
        hgt? (flag? argm "hgt")
        pin-plast? (:plasticity-pinned? settings)
        hold-pinned? (:hold-pinned? settings)
        ;; dedicated streams: HGT cut points and neutral-mode coins must not shift
        ;; the mutation tape, or paired arms cease to be comparable
        evolution-seed (Long/parseLong (get argm "evolution-seed" "20260730"))
        hrng (java.util.Random. (+ evolution-seed 555000001))
        nrng (java.util.Random. (+ evolution-seed 555000002))
        neutral? (flag? argm "neutral")
        preflight-only? (flag? argm "preflight-only")
        certificate-path (get argm "preflight-certificate")
        _certificate-check
        (when (and preflight-only? (nil? certificate-path))
          (throw (ex-info "--preflight-only requires --preflight-certificate"
                          {:arguments argm})))
        record-file (get argm "record")
        w (when record-file (io/writer record-file))
        !next-id (atom 0)
        fresh-id #(swap! !next-id inc)
        rng (java.util.Random. evolution-seed)
        seed-set (range 1 (inc nseeds))
        site-set (take nsites (range 0 W (max 1 (quot W nsites))))
        config {:mode mode :cost cost :generations G :population P
                :evaluation-seed-count nseeds :evaluation-site-count nsites
                :field-rate frate :warmup warm :hgt hgt? :neutral neutral?
                :evolution-seed evolution-seed
                :pin pinned
                :gamma-pinned (some? gamma-start)
                :plasticity-pinned pin-plast? :hold-pinned hold-pinned?}]
    (validate-config! config)
    (ensure-preflight! (get argm "revision" "unrecorded")
                       seed-set site-set certificate-path preflight-only?)
    (when preflight-only?
      (binding [*out* *err*]
        (println "preflight certificate written:" certificate-path))
      (when w (.close ^java.io.Writer w))
      (System/exit 0))
    (binding [*out* *err*]
      (println "preflight: certified I2 I3 I4 I6. NOT checked here:"
               "I1 tape alignment, I5 empirical null, I7 endpoint, I8 calibration,"
               "I9 treatment separation."))
    (write-manifest! (get argm "manifest") w (run-manifest argm config seed-set site-set))
    (println "gen\tmean-gamma\tmean-score\tmean-reach\tbest-score\tbest-gamma\tmean-update\tbest-update\tmean-plastic\tmean-held")
    (loop [gen 0
           population (vec (repeatedly P #(hash-map
                                            :gamma (or gamma-start (nth GAMMA-LEVELS (.nextInt rng 8)))
                                            ;; Start at 1.0 -- where the river dynamics actually work, as the pre-G5 runs
   ;; implicitly did. Randomising this crippled most lineages from generation 0,
   ;; band-score was then 0 for the whole population, and the only ranking signal
   ;; left was -c*gamma, so selection just minimised gamma and never found reach.
   ;; Mutation still walks update-prob down to 0, so degeneration stays reachable.
                                            :update-prob 1.0
                                            :mask (vec (repeat W true))
                                            :hold (vec (repeat W (:initial-hold settings)))
                                            :field (c/java-random-genotype rng W)
                                            :id (fresh-id))))]
      (when (< gen G)
        (assert-mode! mode population)
        ;; Fitness evaluation is embarrassingly parallel across the population:
        ;; each genome's reach builds its own seeded RNGs and shares no state, so
        ;; pmap changes wall-clock only, never the numbers. This is what lets one
        ;; run use a many-core box rather than one core.
        ;; Baldwin's first phase is plasticity ESTABLISHING; charging for it from
        ;; generation 0 forecloses that by construction. Cost applies after warm-up.
        (let [c-now (if (< gen warm) 0.0 cost)
              evaluated (vec
                         (pmap (fn [g]
                                 (let [r (:mean (reach g seed-set site-set))
                                       b (band-score r)
                                       d (plastic-dependence g)]
                                   ;; Band and cost stay separate so the witness
                                   ;; checker can inspect raw function.
                                   (assoc g :reach r :band b :dependence d
                                            :score (- b (* c-now d)))))
                               population))
              ;; Neutral coins must be assigned SEQUENTIALLY. Drawing from nrng
              ;; inside pmap made draw-to-genome assignment scheduler-dependent.
              ;; A constant score is also not neutral because stable sort would
              ;; retain the same half forever.
              scored (if neutral?
                       (mapv #(assoc % :score (.nextDouble ^java.util.Random nrng))
                             evaluated)
                       evaluated)
              ranked (vec (sort-by :score > scored))
              n (count ranked)
              breeding (breed ranked rng hrng
                              {:hgt? hgt? :field-rate frate
                               :gamma-pinned? (some? gamma-start)
                               :plasticity-pinned? pin-plast?
                               :hold-pinned? hold-pinned?
                               :fresh-id fresh-id})
              next-population (:population breeding)
              mg (/ (reduce + (map :gamma scored)) (double n))
              mu (/ (reduce + (map #(or (:update-prob %) 1.0) scored)) (double n))
              mp (/ (reduce + (map plastic-fraction scored)) (double n))
              ;; RAW held fraction, distinct from plastic-dependence: this is what the
              ;; --neutral null baselines, and it is not recoverable from mp because mp
              ;; folds in gamma and update-prob
              mh (/ (reduce + (map (fn [g] (/ (count (filter true? (:hold g)))
                                              (double (count (:hold g))))) scored))
                    (double n))
              ms (/ (reduce + (map :score scored)) (double n))
              mr (/ (reduce + (map :reach scored)) (double n))
              best (first ranked)
              ;; Lean inheritedFunction: does the best genome still work when EVERY
              ;; locus is held? Without this a witness cannot be completed.
              endpoint (reach (assoc best :hold (vec (repeat W true))) seed-set site-set)
              _ (write-records! w gen ranked endpoint)]
          (assert-mode! mode next-population)
          (println (format "%d\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f\t%.4f"
                           gen mg ms mr (:score best) (:gamma best)
                           mu (or (:update-prob best) 1.0) mp mh))
          (flush)
          (recur (inc gen) next-population))))
    (when w (.close ^java.io.Writer w))))
