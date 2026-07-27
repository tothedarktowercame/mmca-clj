;; The preserving limit: genotype held fixed at a full, randomised rule
;; population. Maximum diversity by construction, zero genotype dynamics, so no
;; edge of chaos. This is a boundary condition on the diversity axis, not a
;; member of the operator family -- it does not go through genotype-step, and so
;; is not subject to the blend.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn seeded-population [seed W]
  (let [rnd (java.util.Random. (+ 90000 seed))
        base (vec (mapcat (fn [_] (range 256)) (range (max 1 (quot W 256)))))]
    (loop [v base i (dec (count v))]
      (if (<= i 0) v
        (let [j (.nextInt rnd (inc i))]
          (recur (assoc v i (nth v j) j (nth v i)) (dec i)))))))
(defn probe [seed W T t* sites]
  (let [r (rng/make-rng (format "prop-%d" seed))
        _ (c/random-genotype r W)
        g (seeded-population seed W)                 ; genotype: fixed forever
        p0 (c/random-phenotype r W)]
    (loop [t 0 p p0]                                  ; only the phenotype evolves
      (if (= t t*)
        (for [x sites]
          (let [gB (assoc g x (bit-xor (nth g x) 1))]
            (loop [tt t* pA p pB p]
              (if (= tt T)
                {:dG (c/changed-count g gB)
                 :dX (count (remove true? (map = pA pB)))
                 :rules (c/distinct-rules g)}
                (recur (inc tt) (c/phenotype-step g pA) (c/phenotype-step gB pB))))))
        (recur (inc t) (c/phenotype-step g p))))))
(let [W 256 T 70 t* 30 sites (range 0 W 16)]
  (println "op\tW\tseed\trules\tdG\tdX")
  (doseq [seed (range 3)]
    (doseq [pr (probe seed W T t* sites)]
      (println (format "preserve\t%d\t%d\t%d\t%d\t%d" W seed (:rules pr) (:dG pr) (:dX pr))))))
