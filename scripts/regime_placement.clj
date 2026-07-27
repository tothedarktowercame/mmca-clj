;; Where does each construction sit on the elementary-rule damage-spreading
;; scale? One protocol throughout -- L=80, t*=60, T=120, flip one PHENOTYPE bit,
;; count differing phenotype cells at dt=59 -- so every number is directly
;; comparable with rules 0 / 204 / 90 / 54 / 110 / 30 and with find:ladder.
;;
;; Mechanism semantics follow diversity_dial2.clj exactly:
;;   blend-strength b : with probability b propagate from the neighbour blend,
;;                      otherwise from the centre rule
;;   async-refuges u  : with probability u propagate from the blend, otherwise
;;                      leave the cell's rule unchanged (u is an UPDATE fraction)
;;   niches           : blend-propagate with the writing selected by patch
;; Conservative transport follows diversity_dial4.clj: Margolus-style disjoint
;; phenotype-gated bijective swaps, which never rewrite a rule.
(require '[mmca.core :as c] '[mmca.rng :as rng])
(defn clone [r] (rng/->EmacsRng (atom @(:state r)) (atom @(:index r))))
(def W 80) (def TSTAR 60) (def DT 59)
(defn eca-row [rule p]
  (let [n (count p)]
    (apply str (for [i (range n)]
      (let [l (Character/digit (nth p (mod (dec i) n)) 2)
            ce (Character/digit (nth p i) 2)
            r (Character/digit (nth p (mod (inc i) n)) 2)]
        (bit-and (bit-shift-right rule (+ (* 4 l) (* 2 ce) r)) 1))))))
(defn mech-step [pr mr g cfg t phenotype]
  (let [{:keys [kind wa wb b u patch]} cfg]
    (if (= kind :preserve) g
      (if (= kind :transport)
        (let [start (mod t 2)]
          (reduce (fn [gg i]
                    (let [j (inc i)
                          li (Character/digit (nth phenotype i) 2)
                          lj (Character/digit (nth phenotype j) 2)
                          pr* (min 1.0 (* u (if (= li lj) 0.5 1.5)))]
                      (if (< (rng/rand-double mr) pr*)
                        (assoc gg i (nth gg j) j (nth gg i)) gg)))
                  g (range start (dec W) 2)))
        (let [ord (concat [0 (dec W)] (range 1 (dec W)))]
          (reduce (fn [out i]
                    (let [pred (if (zero? i) c/default-rule (nth g (dec i)))
                          ctr (nth g i)
                          succ (if (= i (dec W)) c/default-rule (nth g (inc i)))
                          blended (c/blend-rule pred ctr succ)
                          source (rng/rand-int pr 8)
                          wr (cond wb (if patch
                                        (if (even? (quot i patch)) wa wb)
                                        (if (even? t) wa wb))
                                   :else wa)
                          nxt (case kind
                                :baseline (c/propagate-at blended wr source true)
                                :blend (c/propagate-at
                                         (if (< (rng/rand-double mr) b) blended ctr)
                                         wr source true)
                                :async (if (< (rng/rand-double mr) u)
                                         (c/propagate-at blended wr source true) ctr))]
                      (assoc out i nxt)))
                  (vec (repeat W nil)) ord))))))
(defn run-mech [cfg seed]
  (let [pr (rng/make-rng (format "prop-%d" seed)) mr (rng/make-rng (format "mech-%d" seed))
        nr (rng/make-rng (format "noise-%d" seed))
        g0 (c/random-genotype pr W) p0 (c/random-phenotype pr W)
        step (fn [p m n g t ph]
               (let [g' (mech-step p m g cfg t ph)]
                 (if (pos? (or (:noise cfg) 0)) (c/genotype-noise n g' (:noise cfg)) g')))]
    (loop [t 0 g g0 p p0]
      (if (= t TSTAR)
        (for [x (range 0 W 8)]
          (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))
                pA' (clone pr) pB' (clone pr) mA (clone mr) mB (clone mr)
                nA (clone nr) nB (clone nr)]
            (loop [tt TSTAR gA g a p gB g b pB]
              (if (= tt (+ TSTAR DT)) (count (remove true? (map = a b)))
                (recur (inc tt) (step pA' mA nA gA tt a) (c/phenotype-step gA a)
                                (step pB' mB nB gB tt b) (c/phenotype-step gB b))))))
        (recur (inc t) (step pr mr nr g t p) (c/phenotype-step g p))))))
(let [PA [3 0 1 2 7 4 5 6] T4 [6 7 0 2 1 4 3 5]
      R1 [1 2 3 4 5 6 7 0] R2 [2 3 4 5 6 7 0 1] R4 [4 5 6 7 0 1 2 3]]
  (println "class\tname\tseed\tdamage")
  (doseq [rule [0 204 90 54 110 30] seed (range 4)]
    (let [r (rng/make-rng (format "prop-%d" seed))
          _ (c/random-genotype r W) p0 (c/random-phenotype r W)]
      (doseq [d (loop [t 0 p p0]
                  (if (= t TSTAR)
                    (for [x (range 0 W 8)]
                      (let [pB (apply str (assoc (vec p) x (if (= \1 (nth p x)) \0 \1)))]
                        (loop [tt TSTAR a p b pB]
                          (if (= tt (+ TSTAR DT)) (count (remove true? (map = a b)))
                            (recur (inc tt) (eca-row rule a) (eca-row rule b))))))
                    (recur (inc t) (eca-row rule p))))]
        (println (format "eca\trule %d\t%d\t%d" rule seed d)))))
  (doseq [[cls nm cfg]
          [["family" "$P_a$ (bare)"          {:kind :baseline :wa PA}]
           ["family" "rot$+1$"               {:kind :baseline :wa R1}]
           ["family" "braid $P_a$/two-4"     {:kind :baseline :wa PA :wb T4}]
           ["family" "braid rot$+2$/rot$+4$" {:kind :baseline :wa R2 :wb R4}]
           ["dial" "blend $0.00$"            {:kind :blend :wa PA :b 0.0}]
           ["dial" "blend $0.35$"            {:kind :blend :wa PA :b 0.35}]
           ["dial" "blend $0.70$"            {:kind :blend :wa PA :b 0.70}]
           ["dial" "blend $1.00$"            {:kind :blend :wa PA :b 1.0}]
           ["dial" "braid + blend $0.70$"    {:kind :blend :wa PA :wb T4 :b 0.70}]
           ["dial" "async update $0.25$"     {:kind :async :wa PA :u 0.25}]
           ["dial" "async update $0.75$"     {:kind :async :wa PA :u 0.75}]
           ["dial" "niches $P_a$/two-4 (16)" {:kind :baseline :wa PA :wb T4 :patch 16}]
           ["dial" "niches $P_a$/two-4 (8)"  {:kind :baseline :wa PA :wb T4 :patch 8}]
           ["dial" "mutation $0.10$"         {:kind :baseline :wa PA :noise 0.10}]
           ["dial" "mutation $0.40$"         {:kind :baseline :wa PA :noise 0.40}]
           ["dial" "transport $0.50$"        {:kind :transport :u 0.5}]
           ["dial" "transport $1.00$"        {:kind :transport :u 1.0}]
           ["dial" "preserving limit"        {:kind :preserve}]]
          seed (range 4)]
    (doseq [d (run-mech cfg seed)]
      (println (format "%s\t%s\t%d\t%d" cls nm seed d)))))
