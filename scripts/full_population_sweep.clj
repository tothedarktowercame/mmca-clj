;; Joe's design (2026-07-27): seed the genotype with a randomised order of the
;; FULL rule population, so every operator starts at maximum diversity, and
;; anchor the high-diversity end with a preserving operator (identity writing,
;; invert? = false) that has no genotype dynamics and therefore no edge of chaos.
;; If propagation peaks between the collapsing operators and the preserver, the
;; maximum is in the middle.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn full-population [seed W]
  (let [reps (quot W 256)]
    (vec (shuffle (vec (mapcat (fn [_] (range 256)) (range reps)))
                  ))))
(defn seeded-population [seed W]
  (let [rnd (java.util.Random. (+ 90000 seed))
        base (vec (mapcat (fn [_] (range 256)) (range (quot W 256))))]
    (loop [v base i (dec (count v))]
      (if (<= i 0) v
        (let [j (.nextInt rnd (inc i))]
          (recur (assoc v i (nth v j) j (nth v i)) (dec i)))))))
(defn probe [writing invert? seed W T t* sites]
  (let [r (rng/make-rng (format "prop-%d" seed))
        _ (c/random-genotype r W)                    ; keep the tape aligned
        g0 (seeded-population seed W)
        p0 (c/random-phenotype r W)]
    (loop [t 0 g g0 p p0]
      (if (= t t*)
        {:sustained-at-t* (c/distinct-rules g)
         :probes (for [x sites]
                   (let [rA (clone r) rB (clone r)
                         gB (assoc g x (bit-xor (nth g x) 1))]
                     (loop [tt t* gA g pA p gB* gB pB p]
                       (if (= tt T)
                         {:dG (c/changed-count gA gB*) :rules (c/distinct-rules gA)}
                         (let [pA' (c/phenotype-step gA pA) gA' (c/genotype-step rA gA writing invert?)
                               pB' (c/phenotype-step gB* pB) gB' (c/genotype-step rB gB* writing invert?)]
                           (recur (inc tt) gA' pA' gB' pB'))))))}
        (let [np (c/phenotype-step g p) ng (c/genotype-step r g writing invert?)]
          (recur (inc t) ng np))))))
(let [W (Integer/parseInt (or (System/getenv "SWEEP_W") "256"))
      T 70 t* 30 sites (range 0 W (quot W 16))
      ops (concat [["preserver" [0 1 2 3 4 5 6 7] false]]
                  (for [k (range 1 8)] [(str "rot+" k) (vec (map #(mod (+ % k) 8) (range 8))) true])
                  [["two4cyc" [6 7 0 2 1 4 3 5] true]
                   ["sigma16250374" (c/positional-writing->neighbourhood-writing [1 6 2 5 0 3 7 4]) true]
                   ["p-a" [3 0 1 2 7 4 5 6] true] ["p-c" [2 5 0 7 4 1 6 3] true]
                   ["p-e" [4 0 6 2 5 1 7 3] true]])]
  (println "op\tW\tseed\trules_at_tstar\trules_end\tdG")
  (doseq [[nm wrt inv] ops seed (range 3)]
    (let [{:keys [sustained-at-t* probes]} (probe wrt inv seed W T t* sites)]
      (doseq [pr probes]
        (println (format "%s\t%d\t%d\t%d\t%d\t%d" nm W seed sustained-at-t* (:rules pr) (:dG pr)))))))
