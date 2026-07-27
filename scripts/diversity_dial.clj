;; Populate the 100-256 diversity gap using machinery we already have:
;; noise-p as a continuous diversity dial, braided writings, and the no-blend
;; variant. Full-population seeding throughout. Noise uses its own RNG which is
;; cloned into both branches, so identical replacements land in both and the
;; noise injects no spurious damage.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn seeded-population [seed W]
  (let [rnd (java.util.Random. (+ 90000 seed))
        base (vec (mapcat (fn [_] (range 256)) (range (max 1 (quot W 256)))))]
    (loop [v base i (dec (count v))]
      (if (<= i 0) v
        (let [j (.nextInt rnd (inc i))]
          (recur (assoc v i (nth v j) j (nth v i)) (dec i)))))))
(defn gstep [r nr g wa wb t noise-p blend?]
  (let [w (if (or (nil? wb) (even? t)) wa wb)
        g' (if blend? (c/genotype-step r g w true) (c/genotype-step-alone r g w true))]
    (if (pos? noise-p) (c/genotype-noise nr g' noise-p) g')))
(defn probe [wa wb noise-p blend? seed W T t* sites]
  (let [r (rng/make-rng (format "prop-%d" seed))
        nr (rng/make-rng (format "noise-%d" seed))
        _ (c/random-genotype r W)
        g0 (seeded-population seed W)
        p0 (c/random-phenotype r W)]
    (loop [t 0 g g0 p p0]
      (if (= t t*)
        {:rules (c/distinct-rules g)
         :probes (for [x sites]
                   (let [rA (clone r) rB (clone r) nA (clone nr) nB (clone nr)
                         gB (assoc g x (bit-xor (nth g x) 1))]
                     (loop [tt t* gA g pA p gB* gB pB p]
                       (if (= tt T)
                         {:dG (c/changed-count gA gB*) :rules (c/distinct-rules gA)}
                         (recur (inc tt)
                                (gstep rA nA gA wa wb tt noise-p blend?) (c/phenotype-step gA pA)
                                (gstep rB nB gB* wa wb tt noise-p blend?) (c/phenotype-step gB* pB))))))}
        (recur (inc t) (gstep r nr g wa wb t noise-p blend?) (c/phenotype-step g p))))))
(let [W 256 T 70 t* 30 sites (range 0 W 16)
      PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5]
      R1 [1 2 3 4 5 6 7 0] R3 [3 4 5 6 7 0 1 2] R5 [5 6 7 0 1 2 3 4]
      R2 [2 3 4 5 6 7 0 1] R4 [4 5 6 7 0 1 2 3]
      cfgs (concat
             (for [p [0.0 0.005 0.02 0.05 0.1 0.2 0.4]] [(str "p-a n" p) PA nil p true])
             (for [p [0.0 0.02 0.1 0.3]] [(str "two4cyc n" p) T4 nil p true])
             (for [p [0.0 0.02 0.1]] [(str "rot+5 n" p) R5 nil p true])
             [["braid r1/r3" R1 R3 0.0 true] ["braid pa/t4" PA T4 0.0 true]
              ["braid r2/r4" R2 R4 0.0 true] ["braid r1/r3 n0.02" R1 R3 0.02 true]
              ["noblend p-a" PA nil 0.0 false] ["noblend r1" R1 nil 0.0 false]
              ["noblend braid r1/r3" R1 R3 0.0 false]])]
  (println "cfg\tseed\trules\tdG")
  (doseq [[nm wa wb np bl] cfgs seed (range 2)]
    (let [{:keys [rules probes]} (probe wa wb np bl seed W T t* sites)]
      (doseq [pr probes] (println (format "%s\t%d\t%d\t%d" nm seed rules (:dG pr)))))))
