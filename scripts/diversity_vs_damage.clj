;; Does sustained diversity (a lambda-analogue) predict causal propagation?
;; Perturbs one genotype bit while every operator is still alive (t*=20).
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(defn probe [writing seed W T t* sites]
  (let [r (rng/make-rng (format "prop-%d" seed))
        g0 (c/random-genotype r W) p0 (c/random-phenotype r W)]
    (loop [t 0 g g0 p p0]
      (if (= t t*)
        (for [x sites]
          (let [rA (clone r) rB (clone r) gB (assoc g x (bit-xor (nth g x) 1))]
            (loop [tt t* gA g pA p gB* gB pB p out {}]
              (if (= tt T) out
                (let [pA' (c/phenotype-step gA pA) gA' (c/genotype-step rA gA writing true)
                      pB' (c/phenotype-step gB* pB) gB' (c/genotype-step rB gB* writing true)
                      dt (- (inc tt) t*)]
                  (recur (inc tt) gA' pA' gB' pB'
                         (if (#{10 20 30} dt)
                           (assoc out dt [(c/changed-count gA' gB')
                                          (count (remove true? (map = pA' pB')))
                                          (c/distinct-rules gA')])
                           out)))))))
        (let [np (c/phenotype-step g p) ng (c/genotype-step r g writing true)]
          (recur (inc t) ng np))))))
(let [W 80 T 50 t* 20 sites (range 0 W 10)
      rot (fn [k] (vec (map #(mod (+ % k) 8) (range 8))))
      ops (concat (for [k (range 1 8)] [(str "rot+" k) (rot k)])
                  [["two4cyc" [6 7 0 2 1 4 3 5]]
                   ["sigma16250374" (c/positional-writing->neighbourhood-writing [1 6 2 5 0 3 7 4])]
                   ["p-a" [3 0 1 2 7 4 5 6]] ["p-b" [1 0 3 2 5 4 7 6]]
                   ["p-c" [2 5 0 7 4 1 6 3]] ["p-d" [7 3 5 1 6 2 4 0]]
                   ["p-e" [4 0 6 2 5 1 7 3]] ["p-f" [1 3 5 7 0 2 4 6]]])]
  (println "op\tseed\tsite\tdt\tdG\tdX\trules\tsustained")
  (doseq [[nm wrt] ops seed (range 6)]
    (let [sus (c/distinct-rules (nth (:gen (c/run-propagator wrt seed W T)) (dec T)))]
      (doseq [[x res] (map vector sites (probe wrt seed W T t* sites))
              [dt [dg dx ru]] res]
        (println (format "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d" nm seed x dt dg dx ru sus))))))
