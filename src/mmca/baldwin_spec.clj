(ns mmca.baldwin-spec
  "Executable port of DarkTower/BaldwinDesign.lean.

   Every definition here names the Lean declaration it mirrors, so the
   correspondence can be audited rather than assumed. The point is that a claim
   about a run is DECIDED by these predicates rather than asserted from a table:
   nine designs in this line of work were reported as results before someone
   noticed they could not have answered their question.

   The checker reports WHICH condition failed, because that is the useful output.")


;; ---------------------------------------------------------------- Genome ----

(defn static?
  "Lean: `Genome.IsStatic` — every locus is held."
  [{:keys [hold]}]
  (boolean (and (seq hold) (every? true? hold))))

(defn hold-all
  "Lean: `Genome.holdAll` — fix every locus without touching any inherited rule."
  [g]
  (assoc g :hold (vec (repeat (count (:field g)) true))))

(defn hold-all-preserves-field?
  "Lean: `holdAll_field` — holding changes no inherited rule."
  [g]
  (= (:field g) (:field (hold-all g))))

;; --------------------------------------------------------- Tape alignment ----

(defn tape-aligned?
  "Lean: `LooseEvaluator.TapeAligned` — the draw schedule does not consult branch
   state, so both branches of a perturbation fork consume the same tape.

   `per-branch-draws` is a seq of two seqs of per-step draw vectors, one per fork
   branch. Non-vacuity matters: if the branches never differed the check passes
   trivially, so `differed?` must also hold."
  [[a b] differed?]
  (and (boolean differed?) (= (vec a) (vec b))))

;; ------------------------------------------------------------ Linked HGT ----

(defn linked-hgt?
  "Lean: `linkedHGT_sameDonor` — at each locus the rule and its held flag come
   from the SAME donor. Splicing `field` without `hold` places transferred rules
   under the recipient's unrelated hold pattern; that defect made one arm
   uninterpretable."
  [a b child]
  (every? (fn [i]
            (let [fa (nth (:field a) i) fb (nth (:field b) i)
                  ha (nth (:hold a) i)  hb (nth (:hold b) i)
                  fc (nth (:field child) i) hc (nth (:hold child) i)]
              (or (and (= fc fa) (= hc ha))
                  (and (= fc fb) (= hc hb)))))
          (range (count (:field child)))))

;; --------------------------------------------- Mutation-only baseline (I5) ----

(defn mutation-only-held
  "Lean: `mutationOnlyHeld_closedForm` — (1 - (1-mu)^n)/2 for the ELITIST
   lifecycle in which survivors are retained unmutated and only offspring mutate.
   Using (1 - (1-2mu)^n)/2 instead overstated an effect by about 1.6x."
  [mu n]
  (/ (- 1.0 (Math/pow (- 1.0 (double mu)) n)) 2.0))

;; ------------------------------------------------- Axis degeneracy (I6) ----

(defn axis-degenerate?
  "Lean: `DependenceDegenerate` — the selected quantity takes one value across
   the sampled genomes. Lean's `no_witness_of_degenerate` proves that admits NO
   Baldwin witness of any length, so this is a pre-flight: if it holds, do not run."
  [dependences]
  (<= (count (distinct (map double dependences))) 1))

;; ------------------------------------------------------ Baldwin witness ----

(defn witness-failures
  "Lean: `BaldwinWitness`. Returns the seq of conditions a trajectory FAILS, so
   an empty seq means the trajectory certifies a Baldwin claim.

   `traj` is a seq of {:genome :performance :dependence}; `threshold` is the
   design's success threshold; `accessible?` decides admitted transitions."
  [traj threshold accessible?]
  (let [n (dec (count traj))
        perf #(:performance %) dep #(:dependence %)
        pairs (partition 2 1 traj)
        fin (last traj)]
    (cond-> []
      (< n 1)
      (conj :path-too-short)
      (not (every? (fn [[x y]] (accessible? (:genome x) (:genome y))) pairs))
      (conj :accessible-step)                     ; a transition no operator admits
      (not (every? #(<= threshold (perf %)) traj))
      (conj :high-function)                       ; the path crosses a valley
      (not (every? (fn [[x y]] (<= (dep y) (dep x))) pairs))
      (conj :dependence-step)                     ; dependence rose somewhere
      (not (< (dep fin) (dep (first traj))))
      (conj :strict-assimilation)                 ; no net retreat
      (not (static? (:genome fin)))
      (conj :final-static)
      (not (<= threshold (perf (assoc fin :genome (hold-all (:genome fin))))))
      (conj :inherited-function))))               ; fails when fully held

(defn baldwin-witness? [traj threshold accessible?]
  (empty? (witness-failures traj threshold accessible?)))


;; --------------------------------------- search over observed trajectories ----

(defn find-witness-path
  "Search observed genomes for a length-`n` chain that could witness assimilation:
   admitted transitions, never below threshold, dependence non-increasing. `nil`
   means no such path exists among what was observed, which is stronger than any
   single trajectory failing.

   NOTE ON core.logic. An earlier draft wrapped this in `l/run 1`, which added a
   dependency and bought nothing -- the constraints here are a filter over
   contiguous windows, and logic programming contributes only when the search
   must GENERATE candidates rather than sieve given ones. The natural relational
   version is the counterexample search: given the accessibility relation and the
   scoring function, does ANY path exist in genome space, including genomes never
   observed? That is a genuine synthesis problem and would justify core.logic;
   this is not."
  [observations n threshold accessible?]
  (->> observations
       (filter #(<= threshold (:performance %)))
       (partition n 1)
       (map vec)
       (filter (fn [c]
                 (and (every? (fn [[x y]] (accessible? (:genome x) (:genome y)))
                              (partition 2 1 c))
                      (every? (fn [[x y]] (<= (:dependence y) (:dependence x)))
                              (partition 2 1 c)))))
       first))
