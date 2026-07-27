;; The disordered end: random mutation gives high diversity WITH active
;; rewriting and low propagation -- the configuration the preserving limit
;; cannot supply, because that one freezes the dynamics. Together they pin both
;; ends of the axis for different reasons: frozen (no rewriting) and chaotic
;; (rewriting that destroys what it carries).
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn seeded-population [seed W]
  (let [rnd (java.util.Random. (+ 90000 seed))
        base (vec (mapcat (fn [_] (range 256)) (range (max 1 (quot W 256)))))]
    (loop [v base i (dec (count v))]
      (if (<= i 0) v (let [j (.nextInt rnd (inc i))]
        (recur (assoc v i (nth v j) j (nth v i)) (dec i)))))))
(defn probe [wa wb p seed W t* window sites]
  (let [r (rng/make-rng (format "prop-%d" seed)) nr (rng/make-rng (format "noise-%d" seed))
        _ (c/random-genotype r W) g0 (seeded-population seed W) p0 (c/random-phenotype r W)
        step (fn [rr nn g t] (let [g' (c/genotype-step rr g (if (or (nil? wb) (even? t)) wa wb) true)]
                               (if (pos? p) (c/genotype-noise nn g' p) g')))]
    (loop [t 0 g g0 ph p0]
      (if (= t t*)
        (let [g-next (step (clone r) (clone nr) g t)]
          {:rules (c/distinct-rules g)
           :churn (/ (double (c/changed-count g g-next)) W)
           :probes (for [x sites]
                     (let [rA (clone r) rB (clone r) nA (clone nr) nB (clone nr)
                           gB (assoc g x (bit-xor (nth g x) 1))]
                       (loop [tt t* gA g pA ph gB* gB pB ph]
                         (if (= tt (+ t* window)) (c/changed-count gA gB*)
                           (recur (inc tt) (step rA nA gA tt) (c/phenotype-step gA pA)
                                           (step rB nB gB* tt) (c/phenotype-step gB* pB))))))})
        (recur (inc t) (step r nr g t) (c/phenotype-step g ph))))))
(let [W 256 t* 20 window 30 sites (range 0 W 16)
      PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5]]
  (println "cfg\tp\tseed\trules\tchurn\tdG")
  (doseq [[nm wa wb] [["p-a" PA nil] ["braid pa/t4" PA T4]]
          p [0.0 0.005 0.01 0.02 0.05 0.1 0.2 0.4 0.7]
          seed (range 3)]
    (let [{:keys [rules churn probes]} (probe wa wb p seed W t* window sites)]
      (doseq [dg probes]
        (println (format "%s\t%.3f\t%d\t%d\t%.3f\t%d" nm p seed rules churn dg))))))
