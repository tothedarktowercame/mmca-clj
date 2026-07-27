(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn seeded-population [seed W]
  (let [rnd (java.util.Random. (+ 90000 seed))
        base (vec (mapcat (fn [_] (range 256)) (range (max 1 (quot W 256)))))]
    (loop [v base i (dec (count v))]
      (if (<= i 0) v (let [j (.nextInt rnd (inc i))]
        (recur (assoc v i (nth v j) j (nth v i)) (dec i)))))))
(defn probe [wa wb seed W t* window sites]
  (let [r (rng/make-rng (format "prop-%d" seed)) _ (c/random-genotype r W)
        g0 (seeded-population seed W) p0 (c/random-phenotype r W)
        step (fn [rr g t] (c/genotype-step rr g (if (or (nil? wb) (even? t)) wa wb) true))]
    (loop [t 0 g g0 p p0]
      (if (= t t*)
        {:rules (c/distinct-rules g)
         :probes (for [x sites]
                   (let [rA (clone r) rB (clone r) gB (assoc g x (bit-xor (nth g x) 1))]
                     (loop [tt t* gA g pA p gB* gB pB p]
                       (if (= tt (+ t* window)) (c/changed-count gA gB*)
                         (recur (inc tt) (step rA gA tt) (c/phenotype-step gA pA)
                                         (step rB gB* tt) (c/phenotype-step gB* pB))))))}
        (recur (inc t) (step r g t) (c/phenotype-step g p))))))
(let [W 256 window 30 sites (range 0 W 16)
      PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5] R1 [1 2 3 4 5 6 7 0] R3 [3 4 5 6 7 0 1 2]]
  (println "cfg\tt*\tseed\trules\tdG")
  (doseq [[nm wa wb] [["p-a" PA nil] ["two4cyc" T4 nil] ["braid pa/t4" PA T4] ["braid r1/r3" R1 R3]]
          t* [1 2 3 5 8 12 20 30 45]
          seed (range 2)]
    (let [{:keys [rules probes]} (probe wa wb seed W t* window sites)]
      (doseq [dg probes] (println (format "%s\t%d\t%d\t%d\t%d" nm t* seed rules dg))))))
