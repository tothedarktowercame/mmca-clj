;; Where do the dial mechanisms sit on the elementary-rule damage-spreading
;; scale? Same protocol as find:ladder -- L=80, t*=60, T=120, flip one PHENOTYPE
;; bit, count differing phenotype cells at dt=59 -- so the numbers are directly
;; comparable with rule 0 / 204 / 90 / 54 / 110 / 30.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(def W 80) (def T 120) (def TSTAR 60)
(defn eca-row [rule p]
  (let [n (count p)]
    (apply str (for [i (range n)]
      (let [l (Character/digit (nth p (mod (dec i) n)) 2)
            ce (Character/digit (nth p i) 2)
            r (Character/digit (nth p (mod (inc i) n)) 2)]
        (bit-and (bit-shift-right rule (+ (* 4 l) (* 2 ce) r)) 1))))))
(defn run-eca [rule seed]
  (let [r (rng/make-rng (format "prop-%d" seed))
        _ (c/random-genotype r W) p0 (c/random-phenotype r W)]
    (loop [t 0 p p0]
      (if (= t TSTAR)
        (for [x (range 0 W 8)]
          (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))]
            (loop [tt TSTAR a p b pB]
              (if (= tt (+ TSTAR 59)) (count (remove true? (map = a b)))
                (recur (inc tt) (eca-row rule a) (eca-row rule b))))))
        (recur (inc t) (eca-row rule p))))))
(defn run-mech [wa wb noise-p async-u seed]
  (let [r (rng/make-rng (format "prop-%d" seed)) nr (rng/make-rng (format "noise-%d" seed))
        ar (rng/make-rng (format "async-%d" seed))
        g0 (c/random-genotype r W) p0 (c/random-phenotype r W)
        gstep (fn [rr nn aa g t]
                (if (nil? wa) g                                  ; preserving limit
                  (let [w (if (or (nil? wb) (even? t)) wa wb)
                        g' (c/genotype-step rr g w true)
                        g' (if (pos? async-u)
                             (vec (map-indexed (fn [i v] (if (< (rng/rand-double aa) async-u)
                                                           (nth g i) v)) g'))
                             g')]
                    (if (pos? noise-p) (c/genotype-noise nn g' noise-p) g'))))]
    (loop [t 0 g g0 p p0]
      (if (= t TSTAR)
        (for [x (range 0 W 8)]
          (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))
                rA (clone r) rB (clone r) nA (clone nr) nB (clone nr) aA (clone ar) aB (clone ar)]
            (loop [tt TSTAR gA g pA p gB g pB* pB]
              (if (= tt (+ TSTAR 59)) (count (remove true? (map = pA pB*)))
                (recur (inc tt) (gstep rA nA aA gA tt) (c/phenotype-step gA pA)
                                (gstep rB nB aB gB tt) (c/phenotype-step gB pB*))))))
        (recur (inc t) (gstep r nr ar g t) (c/phenotype-step g p))))))
(let [PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5]
      R2 [2 3 4 5 6 7 0 1] R4 [4 5 6 7 0 1 2 3] R1 [1 2 3 4 5 6 7 0]]
  (println "kind\tname\tseed\tdamage")
  (doseq [rule [0 204 90 54 110 30] seed (range 4)]
    (doseq [d (run-eca rule seed)] (println (format "eca\trule %d\t%d\t%d" rule seed d))))
  (doseq [[nm wa wb np au]
          [["p-a (bare)" PA nil 0.0 0.0] ["braid p-a/two4" PA T4 0.0 0.0]
           ["braid rot+2/rot+4" R2 R4 0.0 0.0] ["rot+1" R1 nil 0.0 0.0]
           ["p-a + async 0.25" PA nil 0.0 0.25] ["p-a + async 0.75" PA nil 0.0 0.75]
           ["p-a + mutation 0.10" PA nil 0.10 0.0] ["p-a + mutation 0.40" PA nil 0.40 0.0]
           ["preserving limit" nil nil 0.0 0.0]]
          seed (range 4)]
    (doseq [d (run-mech wa wb np au seed)]
      (println (format "mech\t%s\t%d\t%d" nm seed d)))))
